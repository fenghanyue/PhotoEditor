package io.github.fenghanyue.photoeditor.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.withTranslation
import kotlin.math.max
import kotlin.math.min

/**
 * 信息叠加：在照片一角叠一块信息。
 * 第一行大号时间 + 黄色竖条 + 日期/星期，下面依次是地点、坐标海拔、备注。
 */
internal object OverlayTemplate {

    private const val ACCENT = 0xFFF5B800.toInt()
    private const val TEXT = 0xFFFFFFFF.toInt()
    private const val TEXT_SECONDARY = 0xE6FFFFFF.toInt()
    private const val BACKDROP = 0x59000000
    private const val SHADOW = 0x80000000.toInt()

    /** 备注最宽占照片宽度的比例，最多显示几行。 */
    private const val NOTE_WIDTH_RATIO = 0.6f
    private const val NOTE_MAX_LINES = 3

    private interface Row {
        val width: Float
        val height: Float
        fun draw(canvas: Canvas, x: Float, top: Float)
    }

    fun draw(
        canvas: Canvas,
        width: Int,
        height: Int,
        content: WatermarkContent,
        options: WatermarkOptions,
        assets: WatermarkAssets,
    ) {
        val unit = min(width, height) / 100f
        val margin = 4f * unit
        val padding = if (options.backdrop) 2.4f * unit else 0f
        val maxWidth = width - 2 * (margin + padding)
        val alignRight = options.corner == Corner.BOTTOM_RIGHT || options.corner == Corner.TOP_RIGHT
        val alignBottom = options.corner == Corner.BOTTOM_LEFT || options.corner == Corner.BOTTOM_RIGHT
        // 没有底板时给文字加一圈阴影，免得在亮背景上看不清
        val shadow = if (options.backdrop) 0f else 0.4f * unit

        fun paint(typeface: Typeface, size: Float, color: Int) = textPaint(typeface, size, color).apply {
            if (shadow > 0f) setShadowLayer(shadow, 0f, shadow / 3, SHADOW)
        }

        val rows = buildList {
            content.time?.let {
                add(
                    timeRow(
                        time = it,
                        column = listOfNotNull(content.date, content.weekday),
                        unit = unit,
                        big = paint(assets.bold, 8f * unit, TEXT),
                        small = paint(assets.regular, 2.6f * unit, TEXT),
                    ),
                )
            }
            content.location?.let { add(textRow(it, paint(assets.regular, 2.8f * unit, TEXT), maxWidth)) }
            if (options.showCoordinates) {
                listOfNotNull(content.coordinates, content.altitude).joinToString("  ").ifEmpty { null }
                    ?.let { add(textRow(it, paint(assets.regular, 2.2f * unit, TEXT_SECONDARY), maxWidth)) }
            }
            content.note?.let {
                val noteWidth = min(maxWidth, width * NOTE_WIDTH_RATIO)
                add(noteRow(it, paint(assets.regular, 2.4f * unit, TEXT), noteWidth, alignRight))
            }
        }
        if (rows.isEmpty()) return

        val gap = 1.2f * unit
        val blockWidth = rows.maxOf { it.width }
        val blockHeight = rows.sumOf { it.height.toDouble() }.toFloat() + gap * (rows.size - 1)
        val outerWidth = blockWidth + 2 * padding
        val outerHeight = blockHeight + 2 * padding
        val left = if (alignRight) width - margin - outerWidth else margin
        val top = if (alignBottom) height - margin - outerHeight else margin
        if (options.backdrop) {
            val radius = 1.6f * unit
            val backdrop = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BACKDROP }
            canvas.drawRoundRect(left, top, left + outerWidth, top + outerHeight, radius, radius, backdrop)
        }
        var y = top + padding
        for (row in rows) {
            val x = if (alignRight) left + padding + blockWidth - row.width else left + padding
            row.draw(canvas, x, y)
            y += row.height + gap
        }
    }

    private fun textRow(text: String, paint: TextPaint, maxWidth: Float): Row {
        val shown = ellipsize(text, paint, maxWidth)
        return object : Row {
            override val width = paint.measureText(shown)
            override val height = paint.textSize
            override fun draw(canvas: Canvas, x: Float, top: Float) {
                canvas.drawText(shown, x, top + EM_ASCENT * paint.textSize, paint)
            }
        }
    }

    private fun noteRow(text: String, paint: TextPaint, maxWidth: Float, alignRight: Boolean): Row {
        val layoutWidth = min(maxWidth, paint.measureText(text)).toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, layoutWidth)
            .setAlignment(if (alignRight) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(NOTE_MAX_LINES)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()
        return object : Row {
            override val width = layoutWidth.toFloat()
            override val height = layout.height.toFloat()
            override fun draw(canvas: Canvas, x: Float, top: Float) {
                canvas.withTranslation(x, top) { layout.draw(this) }
            }
        }
    }

    /** 大号时间，右边一条黄色竖条，竖条右边上下两行日期和星期。 */
    private fun timeRow(time: String, column: List<String>, unit: Float, big: TextPaint, small: TextPaint): Row {
        val bounds = Rect().also { big.getTextBounds(time, 0, time.length, it) }
        val timeWidth = big.measureText(time)
        val columnGap = 0.5f * unit
        val columnHeight = if (column.isEmpty()) 0f else column.size * small.textSize + columnGap * (column.size - 1)
        val columnWidth = column.maxOfOrNull { small.measureText(it) } ?: 0f
        val spacing = 1.4f * unit
        val barWidth = 0.5f * unit
        val rowHeight = max(bounds.height().toFloat(), columnHeight)
        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT }
        return object : Row {
            override val width = if (column.isEmpty()) timeWidth else timeWidth + 2 * spacing + barWidth + columnWidth
            override val height = rowHeight
            override fun draw(canvas: Canvas, x: Float, top: Float) {
                val glyphTop = top + (rowHeight - bounds.height()) / 2
                canvas.drawText(time, x, glyphTop - bounds.top, big)
                if (column.isEmpty()) return
                val barX = x + timeWidth + spacing
                canvas.drawRect(barX, top, barX + barWidth, top + rowHeight, accent)
                val textX = barX + barWidth + spacing
                var lineTop = top + (rowHeight - columnHeight) / 2
                for (line in column) {
                    canvas.drawText(line, textX, lineTop + EM_ASCENT * small.textSize, small)
                    lineTop += small.textSize + columnGap
                }
            }
        }
    }
}
