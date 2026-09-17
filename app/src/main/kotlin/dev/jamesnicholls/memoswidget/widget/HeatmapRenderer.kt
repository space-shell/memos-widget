package dev.jamesnicholls.memoswidget.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import java.time.LocalDate

/**
 * Renders the memo activity heatmap: the trailing [DAYS] days as one row of
 * squares (Monday-first), colour intensity = memos created that day.
 * RemoteViews cannot compose dynamic view grids, so the widget displays the
 * result in an ImageView; pass a cell size derived from the widget's actual
 * width so the row fills the available space with square cells.
 */
object HeatmapRenderer {

    const val DAYS = 7

    private val LEVEL_COLORS = intArrayOf(
        0x1FFFFFFF,            // 0 memos
        0xFF44466E.toInt(),    // 1
        0xFF5659B8.toInt(),    // 2–3
        0xFF8F93FF.toInt(),    // 4+
    )

    /**
     * Cell size (px) so that [DAYS] squares plus gaps exactly fill
     * [availableWidthPx].
     */
    fun cellSizeForWidth(availableWidthPx: Float, gapPx: Float): Float =
        ((availableWidthPx - (DAYS - 1) * gapPx) / DAYS).coerceAtLeast(1f)

    fun render(
        counts: Map<String, Int>,
        today: LocalDate,
        cellPx: Float,
        gapPx: Float,
    ): Bitmap {
        val width = (DAYS * (cellPx + gapPx) - gapPx).toInt().coerceAtLeast(1)
        val height = cellPx.toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Trailing DAYS days ending today, oldest on the left.
        val start = today.minusDays((DAYS - 1).toLong())
        for (column in 0 until DAYS) {
            val date = start.plusDays(column.toLong())
            val level = levelFor(counts[date.toString()] ?: 0)
            paint.color = LEVEL_COLORS[level]
            val left = column * (cellPx + gapPx)
            canvas.drawRoundRect(
                RectF(left, 0f, left + cellPx, cellPx),
                cellPx / 4f,
                cellPx / 4f,
                paint,
            )
        }
        return bitmap
    }

    private fun levelFor(count: Int): Int = when {
        count <= 0 -> 0
        count == 1 -> 1
        count <= 3 -> 2
        else -> 3
    }
}
