package dev.jamesnicholls.memoswidget.util

import dev.jamesnicholls.memoswidget.net.MemoSummary
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class MemoStatsTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val today: LocalDate = LocalDate.of(2026, 9, 16)

    private fun memo(name: String, isoTime: String?) =
        MemoSummary(name = name, content = name, createTime = isoTime)

    @Test
    fun `todaysMemos keeps only memos from the current local day`() {
        val memos = listOf(
            memo("a", "2026-09-16T23:59:59Z"),
            memo("b", "2026-09-16T00:00:00Z"),
            memo("c", "2026-09-15T23:00:00Z"),
            memo("d", "2026-09-17T01:00:00Z"),
            memo("e", null),
        )

        val todays = MemoStats.todaysMemos(memos, zone, today)

        assertEquals(listOf("a", "b"), todays.map { it.name })
    }

    @Test
    fun `todaysMemos respects the zone`() {
        val zonePlus = ZoneId.of("UTC+2")
        val memos = listOf(
            memo("a", "2026-09-16T22:30:00Z"), // 2026-09-17 00:30 local in UTC+2
            memo("b", "2026-09-15T22:30:00Z"), // 2026-09-16 00:30 local in UTC+2
        )

        val todays = MemoStats.todaysMemos(memos, zonePlus, today)

        assertEquals(listOf("b"), todays.map { it.name })
    }

    @Test
    fun `dailyCounts buckets by local date within the window`() {
        val memos = listOf(
            memo("a", "2026-09-16T10:00:00Z"),
            memo("b", "2026-09-16T11:00:00Z"),
            memo("c", "2026-09-15T09:00:00Z"),
            memo("d", "2026-08-01T09:00:00Z"),  // older than 30 days
            memo("e", "2026-09-17T09:00:00Z"),  // future
            memo("f", null),
        )

        val counts = MemoStats.dailyCounts(memos, zone, today, days = 30)

        assertEquals(mapOf("2026-09-16" to 2, "2026-09-15" to 1), counts)
    }
}
