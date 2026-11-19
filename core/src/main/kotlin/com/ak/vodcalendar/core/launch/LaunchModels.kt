package com.ak.vodcalendar.core.launch

/** What the user wants to open: a movie, or an episode of a show. */
data class ContentRef(
    val type: ContentType,
    val tmdbId: Int,
    val title: String,
    val imdbId: String? = null,
    val year: Int? = null,
    /** Episode only. */
    val season: Int? = null,
    val episode: Int? = null,
    /**
     * The title's id on each streaming service, keyed by [com.ak.vodcalendar.core.model.StreamingApp.id]
     * (from Wikidata, see [StreamingIdsSource]). Empty until it's loaded.
     */
    val serviceIds: Map<String, ServiceTitleId> = emptyMap(),
) {
    val isEpisode: Boolean get() = type == ContentType.SERIES && season != null && episode != null

    /** Text used for a search: the show or movie title (searches find the title, not the episode). */
    val searchQuery: String get() = title

    val episodeCode: String?
        get() = if (isEpisode) "S%02dE%02d".format(season, episode) else null
}

enum class ContentType { MOVIE, SERIES }

/** The id a streaming service uses for a title, and whether it's a movie or a series page there. */
data class ServiceTitleId(val id: String, val type: ContentType)

/** How close to the content a launch step lands. */
enum class LaunchPrecision(val label: String) {
    EPISODE("la episod"),
    TITLE("pe pagina titlului"),
    SEARCH("la căutare"),
    APP("doar aplicația"),
}

/** How sure we are that a step works, documented in the README. */
enum class LinkConfidence {
    /** Checked against the app's own docs or source code. */
    VERIFIED,
    /** Known public link format, but not tested on a TV yet. */
    BEST_EFFORT,
}

/** One way to try opening the content. The app tries the steps of a [LaunchTarget] in order. */
sealed interface LaunchStep {
    val precision: LaunchPrecision
    val confidence: LinkConfidence

    /**
     * ACTION_VIEW on [uri], limited to [packages] (first installed one wins). With an empty
     * [packages], any app that handles the link may open it.
     */
    data class OpenUri(
        val uri: String,
        val packages: List<String>,
        override val precision: LaunchPrecision,
        override val confidence: LinkConfidence,
        val extras: Map<String, String> = emptyMap(),
        /** Also try the app found by name, when none of [packages] is installed. */
        val launcherNames: List<String> = emptyList(),
    ) : LaunchStep

    /** Google TV / Android TV system search for [query]: its results link straight into the right app. */
    data class GlobalSearch(
        val query: String,
        override val confidence: LinkConfidence = LinkConfidence.BEST_EFFORT,
    ) : LaunchStep {
        override val precision: LaunchPrecision get() = LaunchPrecision.SEARCH
    }

    /** Just start the app (leanback launcher entry), found by package or by name. */
    data class LaunchApp(
        val packages: List<String>,
        val launcherNames: List<String> = emptyList(),
    ) : LaunchStep {
        override val precision: LaunchPrecision get() = LaunchPrecision.APP
        override val confidence: LinkConfidence get() = LinkConfidence.VERIFIED
    }
}

enum class TargetKind { STREAMING_APP, ALTERNATIVE_PLAYER, SYSTEM_SEARCH }

/** A button in the details screen or a row in the "Deschide cu…" chooser. */
data class LaunchTarget(
    /** StreamingApp id, AlternativePlayer id or [SYSTEM_SEARCH_ID]. */
    val id: String,
    val label: String,
    val kind: TargetKind,
    val steps: List<LaunchStep>,
    /** Short Romanian note for the chooser, e.g. "la episod", "pagina serialului". */
    val hint: String = "",
) {
    /** Best precision this target can reach if its first step works. */
    val bestPrecision: LaunchPrecision get() = steps.minOfOrNull { it.precision } ?: LaunchPrecision.APP

    companion object {
        const val SYSTEM_SEARCH_ID = "system-search"
    }
}
