package com.ak.vodcalendar.core

import com.ak.vodcalendar.core.model.AppSettings
import com.ak.vodcalendar.core.model.SavedShow
import com.ak.vodcalendar.core.simkl.SimklClient
import com.ak.vodcalendar.core.simkl.SimklException
import com.ak.vodcalendar.core.simkl.SimklPollResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimklClientTest {
    private val server = MockWebServer().apply { start() }
    private val client = SimklClient(OkHttpClient(), baseUrl = server.url("/").toString(), appVersion = "0.5")

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `device code login`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"result":"OK","device_code":"DEVICE","user_code":"AB12C","verification_url":"https://simkl.com/pin/","expires_in":900,"interval":5}""",
            ),
        )
        server.enqueue(MockResponse().setBody("""{"result":"KO","message":"Authorization pending"}"""))
        server.enqueue(MockResponse().setBody("""{"result":"KO","message":"Slow down"}"""))
        server.enqueue(MockResponse().setBody("""{"result":"OK","access_token":"TOKEN"}"""))

        val code = client.requestDeviceCode("CID")
        assertEquals("AB12C", code.userCode)
        assertEquals(5, code.intervalSeconds)
        val first = server.takeRequest()
        assertEquals("/oauth/pin", first.requestUrl!!.encodedPath)
        assertEquals("CID", first.requestUrl!!.queryParameter("client_id"))
        assertEquals("akvodcalendar", first.requestUrl!!.queryParameter("app-name"))
        assertEquals("CID", first.getHeader("simkl-api-key"))

        assertEquals(SimklPollResult.Pending, client.pollDeviceCode("CID", "AB12C"))
        assertEquals("/oauth/pin/AB12C", server.takeRequest().requestUrl!!.encodedPath)
        assertEquals(SimklPollResult.SlowDown, client.pollDeviceCode("CID", "AB12C"))
        assertEquals(SimklPollResult.Authorized("TOKEN"), client.pollDeviceCode("CID", "AB12C"))
    }

    @Test
    fun `library reads followed and watched titles by TMDB id`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"shows":[
                  {"status":"watching","show":{"title":"Severance","ids":{"simkl":1,"tmdb":"95396","imdb":"tt11280740"}},
                   "seasons":[{"number":2,"episodes":[{"number":1,"watched_at":"2025-01-17T10:00:00Z"},{"number":2}]}]},
                  {"status":"plantowatch","show":{"title":"New One","ids":{"tmdb":123}}},
                  {"status":"completed","show":{"title":"Old","ids":{"tmdb":"77"}},"seasons":[{"number":1,"episodes":[{"number":8}]}]},
                  {"status":"watching","show":{"title":"No TMDB","ids":{"simkl":5}}}
                ]}
                """.trimIndent(),
            ),
        )
        server.enqueue(MockResponse().setBody("null"))
        server.enqueue(
            MockResponse().setBody(
                """
                {"movies":[
                  {"status":"plantowatch","movie":{"title":"Dune 3","ids":{"tmdb":"1000"}}},
                  {"status":"completed","movie":{"title":"Dune","ids":{"tmdb":438631}}}
                ]}
                """.trimIndent(),
            ),
        )

        val library = client.library("CID", "TOKEN")

        assertEquals(listOf(SavedShow(95396, "Severance"), SavedShow(123, "New One")), library.followedShows)
        assertEquals(setOf("95396-2-1", "95396-2-2", "77-1-8"), library.watchedEpisodes)
        assertEquals(listOf(SavedShow(1000, "Dune 3")), library.followedMovies)
        assertEquals(setOf(438631), library.watchedMovies)

        val shows = server.takeRequest()
        assertEquals("/sync/all-items/shows/", shows.requestUrl!!.encodedPath)
        assertEquals("Bearer TOKEN", shows.getHeader("Authorization"))
        assertEquals("/sync/all-items/anime/", server.takeRequest().requestUrl!!.encodedPath)
        assertEquals("/sync/all-items/movies/", server.takeRequest().requestUrl!!.encodedPath)
    }

    @Test
    fun `mark episode watched sends the episode by TMDB id`() = runTest {
        server.enqueue(MockResponse().setBody("""{"added":{"episodes":1}}"""))
        client.markEpisodeWatched("CID", "TOKEN", showTmdbId = 95396, imdbId = "tt11280740", season = 2, episode = 3)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/sync/history", request.requestUrl!!.encodedPath)
        val show = Json.parseToJsonElement(request.body.readUtf8()).jsonObject["shows"]!!.jsonArray[0].jsonObject
        assertEquals(95396, show["ids"]!!.jsonObject["tmdb"]!!.jsonPrimitive.int)
        val season = show["seasons"]!!.jsonArray[0].jsonObject
        assertEquals(2, season["number"]!!.jsonPrimitive.int)
        assertEquals(3, season["episodes"]!!.jsonArray[0].jsonObject["number"]!!.jsonPrimitive.int)
    }

    @Test
    fun `expired token is reported as unauthorized`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val error = runCatching { client.library("CID", "OLD") }.exceptionOrNull()
        assertTrue(error is SimklException && error.unauthorized)
    }

    @Test
    fun `simkl lists count as followed unless hidden`() {
        val settings = AppSettings(simklShows = listOf(SavedShow(1, "A"), SavedShow(2, "B")), followedShows = listOf(SavedShow(3, "C")))
            .hide(SavedShow(2, "B"))
        assertTrue(settings.isFollowed(1))
        assertFalse(settings.isFollowed(2))
        assertEquals(listOf(3, 1), settings.followedShowIds())
    }
}
