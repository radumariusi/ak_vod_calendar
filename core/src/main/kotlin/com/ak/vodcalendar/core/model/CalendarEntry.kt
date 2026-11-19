package com.ak.vodcalendar.core.model

import java.time.ZonedDateTime

/** Where a title can be watched, according to TMDB / JustWatch. */
data class ProviderAvailability(
    val providerId: Int,
    val providerName: String,
    val region: Region,
    val logoUrl: String?,
)

/** One episode in the calendar, with its release time already in Romania time. */
data class EpisodeEntry(
    val showId: Int,
    val showName: String,
    val posterUrl: String?,
    val stillUrl: String?,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val episodeTitle: String?,
    val overview: String?,
    /** Release moment in Europe/Bucharest. */
    val releaseAt: ZonedDateTime,
    /** True when the hour is estimated (TMDB only gives a date). */
    val timeIsApproximate: Boolean,
    /** The installed app (and its region) where the user can watch it, if any. */
    val watchOn: StreamingApp?,
    val watchRegion: Region?,
    val availability: List<ProviderAvailability>,
    val tmdbRating: Double,
    val tmdbVotes: Int,
    val imdbId: String?,
    val categories: Set<ContentFilter>,
    val isSeasonPremiere: Boolean,
    val isNewShow: Boolean,
) {
    val code: String get() = "S%02dE%02d".format(seasonNumber, episodeNumber)
}
