package com.ak.vodcalendar.core

import com.ak.vodcalendar.core.update.GitHubUpdateChecker
import com.ak.vodcalendar.core.update.UpdateException
import com.ak.vodcalendar.core.update.Versions
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    private val server = MockWebServer().apply { start() }
    private val checker = GitHubUpdateChecker(OkHttpClient(), "radu/app", server.url("/").toString())

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `reads the latest release and its apk`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"tag_name":"v0.4.12","name":"AK VOD Calendar 0.4.12","body":"Etapa 4","published_at":"2026-11-19T22:00:00Z",
                   "assets":[{"name":"notes.txt","browser_download_url":"https://x/notes.txt","size":3},
                             {"name":"ak-vod-calendar-0.4.12.apk","browser_download_url":"https://x/a.apk","size":9969558}]}""",
            ),
        )
        val release = checker.latest()!!
        assertEquals("0.4.12", release.versionName)
        assertEquals("https://x/a.apk", release.apkUrl)
        assertEquals(9969558L, release.apkSizeBytes)
        assertEquals("Etapa 4", release.notes)
        assertEquals("/repos/radu/app/releases/latest", server.takeRequest().path)
    }

    @Test
    fun `no release yet or no apk gives null`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        assertNull(checker.latest())
        server.enqueue(MockResponse().setBody("""{"tag_name":"v1","assets":[]}"""))
        assertNull(checker.latest())
    }

    @Test(expected = UpdateException::class)
    fun `server errors are reported`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        checker.latest()
    }

    @Test
    fun `version comparison`() {
        assertTrue(Versions.isNewer("0.4.12", "0.4.9"))
        assertTrue(Versions.isNewer("0.4.1", "0.4.0"))
        assertTrue(Versions.isNewer("0.5.13", "0.4.12"))
        assertTrue(Versions.isNewer("v1.0", "0.9.99"))
        assertFalse(Versions.isNewer("0.4.12", "0.4.12"))
        assertFalse(Versions.isNewer("0.4.9", "0.4.12"))
        assertFalse(Versions.isNewer("", "0.4.0"))
        assertFalse(Versions.isNewer("0.4.12", "0.4.12-debug"))
    }
}
