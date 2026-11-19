package com.ak.vodcalendar.core.model

/**
 * A streaming service the user can have installed on the TV.
 *
 * @property tmdbProviderIds TMDB / JustWatch watch-provider ids that map to this app
 *   (some services have several ids, e.g. Amazon has one per region and a channel id).
 * @property tmdbNetworkIds TMDB network ids used when the service is the show's original network.
 * @property androidTvPackage package name on Android TV, used for "open in app" in stage 4
 *   (to be verified on a real device then).
 */
data class StreamingApp(
    val id: String,
    val name: String,
    val tmdbProviderIds: Set<Int>,
    val tmdbNetworkIds: Set<Int> = emptySet(),
    val androidTvPackage: String? = null,
)

/**
 * Built-in list of streaming apps shown in Settings. Provider ids come from
 * TMDB `/watch/providers/tv`; they can be refreshed later from the API.
 */
object StreamingCatalog {
    val all: List<StreamingApp> = listOf(
        StreamingApp("netflix", "Netflix", setOf(8, 1796), setOf(213), "com.netflix.ninja"),
        StreamingApp("max", "Max / HBO Max", setOf(1899, 384), setOf(49, 3186), "com.wbd.stream"),
        StreamingApp("disney", "Disney+", setOf(337), setOf(2739), "com.disney.disneyplus"),
        StreamingApp("prime", "Prime Video", setOf(9, 119), setOf(1024), "com.amazon.amazonvideo.livingroom"),
        StreamingApp("appletv", "Apple TV+", setOf(350), setOf(2552), "com.apple.atve.androidtv.appletv"),
        StreamingApp("skyshowtime", "SkyShowtime", setOf(1773), emptySet(), null),
        StreamingApp("paramount", "Paramount+", setOf(531, 582), setOf(4330), "com.cbs.ott"),
        StreamingApp("hulu", "Hulu", setOf(15), setOf(453), null),
        StreamingApp("peacock", "Peacock", setOf(386, 387), setOf(3353), null),
        StreamingApp("crunchyroll", "Crunchyroll", setOf(283), setOf(1112), "com.crunchyroll.crunchyroid"),
        StreamingApp("voyo", "Voyo", setOf(1859), emptySet(), null),
    )

    fun byId(id: String): StreamingApp? = all.firstOrNull { it.id == id }
}
