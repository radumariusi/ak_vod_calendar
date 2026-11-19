package com.ak.vodcalendar.core.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** A published version of the app with its APK. */
data class AppRelease(
    /** e.g. "0.4.12", from the tag "v0.4.12". */
    val versionName: String,
    val title: String,
    val notes: String,
    val apkUrl: String,
    val apkSizeBytes: Long,
    val publishedAt: String?,
)

@Serializable
internal data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<GitHubAssetDto> = emptyList(),
)

@Serializable
internal data class GitHubAssetDto(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    val size: Long = 0,
)

class UpdateException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Reads the latest release of [repo] ("owner/name") from the GitHub API. The repo is
 * public, so no token is needed (60 requests an hour per IP is plenty).
 */
class GitHubUpdateChecker(
    private val http: OkHttpClient,
    private val repo: String,
    baseUrl: String = "https://api.github.com/",
) {
    private val base: HttpUrl = baseUrl.toHttpUrl()
    private val json = Json { ignoreUnknownKeys = true }

    /** The latest release with an APK, or null when there's none yet. */
    suspend fun latest(): AppRelease? {
        val url = base.newBuilder().addPathSegments("repos/$repo/releases/latest").build()
        val request = Request.Builder().url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "AkVodCalendar-updater")
            .get().build()
        val body = withContext(Dispatchers.IO) {
            try {
                http.newCall(request).execute().use { r ->
                    when {
                        r.code == 404 -> null
                        !r.isSuccessful -> throw UpdateException("GitHub a răspuns cu ${r.code}")
                        else -> r.body?.string()
                    }
                }
            } catch (e: UpdateException) {
                throw e
            } catch (e: Exception) {
                throw UpdateException("Nu pot contacta GitHub", e)
            }
        } ?: return null
        val dto = try {
            json.decodeFromString(GitHubReleaseDto.serializer(), body)
        } catch (e: Exception) {
            throw UpdateException("Răspuns GitHub necunoscut", e)
        }
        return toRelease(dto)
    }

    internal fun toRelease(dto: GitHubReleaseDto): AppRelease? {
        if (dto.draft) return null
        val apk = dto.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) } ?: return null
        return AppRelease(
            versionName = dto.tagName.trim().removePrefix("v").removePrefix("V"),
            title = dto.name?.takeIf { it.isNotBlank() } ?: dto.tagName,
            notes = dto.body.orEmpty().trim(),
            apkUrl = apk.downloadUrl,
            apkSizeBytes = apk.size,
            publishedAt = dto.publishedAt,
        )
    }
}

object Versions {
    /**
     * True when [candidate] is a higher version than [current], comparing the numbers
     * between dots ("0.4.12" > "0.4.9" > "0.4.0"). Text after a dash is ignored.
     */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = parts(candidate)
        val b = parts(current)
        if (a.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parts(v: String): List<Int> =
        v.trim().removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            .takeIf { v.isNotBlank() } ?: emptyList()
}
