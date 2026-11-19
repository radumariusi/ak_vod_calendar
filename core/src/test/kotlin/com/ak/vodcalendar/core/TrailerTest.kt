package com.ak.vodcalendar.core

import com.ak.vodcalendar.core.tmdb.VideoDto
import com.ak.vodcalendar.core.tmdb.VideosDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrailerTest {
    @Test
    fun `official youtube trailer wins over teasers and other sites`() {
        val videos = VideosDto(
            listOf(
                VideoDto(key = "vimeo1", site = "Vimeo", type = "Trailer", official = true),
                VideoDto(key = "teaser", site = "YouTube", type = "Teaser", official = true),
                VideoDto(key = "fan", site = "YouTube", type = "Trailer", official = false),
                VideoDto(key = "official", site = "YouTube", type = "Trailer", official = true),
                VideoDto(key = "clip", site = "YouTube", type = "Clip", official = true),
            ),
        )
        assertEquals("official", videos.bestTrailer()?.key)
    }

    @Test
    fun `teaser when there is no trailer, nothing for clips only`() {
        assertEquals("t", VideosDto(listOf(VideoDto(key = "t", site = "YouTube", type = "Teaser"))).bestTrailer()?.key)
        assertNull(VideosDto(listOf(VideoDto(key = "c", site = "YouTube", type = "Clip"))).bestTrailer())
    }
}
