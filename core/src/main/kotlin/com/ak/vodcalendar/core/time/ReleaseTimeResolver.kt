package com.ak.vodcalendar.core.time

import com.ak.vodcalendar.core.model.Region
import com.ak.vodcalendar.core.model.StreamingCatalog
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object RomaniaTime {
    val ZONE: ZoneId = ZoneId.of("Europe/Bucharest")

    fun today(clock: Clock = Clock.systemUTC()): LocalDate = LocalDate.now(clock.withZone(ZONE))

    fun now(clock: Clock = Clock.systemUTC()): ZonedDateTime = ZonedDateTime.now(clock.withZone(ZONE))
}

data class ResolvedRelease(
    /** Release moment, always in Europe/Bucharest. */
    val at: ZonedDateTime,
    /** TMDB only gives a date, so the hour is an estimate. */
    val approximate: Boolean,
)

/**
 * Turns a TMDB episode air date (a local date in the show's home market) into a
 * moment in Romania time.
 *
 * Rules (estimates until a source with exact hours is added):
 * - Streaming originals (Netflix, Max, Disney+, …) drop at 00:00 Pacific time,
 *   which is 09:00–10:00 in Romania on the same date.
 * - Broadcast and cable shows air at 21:00 in their home time zone, so a US
 *   evening episode lands at 03:00–04:00 the next day in Romania.
 * - Apps set to RO are assumed to get the episode at the same moment as the US
 *   (true for Max, Netflix, Disney+, SkyShowtime simultaneous releases).
 *   Per-region delays are refined in stage 2.
 */
class ReleaseTimeResolver {

    fun resolve(
        airDate: LocalDate,
        originCountries: List<String>,
        networkIds: Set<Int>,
        @Suppress("UNUSED_PARAMETER") watchRegion: Region?,
    ): ResolvedRelease {
        val local: ZonedDateTime = when {
            networkIds.any { it in streamingNetworkIds } ->
                ZonedDateTime.of(airDate, LocalTime.MIDNIGHT, PACIFIC)
            else -> {
                val zone = originCountries.firstNotNullOfOrNull { originZones[it.uppercase()] }
                    ?: return ResolvedRelease(ZonedDateTime.of(airDate, LocalTime.MIDNIGHT, RomaniaTime.ZONE), true)
                ZonedDateTime.of(airDate, PRIMETIME, zone)
            }
        }
        return ResolvedRelease(local.withZoneSameInstant(RomaniaTime.ZONE), approximate = true)
    }

    companion object {
        private val PACIFIC: ZoneId = ZoneId.of("America/Los_Angeles")
        private val PRIMETIME: LocalTime = LocalTime.of(21, 0)

        private val streamingNetworkIds: Set<Int> =
            StreamingCatalog.all.flatMap { it.tmdbNetworkIds }.toSet() - setOf(49) // HBO is a cable network

        private val originZones: Map<String, ZoneId> = mapOf(
            "US" to "America/New_York",
            "CA" to "America/Toronto",
            "GB" to "Europe/London",
            "IE" to "Europe/Dublin",
            "RO" to "Europe/Bucharest",
            "DE" to "Europe/Berlin",
            "FR" to "Europe/Paris",
            "ES" to "Europe/Madrid",
            "IT" to "Europe/Rome",
            "NL" to "Europe/Amsterdam",
            "BE" to "Europe/Brussels",
            "SE" to "Europe/Stockholm",
            "NO" to "Europe/Oslo",
            "DK" to "Europe/Copenhagen",
            "PL" to "Europe/Warsaw",
            "TR" to "Europe/Istanbul",
            "KR" to "Asia/Seoul",
            "JP" to "Asia/Tokyo",
            "CN" to "Asia/Shanghai",
            "TW" to "Asia/Taipei",
            "IN" to "Asia/Kolkata",
            "AU" to "Australia/Sydney",
            "NZ" to "Pacific/Auckland",
            "BR" to "America/Sao_Paulo",
            "MX" to "America/Mexico_City",
            "AR" to "America/Argentina/Buenos_Aires",
            "CO" to "America/Bogota",
        ).mapValues { ZoneId.of(it.value) }
    }
}
