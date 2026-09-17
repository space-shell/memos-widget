package dev.jamesnicholls.memoswidget.util

import dev.jamesnicholls.memoswidget.net.MemoSummary
import java.time.LocalDate
import java.time.ZoneId

/**
 * Derives widget statistics from recent memos: the current day's memos and
 * per-day counts for the contribution-style heatmap.
 */
object MemoStats {

    fun todaysMemos(
        memos: List<MemoSummary>,
        zone: ZoneId,
        today: LocalDate,
    ): List<MemoSummary> = memos.filter { memo ->
        IsoTimes.parseInstant(memo.createTime)?.atZone(zone)?.toLocalDate() == today
    }

    /**
     * Counts memos per day for the [days] days ending today (inclusive).
     * Keys are "yyyy-MM-dd"; days with no memos are omitted.
     */
    fun dailyCounts(
        memos: List<MemoSummary>,
        zone: ZoneId,
        today: LocalDate,
        days: Int,
    ): Map<String, Int> {
        val cutoff = today.minusDays((days - 1).toLong())
        val counts = mutableMapOf<String, Int>()
        memos.forEach { memo ->
            val date = IsoTimes.parseInstant(memo.createTime)?.atZone(zone)?.toLocalDate() ?: return@forEach
            if (date.isBefore(cutoff) || date.isAfter(today)) return@forEach
            counts.merge(date.toString(), 1, Int::plus)
        }
        return counts
    }
}
