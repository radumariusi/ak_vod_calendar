package com.ak.vodcalendar.core

import com.ak.vodcalendar.core.calendar.EpisodeCalendarRepository
import com.ak.vodcalendar.core.model.AppSettings
import com.ak.vodcalendar.core.model.ContentFilter
import com.ak.vodcalendar.core.model.Region
import com.ak.vodcalendar.core.tmdb.TmdbHttpApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class EpisodeCalendarRepositoryTest {
    private val server = MockWebServer()

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                val body = when {
                    path.endsWith("/discover/tv") -> DISCOVER
                    path.endsWith("/tv/1") -> SHOW_NETFLIX
                    path.endsWith("/tv/1/season/2") -> SEASON_NETFLIX
                    path.endsWith("/tv/2") -> SHOW_HBO
                    path.endsWith("/tv/2/season/1") -> SEASON_HBO
                    else -> return MockResponse().setResponseCode(404)
                }
                return MockResponse().setBody(body).setHeader("Content-Type", "application/json")
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun repo() = EpisodeCalendarRepository(apiFactory = { key ->
        TmdbHttpApi(OkHttpClient(), key, baseUrl = server.url("/3/").toString())
    })

    private fun settings(netflix: Region? = Region.US, max: Region? = Region.RO) = AppSettings().let { s ->
        var out = s
        if (netflix != null) out = out.updateApp("netflix") { it.copy(installed = true, region = netflix) }
        if (max != null) out = out.updateApp("max") { it.copy(installed = true, region = max) }
        out
    }

    @Test
    fun `builds today's calendar in Romania time with app and region`() = runTest {
        val day = LocalDate.of(2026, 11, 20)
        val entries = repo().episodesBetween(day, day, settings(), "test-key")

        // Netflix drop on 20th (10:00) and the HBO episode from the 19th US evening (04:00 on the 20th).
        assertEquals(listOf("Show HBO" to 3, "Show Netflix" to 5), entries.map { it.showName to it.episodeNumber })

        val hbo = entries[0]
        assertEquals(LocalDateTime.of(2026, 11, 20, 4, 0), hbo.releaseAt.toLocalDateTime())
        assertEquals("max", hbo.watchOn?.id)
        assertEquals(Region.RO, hbo.watchRegion)

        val netflix = entries[1]
        assertEquals(LocalDateTime.of(2026, 11, 20, 10, 0), netflix.releaseAt.toLocalDateTime())
        assertEquals("netflix", netflix.watchOn?.id)
        assertEquals(Region.US, netflix.watchRegion)
        assertEquals("tt123", netflix.imdbId)

        val apiKeys = (1..server.requestCount).map { server.takeRequest().requestUrl!!.queryParameter("api_key") }
        assertEquals(setOf("test-key"), apiKeys.toSet())
    }

    @Test
    fun `show only on a region the user does not use has no app`() = runTest {
        val day = LocalDate.of(2026, 11, 20)
        // Max is set to US but the show is only on Max RO.
        val entries = repo().episodesBetween(day, day, settings(max = Region.US), "k")
        val hbo = entries.first { it.showName == "Show HBO" }
        // Falls back to original network match (HBO network 49 belongs to Max).
        assertEquals("max", hbo.watchOn?.id)
        assertEquals(Region.US, hbo.watchRegion)

        val noApps = repo().episodesBetween(day, day, AppSettings(), "k")
        assertNull(noApps.first { it.showName == "Show HBO" }.watchOn)
    }

    @Test
    fun `hidden categories are dropped`() = runTest {
        val day = LocalDate.of(2026, 11, 20)
        val entries = repo().episodesBetween(day, day, AppSettings(hiddenCategories = setOf(ContentFilter.ANIME)), "k")
        assertEquals(false, entries.any { it.showName == "Anime" })
    }

    companion object {
        val DISCOVER = """
            {"page":1,"total_pages":1,"total_results":3,"results":[
              {"id":1,"name":"Show Netflix","original_language":"en","origin_country":["US"],"genre_ids":[18]},
              {"id":2,"name":"Show HBO","original_language":"en","origin_country":["US"],"genre_ids":[18]},
              {"id":3,"name":"Anime","original_language":"ja","origin_country":["JP"],"genre_ids":[16]}
            ]}
        """.trimIndent()

        val SHOW_NETFLIX = """
            {"id":1,"name":"Show Netflix","original_language":"en","origin_country":["US"],
             "genres":[{"id":18,"name":"Drama"}],"networks":[{"id":213,"name":"Netflix"}],
             "vote_average":8.1,"vote_count":1200,"poster_path":"/p.jpg",
             "last_episode_to_air":{"air_date":"2026-11-13","episode_number":4,"season_number":2},
             "next_episode_to_air":{"air_date":"2026-11-20","episode_number":5,"season_number":2},
             "watch/providers":{"results":{"US":{"flatrate":[{"provider_id":8,"provider_name":"Netflix"}]},
                                           "RO":{"flatrate":[{"provider_id":8,"provider_name":"Netflix"}]}}},
             "external_ids":{"imdb_id":"tt123"}}
        """.trimIndent()

        val SEASON_NETFLIX = """
            {"season_number":2,"episodes":[
              {"air_date":"2026-11-13","episode_number":4,"season_number":2,"name":"E4"},
              {"air_date":"2026-11-20","episode_number":5,"season_number":2,"name":"E5"},
              {"air_date":"2026-11-27","episode_number":6,"season_number":2,"name":"E6"}
            ]}
        """.trimIndent()

        val SHOW_HBO = """
            {"id":2,"name":"Show HBO","original_language":"en","origin_country":["US"],
             "genres":[{"id":18,"name":"Drama"}],"networks":[{"id":49,"name":"HBO"}],
             "vote_average":8.5,"vote_count":3000,
             "last_episode_to_air":{"air_date":"2026-11-19","episode_number":3,"season_number":1},
             "next_episode_to_air":{"air_date":"2026-11-26","episode_number":4,"season_number":1},
             "watch/providers":{"results":{"RO":{"flatrate":[{"provider_id":1899,"provider_name":"Max"}]}}}}
        """.trimIndent()

        val SEASON_HBO = """
            {"season_number":1,"episodes":[
              {"air_date":"2026-11-12","episode_number":2,"season_number":1},
              {"air_date":"2026-11-19","episode_number":3,"season_number":1},
              {"air_date":"2026-11-26","episode_number":4,"season_number":1}
            ]}
        """.trimIndent()
    }
}
