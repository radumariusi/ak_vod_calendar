package com.ak.vodcalendar.launch

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.ak.vodcalendar.core.launch.LaunchPrecision
import com.ak.vodcalendar.core.launch.LaunchStep
import com.ak.vodcalendar.core.launch.LaunchTarget

/** What happened when the user pressed a launch button. */
sealed interface LaunchResult {
    /** [precision] says how close to the content the app landed. */
    data class Opened(val target: LaunchTarget, val precision: LaunchPrecision) : LaunchResult

    /** Nothing on the TV could handle any of the target's steps (usually: app not installed). */
    data class NotAvailable(val target: LaunchTarget) : LaunchResult
}

/**
 * Turns [LaunchTarget]s into Android intents and starts the first step that works.
 *
 * Packages are found by their known names first, then by the app's label among the
 * installed launcher apps (so a regional build of SkyShowtime or an unlisted package of
 * DebridStream is still found). Package visibility comes from `<queries>` in the manifest.
 */
class ContentLauncher(context: Context) {
    private val context = context.applicationContext
    private val pm: PackageManager = this.context.packageManager

    fun launch(target: LaunchTarget): LaunchResult {
        for (step in target.steps) {
            val intent = intentFor(step) ?: continue
            if (start(intent)) return LaunchResult.Opened(target, step.precision)
        }
        return LaunchResult.NotAvailable(target)
    }

    /** True when at least one step of [target] can be started on this TV. */
    fun canLaunch(target: LaunchTarget): Boolean = target.steps.any { step ->
        intentFor(step)?.let { it.resolveActivity(pm) != null } == true
    }

    /** Installed package for a target found by known package names or by label, if any. */
    fun installedPackage(packages: List<String>, launcherNames: List<String>): String? =
        packages.firstOrNull(::isInstalled) ?: findByLabel(launcherNames)

    private fun intentFor(step: LaunchStep): Intent? = when (step) {
        is LaunchStep.OpenUri -> {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(step.uri))
            step.extras.forEach { (k, v) -> intent.putExtra(k, v) }
            if (step.packages.isEmpty() && step.launcherNames.isEmpty()) {
                intent // any app that handles the link; Android asks which one when there are several
            } else {
                installedPackage(step.packages, step.launcherNames)?.let { intent.setPackage(it) }
            }
        }
        is LaunchStep.GlobalSearch ->
            Intent(SEARCH_ACTION).putExtra(SearchManager.QUERY, step.query)
                .takeIf { it.resolveActivity(pm) != null }
                ?: Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, step.query)
        is LaunchStep.LaunchApp -> installedPackage(step.packages, step.launcherNames)?.let { pkg ->
            pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
        }
    }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun start(intent: Intent): Boolean {
        if (intent.resolveActivity(pm) == null) return false
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    private fun isInstalled(pkg: String): Boolean = try {
        pm.getPackageInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Label match: equal to a name, or starting with it followed by a space ("Max" ≠ "MaxPlayer"). */
    private fun findByLabel(names: List<String>): String? {
        if (names.isEmpty()) return null
        val wanted = names.map { it.lowercase().trim() }.filter { it.isNotBlank() }
        val own = context.packageName
        val candidates = (launcherApps(Intent.CATEGORY_LEANBACK_LAUNCHER) + launcherApps(Intent.CATEGORY_LAUNCHER))
            .filter { it.first != own }
            .distinctBy { it.first }
        // Exact names first, then "name …".
        return candidates.firstOrNull { (_, label) -> label in wanted }?.first
            ?: candidates.firstOrNull { (_, label) -> wanted.any { label.startsWith("$it ") } }?.first
    }

    private fun launcherApps(category: String): List<Pair<String, String>> {
        val query = Intent(Intent.ACTION_MAIN).addCategory(category)
        return pm.queryIntentActivities(query, 0).map { info ->
            info.activityInfo.packageName to info.loadLabel(pm).toString().lowercase().trim()
        }
    }

    private companion object {
        const val SEARCH_ACTION = "android.search.action.GLOBAL_SEARCH"
    }
}
