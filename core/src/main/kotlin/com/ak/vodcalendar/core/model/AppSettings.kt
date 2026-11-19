package com.ak.vodcalendar.core.model

import kotlinx.serialization.Serializable

@Serializable
data class UserStreamingApp(
    val appId: String,
    val installed: Boolean = false,
    val region: Region = Region.RO,
)

/**
 * Quality gates for series. Stage 1 only stores them; stage 2 applies them
 * (IMDb values need an OMDb key).
 */
@Serializable
data class QualityThresholds(
    val minImdbRating: Double = 7.0,
    val minImdbVotes: Int = 5_000,
    val minTmdbRating: Double = 7.0,
    val minTmdbVotes: Int = 300,
    /** New shows (season 1) only show up if they are on an "anticipated" list. */
    val newShowsOnlyIfAnticipated: Boolean = true,
)

@Serializable
data class AppSettings(
    /** Optional key typed in Settings; overrides the one baked in at build time. */
    val tmdbApiKey: String = "",
    val apps: List<UserStreamingApp> = StreamingCatalog.all.map { UserStreamingApp(it.id) },
    val alternativePlayerId: String? = null,
    val thresholds: QualityThresholds = QualityThresholds(),
    val hiddenCategories: Set<ContentFilter> = ContentFilter.defaultHidden,
) {
    /** Adds apps that were added to the catalog after the settings were saved and drops unknown ones. */
    fun normalized(): AppSettings {
        val known = apps.filter { StreamingCatalog.byId(it.appId) != null }.distinctBy { it.appId }
        val missing = StreamingCatalog.all
            .filter { app -> known.none { it.appId == app.id } }
            .map { UserStreamingApp(it.id) }
        return copy(apps = known + missing)
    }

    fun installedApps(): List<Pair<StreamingApp, Region>> = apps
        .filter { it.installed }
        .mapNotNull { user -> StreamingCatalog.byId(user.appId)?.let { it to user.region } }

    fun updateApp(appId: String, transform: (UserStreamingApp) -> UserStreamingApp): AppSettings =
        copy(apps = apps.map { if (it.appId == appId) transform(it) else it })
}
