package com.ak.vodcalendar.core.model

/**
 * A streaming service the user can have installed on the TV.
 *
 * @property tmdbProviderIds TMDB / JustWatch watch-provider ids that map to this app
 *   (some services have several ids, e.g. Amazon has one per region and a channel id).
 * @property tmdbNetworkIds TMDB network ids used when the service is the show's original network.
 * @property androidTvPackage package name on Android TV, used for "open in app" in stage 4
 *   (to be verified on a real device then).
 * @property aliases names TMDB uses in the notes of movie release dates (e.g. "Netflix",
 *   "Disney Plus"), so a digital release can be recognized as a premiere on this service.
 * @property globalDrop true when the service releases its titles at the same moment in
 *   every country, at 00:00 Pacific time (Netflix, Disney+, Prime Video, …).
 */
data class StreamingApp(
    val id: String,
    val name: String,
    val tmdbProviderIds: Set<Int>,
    val tmdbNetworkIds: Set<Int> = emptySet(),
    val androidTvPackage: String? = null,
    val aliases: Set<String> = emptySet(),
    val globalDrop: Boolean = false,
) {
    /** True when a TMDB release note (e.g. "Netflix", "Streaming on Max") names this service. */
    fun matchesNote(note: String?): Boolean {
        if (note.isNullOrBlank()) return false
        val words = Regex("[^a-z0-9+]+").split(note.lowercase()).filter { it.isNotBlank() }.joinToString(" ")
        val padded = " $words "
        return (aliases + name).any { alias ->
            val a = Regex("[^a-z0-9+]+").split(alias.lowercase()).filter { it.isNotBlank() }.joinToString(" ")
            a.isNotBlank() && padded.contains(" $a ")
        }
    }
}

/**
 * Built-in list of streaming apps shown in Settings. Provider ids come from
 * TMDB `/watch/providers/tv`; they can be refreshed later from the API.
 */
object StreamingCatalog {
    val all: List<StreamingApp> = listOf(
        StreamingApp("netflix", "Netflix", setOf(8, 1796), setOf(213), "com.netflix.ninja", setOf("Netflix"), globalDrop = true),
        StreamingApp("max", "Max / HBO Max", setOf(1899, 384), setOf(49, 3186), "com.wbd.stream", setOf("Max", "HBO Max", "HBO"), globalDrop = true),
        StreamingApp("disney", "Disney+", setOf(337), setOf(2739), "com.disney.disneyplus", setOf("Disney+", "Disney Plus"), globalDrop = true),
        StreamingApp("prime", "Prime Video", setOf(9, 119), setOf(1024), "com.amazon.amazonvideo.livingroom", setOf("Prime Video", "Amazon Prime Video", "Amazon Prime", "Amazon"), globalDrop = true),
        StreamingApp("appletv", "Apple TV+", setOf(350), setOf(2552), "com.apple.atve.androidtv.appletv", setOf("Apple TV+", "Apple TV Plus"), globalDrop = true),
        StreamingApp("skyshowtime", "SkyShowtime", setOf(1773), emptySet(), null, setOf("SkyShowtime")),
        StreamingApp("paramount", "Paramount+", setOf(531, 582), setOf(4330), "com.cbs.ott", setOf("Paramount+", "Paramount Plus"), globalDrop = true),
        StreamingApp("hulu", "Hulu", setOf(15), setOf(453), null, setOf("Hulu"), globalDrop = true),
        StreamingApp("peacock", "Peacock", setOf(386, 387), setOf(3353), null, setOf("Peacock"), globalDrop = true),
        StreamingApp("crunchyroll", "Crunchyroll", setOf(283), setOf(1112), "com.crunchyroll.crunchyroid", setOf("Crunchyroll")),
        StreamingApp("voyo", "Voyo", setOf(1859), emptySet(), null, setOf("Voyo")),
    )

    fun byId(id: String): StreamingApp? = all.firstOrNull { it.id == id }
}
