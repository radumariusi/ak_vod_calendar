package com.ak.vodcalendar.ui.common

import com.ak.vodcalendar.core.model.MovieEntry
import com.ak.vodcalendar.core.model.MovieRelease
import com.ak.vodcalendar.core.model.MovieReleaseKind
import com.ak.vodcalendar.core.time.RomaniaTime
import java.time.format.DateTimeFormatter

private val movieTimeFormat = DateTimeFormatter.ofPattern("HH:mm", romanian)

/** "≈ 10:00" for streaming and digital, "în cinema" for cinema (no hour). */
fun movieTimeLabel(entry: MovieEntry): String = when {
    !entry.hasTime -> if (entry.limited) "în cinema (limitat)" else "în cinema"
    else -> (if (entry.timeIsApproximate) "≈ " else "") + entry.releaseAt.format(movieTimeFormat)
}

/** Short kind tag with its region, e.g. "Streaming RO", "Digital US", "Cinema RO". */
fun movieKindLabel(entry: MovieEntry): String = when (entry.kind) {
    MovieReleaseKind.STREAMING -> "Streaming ${entry.region.code}"
    MovieReleaseKind.DIGITAL -> "Digital ${entry.region.code}"
    MovieReleaseKind.CINEMA_US -> "Cinema US"
    MovieReleaseKind.CINEMA_RO -> "Cinema RO"
}

fun movieWhere(entry: MovieEntry): String = when (entry.kind) {
    MovieReleaseKind.STREAMING -> {
        val app = entry.watchOn
        val service = entry.service
        when {
            app != null -> "${app.name} (${entry.watchRegion?.code})"
            service != null -> "${service.name} ${entry.region.code} · nu ai aplicația"
            else -> "Streaming ${entry.region.code}"
        }
    }
    MovieReleaseKind.DIGITAL -> "Închiriere / cumpărare ${entry.region.code} (Apple TV, Google TV, Prime Video…)"
    MovieReleaseKind.CINEMA_US -> "Cinematografe din SUA"
    MovieReleaseKind.CINEMA_RO -> "Cinematografe din România"
}

fun movieRatingLabel(entry: MovieEntry): String = buildList {
    entry.imdb?.let { add("IMDb %.1f (%s)".format(romanian, it.rating, compact(it.votes))) }
    if (entry.tmdbVotes > 0) add("TMDB %.1f (%s)".format(romanian, entry.tmdbRating, compact(entry.tmdbVotes)))
}.joinToString(" · ")

fun movieBadges(entry: MovieEntry): List<String> = buildList {
    if (entry.isAnticipated) add("Mult așteptat")
    if (entry.isFollowed) add("Urmărești")
    if (entry.limited) add("Lansare limitată")
}

fun movieReleaseLine(release: MovieRelease): String {
    val what = when (release.kind) {
        MovieReleaseKind.STREAMING -> {
            val service = release.service?.name ?: "streaming"
            "$service ${release.region.code}" + if (release.onMyApp) " (aplicația ta)" else ""
        }
        MovieReleaseKind.DIGITAL -> "Digital ${release.region.code} (închiriere / cumpărare)"
        MovieReleaseKind.CINEMA_US -> "Cinema SUA" + if (release.limited) " (limitat)" else ""
        MovieReleaseKind.CINEMA_RO -> "Cinema România" + if (release.limited) " (limitat)" else ""
    }
    val year = if (release.date.year != RomaniaTime.today().year) " ${release.date.year}" else ""
    return "$what: " + dayLabel(release.date) + year
}
