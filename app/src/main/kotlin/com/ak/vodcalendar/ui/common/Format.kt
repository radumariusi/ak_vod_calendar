package com.ak.vodcalendar.ui.common

import com.ak.vodcalendar.core.model.EpisodeEntry
import com.ak.vodcalendar.core.time.RomaniaTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val romanian: Locale = Locale.forLanguageTag("ro-RO")
private val dayFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM", romanian)
private val shortDayFormat = DateTimeFormatter.ofPattern("d MMM", romanian)
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", romanian)

fun dayLabel(date: LocalDate): String {
    val today = RomaniaTime.today()
    val prefix = when (date) {
        today -> "Azi · "
        today.plusDays(1) -> "Mâine · "
        today.minusDays(1) -> "Ieri · "
        else -> ""
    }
    return prefix + date.format(dayFormat).replaceFirstChar { it.titlecase(romanian) }
}

fun weekLabel(from: LocalDate, to: LocalDate): String =
    "${from.format(shortDayFormat)} – ${to.format(shortDayFormat)} ${to.year}"

fun timeLabel(entry: EpisodeEntry): String =
    (if (entry.timeIsApproximate) "≈ " else "") + entry.releaseAt.format(timeFormat)

fun whereToWatch(entry: EpisodeEntry): String {
    val app = entry.watchOn
    if (app != null) return "${app.name} (${entry.watchRegion?.code})"
    if (entry.availability.isEmpty()) return "Fără info de streaming"
    val elsewhere = entry.availability.take(3).joinToString(", ") { "${it.providerName} ${it.region.code}" }
    return "Nu ai aplicația · $elsewhere"
}

fun compact(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(romanian, n / 1_000_000.0)
    n >= 1_000 -> "%.1fk".format(romanian, n / 1_000.0)
    else -> n.toString()
}

fun ratingLabel(entry: EpisodeEntry): String = buildList {
    entry.imdb?.let { add("IMDb %.1f (%s)".format(romanian, it.rating, compact(it.votes))) }
    if (entry.tmdbVotes > 0) add("TMDB %.1f (%s)".format(romanian, entry.tmdbRating, compact(entry.tmdbVotes)))
}.joinToString(" · ")

/** Short tags: new show, premiere, finale, anticipated, followed. */
fun badges(entry: EpisodeEntry): List<String> = buildList {
    when {
        entry.isNewShow && entry.isSeasonPremiere -> add("Serial nou")
        entry.isSeasonPremiere -> add("Premieră de sezon")
    }
    if (entry.isFinale) add("Final de sezon")
    if (entry.isAnticipated && entry.isNewShow) add("Mult așteptat")
    if (entry.isFollowed) add("Urmărești")
}
