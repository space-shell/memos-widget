package dev.jamesnicholls.memoswidget.util

import java.time.Instant
import java.time.OffsetDateTime

object IsoTimes {

    /** Parses ISO-8601 instants ("...Z" or with offset); null when missing/unparsable. */
    fun parseInstant(raw: String?): Instant? {
        if (raw.isNullOrBlank()) return null
        return try {
            Instant.parse(raw)
        } catch (_: Exception) {
            try {
                OffsetDateTime.parse(raw).toInstant()
            } catch (_: Exception) {
                null
            }
        }
    }
}
