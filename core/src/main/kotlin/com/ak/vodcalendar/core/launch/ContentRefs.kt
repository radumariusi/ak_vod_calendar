package com.ak.vodcalendar.core.launch

import com.ak.vodcalendar.core.model.EpisodeEntry
import com.ak.vodcalendar.core.model.MovieEntry

fun EpisodeEntry.toContentRef(): ContentRef = ContentRef(
    type = ContentType.SERIES,
    tmdbId = showId,
    title = showName,
    imdbId = imdbId?.takeIf { it.isNotBlank() },
    season = seasonNumber,
    episode = episodeNumber,
)

fun MovieEntry.toContentRef(): ContentRef = ContentRef(
    type = ContentType.MOVIE,
    tmdbId = movieId,
    title = title,
    imdbId = imdbId?.takeIf { it.isNotBlank() },
    year = releases.minOfOrNull { it.date }?.year,
)
