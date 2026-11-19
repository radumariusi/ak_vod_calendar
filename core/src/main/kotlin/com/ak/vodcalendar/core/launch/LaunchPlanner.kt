package com.ak.vodcalendar.core.launch

import com.ak.vodcalendar.core.model.AlternativePlayer
import com.ak.vodcalendar.core.model.AlternativePlayers
import com.ak.vodcalendar.core.model.AppSettings
import com.ak.vodcalendar.core.model.PlayerLinkScheme
import com.ak.vodcalendar.core.model.ProviderAvailability
import com.ak.vodcalendar.core.model.StreamingApp
import com.ak.vodcalendar.core.model.StreamingCatalog
import java.net.URLEncoder

/**
 * Title page links of the official apps, built from the ids Wikidata keeps for each
 * service. None of them carries the episode: the services' episode ids aren't public.
 */
object ServiceLinks {
    fun titleUrl(appId: String, id: ServiceTitleId): String? {
        val v = id.id.trim()
        if (v.isBlank()) return null
        val movie = id.type == ContentType.MOVIE
        return when (appId) {
            "netflix" -> "https://www.netflix.com/title/$v"
            "disney" -> if (movie) "https://www.disneyplus.com/movies/x/$v" else "https://www.disneyplus.com/series/x/$v"
            "prime" -> "https://www.primevideo.com/detail/$v"
            "appletv" -> if (movie) "https://tv.apple.com/movie/$v" else "https://tv.apple.com/show/$v"
            "hulu" -> if (movie) "https://www.hulu.com/movie/$v" else "https://www.hulu.com/series/$v"
            else -> null
        }
    }

    /** Extras some apps expect with a link (Netflix's TV app wants a "source"). */
    fun extras(appId: String): Map<String, String> = when (appId) {
        "netflix" -> mapOf("source" to "30")
        else -> emptyMap()
    }
}

/** Builds the launch options for a title: the official apps first, then the alternative players. */
object LaunchPlanner {

    /**
     * Installed apps that have the title in the region the user set for them, the app the
     * calendar picked ([preferred]) first.
     */
    fun streamingTargets(
        ref: ContentRef,
        settings: AppSettings,
        availability: List<ProviderAvailability>,
        preferred: StreamingApp? = null,
    ): List<LaunchTarget> {
        val installed = settings.installedApps()
        val apps = installed.filter { (app, region) ->
            app.id == preferred?.id ||
                availability.any { it.region == region && it.providerId in app.tmdbProviderIds }
        }.map { it.first }
        return apps.sortedByDescending { it.id == preferred?.id }.map { streamingTarget(it, ref) }
    }

    fun streamingTarget(app: StreamingApp, ref: ContentRef): LaunchTarget {
        val steps = buildList {
            val id = ref.serviceIds[app.id]
            val url = id?.let { ServiceLinks.titleUrl(app.id, it) }
            if (url != null) {
                add(
                    LaunchStep.OpenUri(
                        uri = url,
                        packages = app.androidTvPackages,
                        precision = LaunchPrecision.TITLE,
                        confidence = LinkConfidence.BEST_EFFORT,
                        extras = ServiceLinks.extras(app.id),
                        launcherNames = app.launcherNamesOrName(),
                    ),
                )
            }
            add(LaunchStep.LaunchApp(app.androidTvPackages, app.launcherNamesOrName()))
        }
        val hint = if (steps.first().precision == LaunchPrecision.TITLE) {
            if (ref.type == ContentType.MOVIE) "pagina filmului" else "pagina serialului"
        } else {
            "doar aplicația"
        }
        return LaunchTarget(app.id, app.name, TargetKind.STREAMING_APP, steps, hint)
    }

