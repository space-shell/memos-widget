package dev.jamesnicholls.memoswidget.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import java.time.LocalDate

/**
 * Renders a GitHub-contribution-style heatmap (one column per week, one row
 * per day) into a bitmap. RemoteViews cannot compose dynamic view grids, so
 * the widget displays the result in an ImageView.
 */
object HeatmapRenderer {

    private val LEVEL_COLORS = intArrayOf(
        0x1FFFFFFF,            // 0 memos
        0xFF44466E.toInt(),    // 1
        0xFF5659B8.toInt(),    // 2–3
        0xFF8F93FF.toInt(),    // 4+
    )

    /**
     * @param counts map of "yyyy-MM-dd" to memo count
     * @param density display density for dp→px conversion
     */
    fun render(
        counts: Map<String, Int>,
        today: LocalDate,
        weeks: Int = DEFAULT_WEEKS,
        cellPx: Float,
        gapPx: Float,
    ): Bitmap {
        val rowToday = (today.dayOfWeek.value + 6) % 7 // Monday-first rows
        val gridStart = today.minusDays(rowToday.toLong() + (weeks - 1) * 7L)

        val width = (weeks * (cellPx + gapPx) - gapPx).toInt().coerceAtLeast(1)
        val height = (7 * (cellPx + gapPx) - gapPx).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        for (week in 0 until weeks) {
            for (day in 0 until 7) {
                val date = gridStart.plusDays(week * 7L + day)
                if (date.isAfter(today)) continue
                val level = levelFor(counts[date.toString()] ?: 0)
                paint.color = LEVEL_COLORS[level]
                val left = week * (cellPx + gapPx)
                val top = day * (cellPx + gapPx)
                canvas.drawRoundRect(
                    RectF(left, top, left + cellPx, top + cellPx),
                    cellPx / 4f,
                    cellPx / 4f,
                    paint,
                )
            }
        }
        return bitmap
    }

    private fun levelFor(count: Int): Int = when {
        count <= 0 -> 0
        count == 1 -> 1
        count <= 3 -> 2
        else -> 3
    }

    const val DEFAULT_WEEKS = 15
}
