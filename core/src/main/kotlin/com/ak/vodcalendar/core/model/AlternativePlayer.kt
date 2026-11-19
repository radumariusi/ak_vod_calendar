package com.ak.vodcalendar.core.model

/** Which link format a player understands. */
enum class PlayerLinkScheme {
    /** `stremio:///detail/{type}/{imdbId}/{videoId}`, documented in the Stremio addon SDK (deep-links.md). */
    STREMIO,
    /** `nuvio://series/{imdbId}`, `nuvio://tmdb/tv/{tmdbId}`, from Nuvio TV's DeepLinkParser. */
    NUVIO,
    /** No known link format: the app is only started. */
    NONE,
}

/**
 * Third-party player used for "Deschide cu…", mostly for titles on services the
 * user doesn't have.
 *
 * @property packages known package names; when none is installed, an installed app
 *   whose name matches [launcherNames] is used.
 * @property linkVerified true when [scheme] is confirmed for this exact app (docs or
 *   source code), false when it's a guess (e.g. a Stremio fork that may keep the scheme).
 */
data class AlternativePlayer(
    val id: String,
    val name: String,
    val packages: List<String> = emptyList(),
    val launcherNames: List<String> = listOf(name),
    val scheme: PlayerLinkScheme = PlayerLinkScheme.NONE,
    val linkVerified: Boolean = false,
    /** Shown under the name in Settings. */
    val note: String = "",
)

object AlternativePlayers {
    val all: List<AlternativePlayer> = listOf(
        AlternativePlayer(
            id = "debridstream",
            name = "DebridStream",
            launcherNames = listOf("DebridStream", "Debrid Stream"),
            note = "Nu are link public: se deschide aplicația și cauți titlul.",
        ),
        AlternativePlayer(
            id = "wuplay",
            name = "Wuplay",
            packages = listOf("app.wuplay.androidtv"),
            scheme = PlayerLinkScheme.STREMIO,
            linkVerified = false,
            note = "Fork de Stremio: se încearcă linkul Stremio direct la episod, altfel se deschide aplicația.",
        ),
        AlternativePlayer(
            id = "nuvio",
            name = "Nuvio",
            packages = listOf("com.nuvio.tv", "com.nuvio.app"),
            scheme = PlayerLinkScheme.NUVIO,
            linkVerified = true,
            note = "Deschide pagina serialului sau a filmului (Nuvio nu primește episodul în link).",
        ),
        AlternativePlayer(
            id = "stremio",
            name = "Stremio",
            packages = listOf("com.stremio.one"),
            scheme = PlayerLinkScheme.STREMIO,
            linkVerified = true,
            note = "Deschide direct episodul sau filmul.",
        ),
        AlternativePlayer(
            id = "stremio-any",
            name = "Orice aplicație compatibilă Stremio",
            launcherNames = emptyList(),
            scheme = PlayerLinkScheme.STREMIO,
            linkVerified = false,
            note = "Trimite linkul Stremio și Android te lasă să alegi aplicația (forkuri de Stremio).",
        ),
    )

    fun byId(id: String?): AlternativePlayer? = all.firstOrNull { it.id == id }
}