    fun playerTarget(player: AlternativePlayer, ref: ContentRef): LaunchTarget {
        val confidence = if (player.linkVerified) LinkConfidence.VERIFIED else LinkConfidence.BEST_EFFORT
        val links: List<Pair<String, LaunchPrecision>> = when (player.scheme) {
            PlayerLinkScheme.STREMIO -> stremioLinks(ref)
            PlayerLinkScheme.NUVIO -> nuvioLinks(ref)
            PlayerLinkScheme.NONE -> emptyList()
        }
        val steps = buildList<LaunchStep> {
            links.forEach { (uri, precision) ->
                add(LaunchStep.OpenUri(uri, player.packages, precision, confidence, launcherNames = player.launcherNames))
            }
            if (player.packages.isNotEmpty() || player.launcherNames.isNotEmpty()) {
                add(LaunchStep.LaunchApp(player.packages, player.launcherNames))
            }
        }
        val hint = when (steps.firstOrNull()?.precision) {
            LaunchPrecision.EPISODE -> "la episod"
            LaunchPrecision.TITLE -> if (ref.type == ContentType.MOVIE) "pagina filmului" else "pagina serialului"
            LaunchPrecision.SEARCH -> "căutare"
            else -> "doar aplicația"
        }
        return LaunchTarget(player.id, player.name, TargetKind.ALTERNATIVE_PLAYER, steps, hint)
    }

    fun systemSearchTarget(ref: ContentRef): LaunchTarget = LaunchTarget(
        id = LaunchTarget.SYSTEM_SEARCH_ID,
        label = "Caută în Google TV",
        kind = TargetKind.SYSTEM_SEARCH,
        steps = listOf(LaunchStep.GlobalSearch(ref.searchQuery)),
        hint = "arată pe ce aplicații e",
    )

    /** Every option for the "Deschide cu…" chooser: alternative players, then the system search. */
    fun chooserTargets(ref: ContentRef): List<LaunchTarget> =
        AlternativePlayers.all.map { playerTarget(it, ref) } + systemSearchTarget(ref)

    /** The default player from Settings, or null for "ask every time". */
    fun defaultPlayerTarget(ref: ContentRef, settings: AppSettings): LaunchTarget? =
        AlternativePlayers.byId(settings.alternativePlayerId)?.let { playerTarget(it, ref) }

    /**
     * Stremio deep links (documented in the Stremio addon SDK, docs/deep-links.md). The
     * episode id is `{imdbId}:{season}:{episode}` in Cinemeta. Without an IMDb id, a search.
     */
    fun stremioLinks(ref: ContentRef): List<Pair<String, LaunchPrecision>> {
        val imdb = ref.imdbId?.takeIf { it.startsWith("tt") }
        return buildList {
            if (imdb != null) {
                if (ref.type == ContentType.MOVIE) {
                    add("stremio:///detail/movie/$imdb/$imdb" to LaunchPrecision.TITLE)
                } else {
                    if (ref.isEpisode) add("stremio:///detail/series/$imdb/$imdb:${ref.season}:${ref.episode}" to LaunchPrecision.EPISODE)
                    add("stremio:///detail/series/$imdb" to LaunchPrecision.TITLE)
                }
            }
            add("stremio:///search?search=${encode(ref.searchQuery)}" to LaunchPrecision.SEARCH)
        }
    }

    /** Nuvio links, as parsed by Nuvio TV's DeepLinkParser: title pages only. */
    fun nuvioLinks(ref: ContentRef): List<Pair<String, LaunchPrecision>> {
        val imdb = ref.imdbId?.takeIf { it.startsWith("tt") }
        val type = if (ref.type == ContentType.MOVIE) "movie" else "series"
        val tmdbType = if (ref.type == ContentType.MOVIE) "movie" else "tv"
        return buildList {
            if (imdb != null) add("nuvio://$type/$imdb" to LaunchPrecision.TITLE)
            add("nuvio://tmdb/$tmdbType/${ref.tmdbId}" to LaunchPrecision.TITLE)
        }
    }

    // The (String, Charset) overload needs Android 13; this one works on every TV.
    private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun StreamingApp.launcherNamesOrName(): List<String> = launcherNames.ifEmpty { listOf(name) }

    /** Catalog lookup kept here so callers don't need StreamingCatalog. */
    fun app(id: String): StreamingApp? = StreamingCatalog.byId(id)
}
