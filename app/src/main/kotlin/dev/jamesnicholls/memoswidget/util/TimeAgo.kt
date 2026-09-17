package dev.jamesnicholls.memoswidget.util

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TimeAgo {

    private val dayMonthFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM").withZone(ZoneId.systemDefault())

    /**
     * Formats an ISO-8601 timestamp as a compact relative label for widget subtext.
     * Returns "" when the timestamp is missing, unparsable, or in the future.
     */
    fun format(isoTimestamp: String?, now: Instant = Instant.now()): String {
        val instant = parse(isoTimestamp) ?: return ""
        val diff = Duration.between(instant, now)
        if (diff.isNegative) return ""
        return when {
            diff.seconds < 60 -> "just now"
            diff.toMinutes() < 60 -> "${diff.toMinutes()}m ago"
            diff.toHours() < 24 -> "${diff.toHours()}h ago"
            diff.toDays() < 7 -> "${diff.toDays()}d ago"
            else -> dayMonthFormat.format(instant)
        }
    }

    private fun parse(raw: String?): Instant? = IsoTimes.parseInstant(raw)
}
