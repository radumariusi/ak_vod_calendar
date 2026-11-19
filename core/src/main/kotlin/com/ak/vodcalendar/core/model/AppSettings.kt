package com.ak.vodcalendar.core.model

import kotlinx.serialization.Serializable

@Serializable
data class UserStreamingApp(
    val appId: String,
    val installed: Boolean = false,
    val region: Region = Region.RO,
)

/** A show or movie the user follows or hid, remembered with its name for the Settings screen. */
@Serializable
data class SavedShow(val id: Int, val name: String)

/**
 * Quality gates for series.
 *
 * Returning shows must reach the IMDb thresholds (when an OMDb key is set and
 * OMDb knows the show) or else the TMDB thresholds. New shows (season 1) pass
 * when they are on an "anticipated" list or already reach the thresholds.
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

/** Defaults for movies: a bit lower than for series, but with more IMDb votes. */
val DefaultMovieThresholds = QualityThresholds(
    minImdbRating = 6.5,
    minImdbVotes = 10_000,
    minTmdbRating = 6.5,
    minTmdbVotes = 200,
    newShowsOnlyIfAnticipated = true,
)

@Serializable
data class AppSettings(
    /** Optional key typed in Settings; overrides the one baked in at build time. */
    val tmdbApiKey: String = "",
    /** Optional OMDb key for IMDb ratings; overrides the one baked in at build time. */
    val omdbApiKey: String = "",
    val apps: List<UserStreamingApp> = StreamingCatalog.all.map { UserStreamingApp(it.id) },
    val alternativePlayerId: String? = null,
    val thresholds: QualityThresholds = QualityThresholds(),
    val hiddenCategories: Set<ContentFilter> = ContentFilter.defaultHidden,
    /** Always shown, whatever the thresholds say. */
    val followedShows: List<SavedShow> = emptyList(),
    /** Never shown. */
    val hiddenShows: List<SavedShow> = emptyList(),
    /** Calendar shows only episodes the user can watch on an installed app. */
    val onlyInstalledApps: Boolean = false,
    /** Quality gates for movies; `newShowsOnlyIfAnticipated` means "new movies only if anticipated". */
    val movieThresholds: QualityThresholds = DefaultMovieThresholds,
    /** Which kinds of movie releases the movie calendar shows. */
    val movieKinds: Set<MovieReleaseKind> = MovieReleaseKind.entries.toSet(),
    /** Movies always shown, whatever the thresholds say. */
    val followedMovies: List<SavedShow> = emptyList(),
    /** Movies never shown. */
    val hiddenMovies: List<SavedShow> = emptyList(),
) {
    /** Adds apps that were added to the catalog after the settings were saved and drops unknown ones. */
    fun normalized(): AppSettings {
        val known = apps.filter { StreamingCatalog.byId(it.appId) != null }.distinctBy { it.appId }
        val missing = StreamingCatalog.all
            .filter { app -> known.none { it.appId == app.id } }
            .map { UserStreamingApp(it.id) }
        return copy(
            apps = known + missing,
            followedShows = followedShows.distinctBy { it.id },
            hiddenShows = hiddenShows.distinctBy { it.id },
            followedMovies = followedMovies.distinctBy { it.id },
            hiddenMovies = hiddenMovies.distinctBy { it.id },
        )
    }

    fun installedApps(): List<Pair<StreamingApp, Region>> = apps
        .filter { it.installed }
        .mapNotNull { user -> StreamingCatalog.byId(user.appId)?.let { it to user.region } }

    fun updateApp(appId: String, transform: (UserStreamingApp) -> UserStreamingApp): AppSettings =
        copy(apps = apps.map { if (it.appId == appId) transform(it) else it })

    fun isFollowed(showId: Int): Boolean = followedShows.any { it.id == showId }
    fun isHidden(showId: Int): Boolean = hiddenShows.any { it.id == showId }

    /** Following a show un-hides it, and the other way round. */
    fun follow(show: SavedShow): AppSettings =
        copy(followedShows = followedShows.filterNot { it.id == show.id } + show, hiddenShows = hiddenShows.filterNot { it.id == show.id })

    fun unfollow(showId: Int): AppSettings = copy(followedShows = followedShows.filterNot { it.id == showId })

    fun hide(show: SavedShow): AppSettings =
        copy(hiddenShows = hiddenShows.filterNot { it.id == show.id } + show, followedShows = followedShows.filterNot { it.id == show.id })

    fun unhide(showId: Int): AppSettings = copy(hiddenShows = hiddenShows.filterNot { it.id == showId })

    fun isMovieFollowed(movieId: Int): Boolean = followedMovies.any { it.id == movieId }
    fun isMovieHidden(movieId: Int): Boolean = hiddenMovies.any { it.id == movieId }

    fun followMovie(movie: SavedShow): AppSettings = copy(
        followedMovies = followedMovies.filterNot { it.id == movie.id } + movie,
        hiddenMovies = hiddenMovies.filterNot { it.id == movie.id },
    )

    fun unfollowMovie(movieId: Int): AppSettings = copy(followedMovies = followedMovies.filterNot { it.id == movieId })

    fun hideMovie(movie: SavedShow): AppSettings = copy(
        hiddenMovies = hiddenMovies.filterNot { it.id == movie.id } + movie,
        followedMovies = followedMovies.filterNot { it.id == movie.id },
    )

    fun unhideMovie(movieId: Int): AppSettings = copy(hiddenMovies = hiddenMovies.filterNot { it.id == movieId })
}
