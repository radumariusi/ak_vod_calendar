package com.ak.vodcalendar.core

import com.ak.vodcalendar.core.launch.ContentRef
import com.ak.vodcalendar.core.launch.ContentType
import com.ak.vodcalendar.core.launch.LaunchPlanner
import com.ak.vodcalendar.core.launch.LaunchPrecision
import com.ak.vodcalendar.core.launch.LaunchStep
import com.ak.vodcalendar.core.launch.LaunchTarget
import com.ak.vodcalendar.core.launch.LinkConfidence
import com.ak.vodcalendar.core.launch.ServiceTitleId
import com.ak.vodcalendar.core.model.AlternativePlayers
import com.ak.vodcalendar.core.model.AppSettings
import com.ak.vodcalendar.core.model.ProviderAvailability
import com.ak.vodcalendar.core.model.Region
import com.ak.vodcalendar.core.model.StreamingCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchPlannerTest {

    private val episode = ContentRef(
        type = ContentType.SERIES,
        tmdbId = 95396,
        title = "Severance",
        imdbId = "tt11280740",
        season = 2,
        episode = 3,
    )

    private val movie = ContentRef(type = ContentType.MOVIE, tmdbId = 693134, title = "Dune: Part Two", imdbId = "tt15239678")

    @Test
    fun `stremio opens the exact episode, then the show, then a search`() {
        val target = LaunchPlanner.playerTarget(AlternativePlayers.byId("stremio")!!, episode)
        val uris = target.steps.filterIsInstance<LaunchStep.OpenUri>().map { it.uri }
        assertEquals(
            listOf(
                "stremio:///detail/series/tt11280740/tt11280740:2:3",
                "stremio:///detail/series/tt11280740",
                "stremio:///search?search=Severance",
            ),
            uris,
        )
        assertEquals(LaunchPrecision.EPISODE, target.bestPrecision)
        assertTrue(target.steps.last() is LaunchStep.LaunchApp)
        val first = target.steps.first() as LaunchStep.OpenUri
        assertEquals(listOf("com.stremio.one"), first.packages)
        assertEquals(LinkConfidence.VERIFIED, first.confidence)
        assertEquals("la episod", target.hint)
    }

    @Test
    fun `stremio movie link and search encoding`() {
        val uris = LaunchPlanner.stremioLinks(movie).map { it.first }
        assertEquals("stremio:///detail/movie/tt15239678/tt15239678", uris[0])
        assertEquals("stremio:///search?search=Dune%3A%20Part%20Two", uris[1])
    }

    @Test
    fun `without an imdb id stremio falls back to search`() {
        val uris = LaunchPlanner.stremioLinks(episode.copy(imdbId = null)).map { it.first }
        assertEquals(listOf("stremio:///search?search=Severance"), uris)
    }

    @Test
    fun `nuvio opens the title by imdb then by tmdb id`() {
        assertEquals(
            listOf("nuvio://series/tt11280740", "nuvio://tmdb/tv/95396"),
            LaunchPlanner.nuvioLinks(episode).map { it.first },
        )
        assertEquals(
            listOf("nuvio://movie/tt15239678", "nuvio://tmdb/movie/693134"),
            LaunchPlanner.nuvioLinks(movie).map { it.first },
        )
    }

    @Test
    fun `wuplay tries stremio links as best effort`() {
        val target = LaunchPlanner.playerTarget(AlternativePlayers.byId("wuplay")!!, episode)
        val first = target.steps.first() as LaunchStep.OpenUri
        assertEquals(listOf("app.wuplay.androidtv"), first.packages)
        assertEquals(LinkConfidence.BEST_EFFORT, first.confidence)
    }

    @Test
    fun `debridstream only launches the app found by name`() {
        val target = LaunchPlanner.playerTarget(AlternativePlayers.byId("debridstream")!!, episode)
        assertEquals(1, target.steps.size)
        val step = target.steps.single() as LaunchStep.LaunchApp
        assertTrue("DebridStream" in step.launcherNames)
    }

    @Test
    fun `any stremio app has no package so android shows its chooser`() {
        val target = LaunchPlanner.playerTarget(AlternativePlayers.byId("stremio-any")!!, episode)
        assertTrue(target.steps.all { it is LaunchStep.OpenUri && it.packages.isEmpty() })
    }

    @Test
    fun `netflix opens the title page when wikidata knows its id`() {
        val ref = episode.copy(serviceIds = mapOf("netflix" to ServiceTitleId("81280926", ContentType.SERIES)))
        val target = LaunchPlanner.streamingTarget(StreamingCatalog.byId("netflix")!!, ref)
        val first = target.steps.first() as LaunchStep.OpenUri
        assertEquals("https://www.netflix.com/title/81280926", first.uri)
        assertEquals(listOf("com.netflix.ninja", "com.netflix.mediaclient"), first.packages)
        assertEquals("30", first.extras["source"])
        assertTrue(target.steps.last() is LaunchStep.LaunchApp)
        assertEquals("pagina serialului", target.hint)
    }

    @Test
    fun `apple disney and hulu use the movie or series path`() {
        val movieId = ServiceTitleId("umc.cmc.abc", ContentType.MOVIE)
        val showId = ServiceTitleId("umc.cmc.xyz", ContentType.SERIES)
        assertEquals("https://tv.apple.com/movie/umc.cmc.abc", com.ak.vodcalendar.core.launch.ServiceLinks.titleUrl("appletv", movieId))
        assertEquals("https://tv.apple.com/show/umc.cmc.xyz", com.ak.vodcalendar.core.launch.ServiceLinks.titleUrl("appletv", showId))
        assertEquals("https://www.disneyplus.com/series/x/umc.cmc.xyz", com.ak.vodcalendar.core.launch.ServiceLinks.titleUrl("disney", showId))
        assertEquals("https://www.hulu.com/movie/umc.cmc.abc", com.ak.vodcalendar.core.launch.ServiceLinks.titleUrl("hulu", movieId))
        assertNull(com.ak.vodcalendar.core.launch.ServiceLinks.titleUrl("skyshowtime", showId))
    }

    @Test
    fun `without an id the official app is just launched`() {
        val target = LaunchPlanner.streamingTarget(StreamingCatalog.byId("skyshowtime")!!, episode)
        val step = target.steps.single() as LaunchStep.LaunchApp
        assertTrue(step.packages.isEmpty())
        assertEquals(listOf("SkyShowtime"), step.launcherNames)
        assertEquals("doar aplicația", target.hint)
    }

    @Test
    fun `streaming targets respect installed apps and their region`() {
        val settings = AppSettings()
            .updateApp("netflix") { it.copy(installed = true, region = Region.US) }
            .updateApp("max") { it.copy(installed = true, region = Region.RO) }
            .updateApp("disney") { it.copy(installed = true, region = Region.RO) }
        val availability = listOf(
            ProviderAvailability(8, "Netflix", Region.US, null),
            // Max has it only in the US, but the user's Max is on RO.
            ProviderAvailability(1899, "Max", Region.US, null),
            ProviderAvailability(337, "Disney Plus", Region.RO, null),
            // Prime isn't installed.
            ProviderAvailability(119, "Amazon Prime Video", Region.RO, null),
        )
        val ids = LaunchPlanner.streamingTargets(episode, settings, availability, preferred = StreamingCatalog.byId("disney"))
            .map { it.id }
        assertEquals(listOf("disney", "netflix"), ids)
    }

    @Test
    fun `default player comes from settings`() {
        assertNull(LaunchPlanner.defaultPlayerTarget(episode, AppSettings()))
        val target = LaunchPlanner.defaultPlayerTarget(episode, AppSettings(alternativePlayerId = "nuvio"))
        assertEquals("nuvio", target?.id)
    }

    @Test
    fun `chooser lists every player and the system search last`() {
        val targets = LaunchPlanner.chooserTargets(episode)
        assertEquals(AlternativePlayers.all.size + 1, targets.size)
        assertEquals(LaunchTarget.SYSTEM_SEARCH_ID, targets.last().id)
        assertEquals("Severance", (targets.last().steps.single() as LaunchStep.GlobalSearch).query)
    }
}
