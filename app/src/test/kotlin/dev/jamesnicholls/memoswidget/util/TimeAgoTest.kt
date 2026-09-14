package dev.jamesnicholls.memoswidget.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class TimeAgoTest {

    private val now: Instant = Instant.parse("2026-09-14T12:00:00Z")

    @Test
    fun `null and blank return empty`() {
        assertEquals("", TimeAgo.format(null, now))
        assertEquals("", TimeAgo.format("", now))
        assertEquals("", TimeAgo.format("   ", now))
    }

    @Test
    fun `unparsable returns empty`() {
        assertEquals("", TimeAgo.format("not a timestamp", now))
    }

    @Test
    fun `future timestamps return empty`() {
        assertEquals("", TimeAgo.format("2026-09-14T12:05:00Z", now))
    }

    @Test
    fun `under a minute is just now`() {
        assertEquals("just now", TimeAgo.format("2026-09-14T11:59:30Z", now))
    }

    @Test
    fun `minutes`() {
        assertEquals("5m ago", TimeAgo.format("2026-09-14T11:55:00Z", now))
        assertEquals("59m ago", TimeAgo.format("2026-09-14T11:01:00Z", now))
    }

    @Test
    fun `hours`() {
        assertEquals("1h ago", TimeAgo.format("2026-09-14T11:00:00Z", now))
        assertEquals("23h ago", TimeAgo.format("2026-09-13T13:00:00Z", now))
    }

    @Test
    fun `days`() {
        assertEquals("1d ago", TimeAgo.format("2026-09-13T12:00:00Z", now))
        assertEquals("6d ago", TimeAgo.format("2026-09-08T12:00:00Z", now))
    }

    @Test
    fun `week and older falls back to date`() {
        val result = TimeAgo.format("2026-09-01T12:00:00Z", now)
        assertEquals(true, Regex("\\d{1,2} [A-Za-z]{3}").matches(result))
    }

    @Test
    fun `parses offset timestamps`() {
        // 12:00Z == 13:00+01:00 → same instant as now
        assertEquals("just now", TimeAgo.format("2026-09-14T13:00:00+01:00", now))
        // one hour earlier, expressed with offset
        assertEquals("1h ago", TimeAgo.format("2026-09-14T12:00:00+01:00", now))
    }
}
