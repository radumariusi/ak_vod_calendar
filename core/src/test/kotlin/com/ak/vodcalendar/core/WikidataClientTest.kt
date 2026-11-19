package com.ak.vodcalendar.core

import com.ak.vodcalendar.core.launch.ContentType
import com.ak.vodcalendar.core.launch.ServiceTitleId
import com.ak.vodcalendar.core.launch.WikidataClient
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WikidataClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: WikidataClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = WikidataClient(OkHttpClient(), server.url("/").toString())
    }

    @After
    fun tearDown() = server.shutdown()

    private fun claim(value: String, rank: String = "normal") =
        """{"rank":"$rank","mainsnak":{"datavalue":{"value":"$value","type":"string"}}}"""

    private val body = """
        {"entities":{"Q123":{"id":"Q123","claims":{
          "P1874":[${claim("80000001", "deprecated")},${claim("81280926")}],
          "P7595":[${claim("movie-id")}],
          "P7596":[${claim("series-id")}],
          "P9751":[${claim("umc.cmc.show")}],
          "P8055":[${claim("0ABCDEF")}, ${claim("0PREFERRED", "preferred")}]
        }}}}
    """.trimIndent()

    @Test
    fun `reads the service ids for a series`() = runTest {
        server.enqueue(MockResponse().setBody(body))
        val ids = client.ids("q123", ContentType.SERIES)
        assertEquals(ServiceTitleId("81280926", ContentType.SERIES), ids["netflix"])
        assertEquals(ServiceTitleId("series-id", ContentType.SERIES), ids["disney"])
        assertEquals(ServiceTitleId("umc.cmc.show", ContentType.SERIES), ids["appletv"])
        assertEquals("0PREFERRED", ids["prime"]?.id)
        assertEquals("/wiki/Special:EntityData/Q123.json", server.takeRequest().path)
    }

    @Test
    fun `movie ignores series-only properties and answers are cached`() = runTest {
        server.enqueue(MockResponse().setBody(body))
        val ids = client.ids("Q123", ContentType.MOVIE)
        assertEquals("movie-id", ids["disney"]?.id)
        assertTrue("appletv" !in ids)
        client.ids("Q123", ContentType.MOVIE)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `errors and bad ids give nothing`() = runTest {
        assertTrue(client.ids("tt123", ContentType.MOVIE).isEmpty())
        server.enqueue(MockResponse().setResponseCode(500))
        assertTrue(client.ids("Q9", ContentType.MOVIE).isEmpty())
        // A failure isn't cached.
        server.enqueue(MockResponse().setBody(body.replace("Q123", "Q9")))
        assertEquals("81280926", client.ids("Q9", ContentType.MOVIE)["netflix"]?.id)
    }
}
