package com.ak.vodcalendar.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.ak.vodcalendar.BuildConfig
import com.ak.vodcalendar.core.update.AppRelease
import com.ak.vodcalendar.core.update.GitHubUpdateChecker
import com.ak.vodcalendar.core.update.Versions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val latest: String?) : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    /** [progress] from 0 to 1, or null while the size is unknown. */
    data class Downloading(val release: AppRelease, val progress: Float?) : UpdateState
    /** Android needs "install unknown apps" allowed for this app; Settings was opened. */
    data class NeedsPermission(val release: AppRelease) : UpdateState
    data class Installing(val release: AppRelease) : UpdateState
    data class Failed(val message: String, val release: AppRelease? = null) : UpdateState
}

/**
 * In-app updates from the GitHub "Latest release": checks, downloads the APK into the
 * cache and hands it to the system installer. Every build is signed with the same key
 * (see app/build.gradle.kts), so the new version installs over the old one.
 */
class UpdateManager(context: Context, private val http: OkHttpClient) {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val checker = GitHubUpdateChecker(http, BuildConfig.UPDATE_REPO)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var job: Job? = null

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    /** Checks GitHub; [quiet] keeps errors out of the UI (used for the check at start). */
    fun check(quiet: Boolean = false) {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = UpdateState.Checking
            _state.value = try {
                val latest = checker.latest()
                if (latest != null && Versions.isNewer(latest.versionName, currentVersion)) {
                    UpdateState.Available(latest)
                } else {
                    UpdateState.UpToDate(latest?.versionName)
                }
            } catch (e: Exception) {
                if (quiet) UpdateState.Idle else UpdateState.Failed(e.message ?: "Eroare la verificare")
            }
        }
    }

    fun downloadAndInstall(release: AppRelease) {
        if (job?.isActive == true) return
        job = scope.launch {
            if (!canInstall()) {
                openInstallPermissionSettings()
                _state.value = UpdateState.NeedsPermission(release)
                return@launch
            }
            _state.value = UpdateState.Downloading(release, null)
            val file = try {
                download(release)
            } catch (e: Exception) {
                _state.value = UpdateState.Failed("Descărcarea a eșuat: ${e.message ?: e.javaClass.simpleName}", release)
                return@launch
            }
            _state.value = UpdateState.Installing(release)
            if (!install(file)) {
                _state.value = UpdateState.Failed("Nu pot porni instalarea pe acest TV.", release)
            }
        }
    }

    private fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    private fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Some TVs hide this screen; the message in Settings explains what to allow.
        }
    }

    private suspend fun download(release: AppRelease): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "ak-vod-calendar-${release.versionName}.apk")
        val request = Request.Builder().url(release.apkUrl).header("User-Agent", "AkVodCalendar-updater").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
            val body = response.body ?: throw IllegalStateException("răspuns gol")
            val total = body.contentLength().takeIf { it > 0 } ?: release.apkSizeBytes.takeIf { it > 0 }
            var read = 0L
            var lastReported = -1
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        if (total != null) {
                            val percent = (read * 100 / total).toInt()
                            if (percent != lastReported) {
                                lastReported = percent
                                _state.value = UpdateState.Downloading(release, (read.toFloat() / total).coerceAtMost(1f))
                            }
                        }
                    }
                }
            }
        }
        target
    }

    private fun install(file: File): Boolean {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
