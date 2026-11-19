package com.ak.vodcalendar.core.model

/**
 * Third-party player used for "Deschide cu…" when the user does not have the
 * official app. Launch details (intents, URL schemes) are wired in stage 4.
 */
data class AlternativePlayer(
    val id: String,
    val name: String,
    val androidPackage: String? = null,
)

object AlternativePlayers {
    val all: List<AlternativePlayer> = listOf(
        AlternativePlayer("debridstream", "DebridStream"),
        AlternativePlayer("wuplay", "Wuplay"),
        AlternativePlayer("nuvio", "Nuvio"),
        AlternativePlayer("stremio", "Stremio", "com.stremio.one"),
    )

    fun byId(id: String?): AlternativePlayer? = all.firstOrNull { it.id == id }
}
