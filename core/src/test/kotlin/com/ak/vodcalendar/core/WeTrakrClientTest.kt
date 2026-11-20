package com.ak.vodcalendar.core

import com.ak.vodcalendar.core.model.AppSettings
import com.ak.vodcalendar.core.model.SavedShow
import com.ak.vodcalendar.core.wetrakr.WeTrakrClient
import com.ak.vodcalendar.core.wetrakr.WeTrakrException
import com.ak.vodcalendar.core.wetrakr.WeTrakrPollResult
import com.ak.vodcalendar.core.wetrakr.WeTrakrTokens
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WeTrakrClientTest {
    private val server = MockWebServer().apply { start() }
    private val client = WeTrakrClient(OkHttpClient(), baseUrl = server.url("/").toString(), appVersion = "0.5")

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `device code login`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"device_code":"DEV","user_code":"AB12CD","verification_url":"https://wetrakr.com/activate","expires_in":600,"interval":5}""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"authorization_pending"}"""))
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":"slow_down"}"""))
        server.enqueue(MockResponse().setBody("""{"access_token":"AT","refresh_token":"RT","expires_in":604800}"""))

        val code = client.requestDeviceCode("KEY")
        assertEquals("AB12CD", code.userCode)
        assertEquals("DEV", code.deviceCode)
        val first = server.takeRequest()
        assertEquals("/oauth/device/code", first.requestUrl!!.encodedPath)
        assertEquals("1", first.getHeader("wetrakr-api-version"))
        assertEquals("KEY", first.getHeader("wetrakr-api-key"))
        assertEquals("KEY", Json.parseToJsonElement(first.body.readUtf8()).jsonObject["client_id"]!!.jsonPrimitive.content)

        assertEquals(WeTrakrPollResult.Pending, client.pollDeviceCode("KEY", "DEV"))
        val poll = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("DEV", poll["code"]!!.jsonPrimitive.content)
        assertEquals("KEY", poll["client_id"]!!.jsonPrimitive.content)
        assertEquals(WeTrakrPollResult.SlowDown, client.pollDeviceCode("KEY", "DEV"))
        assertEquals(WeTrakrPollResult.Authorized(WeTrakrTokens("AT", "RT")), client.pollDeviceCode("KEY", "DEV"))
    }

    @Test
    fun `denied and expired codes stop the login`() = runTest {
        server.enqueue(MockResponse().setResponseCode(418))
        server.enqueue(MockResponse().setResponseCode(410))
        assertTrue(client.pollDeviceCode("KEY", "DEV") is WeTrakrPollResult.Failed)
        assertTrue(client.pollDeviceCode("KEY", "DEV") is WeTrakrPollResult.Failed)
    }

    @Test
    fun `library reads followed and watched titles by TMDB id`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                val body = when (url.encodedPath) {
                    "/sync/tracking/watching/shows" -> """[{"type":"show","id":10,"title":"Severance","ids":{"tmdb":95396,"imdb":"tt11280740"}}]"""
                    "/sync/tracking/waiting/shows" -> """[{"type":"show","id":11,"title":"The Bear","ids":{"tmdb":136315}}]"""
                    "/sync/tracking/planning/shows" -> """[{"type":"show","id":12,"title":"No TMDB","ids":{}},{"type":"show","id":10,"title":"Severance","ids":{"tmdb":95396}}]"""
                    "/sync/tracking/planning/movies" -> """[{"type":"movie","id":20,"title":"Dune","ids":{"tmdb":438631}}]"""
                    "/sync/tracking/watched/episodes" -> {
                        assertEquals("true", url.queryParameter("compact"))
                        if (url.queryParameter("after") == null) {
                            return MockResponse().addHeader("X-Pagination-Next", "C2")
                                .setBody("""[{"type":"episode","id":1,"show_ids":{"tmdb":95396},"season_number":1,"number":1}]""")
                        }
                        assertEquals("C2", url.queryParameter("after"))
                        """[{"type":"episode","id":2,"show_ids":{"tmdb":"95396"},"season_number":1,"number":2}]"""
                    }
                    "/sync/tracking/watched/movies" -> """[{"type":"movie","id":21,"ids":{"tmdb":27205}}]"""
                    else -> return MockResponse().setResponseCode(404)
                }
                assertEquals("Bearer TOKEN", request.getHeader("Authorization"))
                return MockResponse().setBody(body)
            }
        }

        val library = client.library("KEY", "TOKEN")
        assertEquals(listOf(SavedShow(95396, "Severance"), SavedShow(136315, "The Bear")), library.followedShows)
        assertEquals(listOf(SavedShow(438631, "Dune")), library.followedMovies)
        assertEquals(setOf("95396-1-1", "95396-1-2"), library.watchedEpisodes)
        assertEquals(setOf(27205), library.watchedMovies)
    }

    @Test
    fun `marking an episode watched sends it nested under the show`() = runTest {
        server.enqueue(MockResponse().setBody("""{"added":{"plays":[]}}"""))
        server.enqueue(MockResponse().setBody("""{"removed":{}}"""))
        client.markEpisodeWatched("KEY", "TOKEN", 95396, "tt11280740", 2, 3)
        val add = server.takeRequest()
        assertEquals("/sync/tracking", add.requestUrl!!.encodedPath)
        val show = Json.parseToJsonElement(add.body.readUtf8()).jsonObject["shows"]!!.jsonArray[0].jsonObject
        assertEquals(95396, show["ids"]!!.jsonObject["tmdb"]!!.jsonPrimitive.int)
        val season = show["seasons"]!!.jsonArray[0].jsonObject
        assertEquals(2, season["number"]!!.jsonPrimitive.int)
        val ep = season["episodes"]!!.jsonArray[0].jsonObject
        assertEquals(3, ep["number"]!!.jsonPrimitive.int)
        assertEquals("watched", ep["status"]!!.jsonPrimitive.content)

        client.markMovieWatched("KEY", "TOKEN", 27205, null, watched = false)
        val remove = server.takeRequest()
        assertEquals("/sync/tracking/remove", remove.requestUrl!!.encodedPath)
        val movie = Json.parseToJsonElement(remove.body.readUtf8()).jsonObject["movies"]!!.jsonArray[0].jsonObject
        assertEquals("watched", movie["status"]!!.jsonPrimitive.content)
    }

    @Test
    fun `expired token is reported as unauthorized`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"INVALID_TOKEN"}"""))
        try {
            client.markMovieWatched("KEY", "OLD", 1, null)
            fail()
        } catch (e: WeTrakrException) {
            assertTrue(e.unauthorized)
        }
        server.enqueue(MockResponse().setBody("""{"access_token":"NEW","refresh_token":"RT2"}"""))
        assertEquals(WeTrakrTokens("NEW", "RT2"), client.refresh("KEY", "RT"))
    }

    @Test
    fun `WeTrakr lists count as followed`() {
        val settings = AppSettings(wetrakrShows = listOf(SavedShow(1, "A")), wetrakrMovies = listOf(SavedShow(5, "M")))
        assertTrue(settings.isFollowed(1))
        assertTrue(settings.isMovieFollowed(5))
        assertEquals(listOf(1), settings.followedShowIds())
        assertFalse(settings.unfollow(1).isFollowed(1))
        assertFalse(settings.hide(SavedShow(1, "A")).isFollowed(1))
    }
}
