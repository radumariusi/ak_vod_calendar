package com.ak.vodcalendar.core

import com.ak.vodcalendar.core.model.Region
import com.ak.vodcalendar.core.time.ReleaseTimeResolver
import com.ak.vodcalendar.core.time.RomaniaTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ReleaseTimeResolverTest {
    private val resolver = ReleaseTimeResolver()

    @Test
    fun `US broadcast episode lands next night in Romania`() {
        val r = resolver.resolve(LocalDate.of(2026, 11, 19), listOf("US"), setOf(6), Region.US)
        assertEquals(RomaniaTime.ZONE, r.at.zone)
        // 21:00 New York (EST) = 04:00 Bucharest (EET) next day
        assertEquals(LocalDateTime.of(2026, 11, 20, 4, 0), r.at.toLocalDateTime())
        assertTrue(r.approximate)
    }

    @Test
    fun `Netflix original drops at midnight Pacific`() {
        val r = resolver.resolve(LocalDate.of(2026, 11, 19), listOf("US"), setOf(213), Region.RO)
        // 00:00 PST = 10:00 EET same day
        assertEquals(LocalDateTime.of(2026, 11, 19, 10, 0), r.at.toLocalDateTime())
    }

    @Test
    fun `summer time is handled on both sides`() {
        val r = resolver.resolve(LocalDate.of(2026, 7, 1), listOf("US"), setOf(213), Region.US)
        // 00:00 PDT = 10:00 EEST
        assertEquals(LocalDateTime.of(2026, 7, 1, 10, 0), r.at.toLocalDateTime())
    }

    @Test
    fun `HBO is treated as cable, not streaming`() {
        val r = resolver.resolve(LocalDate.of(2026, 11, 22), listOf("US"), setOf(49), Region.RO)
        assertEquals(LocalDateTime.of(2026, 11, 23, 4, 0), r.at.toLocalDateTime())
    }

    @Test
    fun `UK show at 21 London is 23 in Romania`() {
        val r = resolver.resolve(LocalDate.of(2026, 11, 19), listOf("GB"), emptySet(), null)
        assertEquals(LocalDateTime.of(2026, 11, 19, 23, 0), r.at.toLocalDateTime())
    }
}
