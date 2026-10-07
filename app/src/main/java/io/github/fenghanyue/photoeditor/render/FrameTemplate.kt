package io.github.fenghanyue.photoeditor.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 参数边框：照片下方加一条边框条。
 * 左边两行：机型（粗）、时间等（灰）；右边：Logo | 两行：参数（粗）、地点等（灰）。
 */
internal object FrameTemplate {

    /** 边框条高度占照片短边的比例。 */
    private const val BAR_RATIO = 0.09f

    /** 放不下时字号最多缩到 70%，再放不下就截断。 */
    private const val MIN_SCALE = 0.7f

    private class Palette(val background: Int, val primary: Int, val secondary: Int, val divider: Int)

    private val WHITE = Palette(0xFFFFFFFF.toInt(), 0xFF1A1A1A.toInt(), 0xFF8C8C8C.toInt(), 0xFFD9D9D9.toInt())
    private val BLACK = Palette(0xFF111111.toInt(), 0xFFF2F2F2.toInt(), 0xFF9E9E9E.toInt(), 0xFF3A3A3A.toInt())

    fun barHeight(width: Int, height: Int): Int = (min(width, height) * BAR_RATIO).roundToInt()

    fun draw(canvas: Canvas, photo: Bitmap, content: WatermarkContent, options: WatermarkOptions, assets: WatermarkAssets) {
        val width = photo.width.toFloat()
        val barTop = photo.height.toFloat()
        val barHeight = barHeight(photo.width, photo.height).toFloat()
        val unit = min(photo.width, photo.height) / 100f
        val palette = if (options.frameColor == FrameColor.WHITE) WHITE else BLACK

        canvas.drawBitmap(photo, 0f, 0f, null)
        canvas.drawRect(0f, barTop, width, barTop + barHeight, Paint().apply { color = palette.background })

        val logo = if (options.showLogo) assets.logo(content.brand) else null
        val text = Texts(
            left = listOf(content.deviceName, leftDetail(content, options.frameLeftDetail)),
            right = listOf(content.params, rightDetail(content, options.frameRightDetail)),
            brand = if (options.showLogo && logo == null) content.brand else null,
        )
        val padding = 4f * unit
        val available = width - 2 * padding

        var layout = Layout(text, Sizes(unit, 1f), logo, assets, palette)
        if (layout.totalWidth > available) {
            layout = Layout(text, Sizes(unit, max(MIN_SCALE, available / layout.totalWidth)), logo, assets, palette)
        }
        if (layout.totalWidth > available) {
            // 字号已经缩到底，按两边原本的宽度比例分配剩下的空间，超出的截断
            val room = available - layout.fixedWidth
            val textWidth = layout.leftWidth + layout.rightTextWidth
            val leftShare = if (textWidth > 0f) layout.leftWidth / textWidth else 0.5f
            layout = Layout(text, layout.sizes, logo, assets, palette, room * leftShare, room * (1 - leftShare))
        }
        layout.draw(canvas, padding, width - padding, barTop + barHeight / 2, barHeight)
    }

    private fun leftDetail(content: WatermarkContent, detail: FrameLeftDetail): String? = when (detail) {
        FrameLeftDetail.TIME -> content.dateTime
        FrameLeftDetail.LENS -> content.lens
        FrameLeftDetail.SIGNATURE -> content.signature
    }

    private fun rightDetail(content: WatermarkContent, detail: FrameRightDetail): String? = when (detail) {
        // 没填地点时，有坐标就印坐标
        FrameRightDetail.LOCATION -> content.location ?: content.coordinates
        FrameRightDetail.COORDINATES -> content.coordinates
        FrameRightDetail.LENS -> content.lens
        FrameRightDetail.NONE -> null
    }

    /** 每一列的两行文字（第一行粗体，第二行灰色），以及没有 Logo 时代替 Logo 的品牌名。 */
    private class Texts(val left: List<String?>, val right: List<String?>, val brand: String?)

    /** 各尺寸，单位是像素；unit 是照片短边的 1%。 */
    private class Sizes(unit: Float, scale: Float) {
        val primary = 2.6f * unit * scale
        val secondary = 2.0f * unit * scale
        val lineGap = 0.9f * unit * scale
        val groupGap = 1.6f * unit * scale
        val sideGap = 3f * unit * scale
        val logoHeight = 4.6f * unit * scale
        val brandText = 3.0f * unit * scale
        val dividerWidth = max(1f, 0.12f * unit * scale)
        val dividerHeight = 5.2f * unit * scale
    }

    private class Layout(
        texts: Texts,
        val sizes: Sizes,
        private val logo: WatermarkAssets.Logo?,
        assets: WatermarkAssets,
        palette: Palette,
        maxLeftWidth: Float = Float.MAX_VALUE,
        maxRightWidth: Float = Float.MAX_VALUE,
    ) {
        private val left = lines(texts.left, assets, palette, maxLeftWidth)
        private val right = lines(texts.right, assets, palette, maxRightWidth)
        private val brandPaint = texts.brand?.let { textPaint(assets.bold, sizes.brandText, palette.primary) }
        private val brand = texts.brand
        private val dividerPaint = Paint().apply { color = palette.divider }

        val leftWidth = left.maxOfOrNull { (line, paint) -> paint.measureText(line) } ?: 0f
        val rightTextWidth = right.maxOfOrNull { (line, paint) -> paint.measureText(line) } ?: 0f
        private val markWidth = when {
            logo != null -> sizes.logoHeight * logo.aspectRatio
            brand != null && brandPaint != null -> brandPaint.measureText(brand)
            else -> 0f
        }
        private val separatorWidth = if (markWidth > 0f && right.isNotEmpty()) 2 * sizes.groupGap + sizes.dividerWidth else 0f
        private val sideGap = if (leftWidth > 0f && (rightTextWidth > 0f || markWidth > 0f)) sizes.sideGap else 0f

        /** 除了两列文字之外占用的宽度。 */
        val fixedWidth = markWidth + separatorWidth + sideGap
        val totalWidth = leftWidth + rightTextWidth + fixedWidth

        private fun lines(texts: List<String?>, assets: WatermarkAssets, palette: Palette, maxWidth: Float) =
            texts.mapIndexedNotNull { index, text ->
                val paint = if (index == 0) {
                    textPaint(assets.bold, sizes.primary, palette.primary)
                } else {
                    textPaint(assets.regular, sizes.secondary, palette.secondary)
                }
                text?.let { ellipsize(it, paint, maxWidth) to paint }
            }

        fun draw(canvas: Canvas, start: Float, end: Float, centerY: Float, barHeight: Float) {
            drawLines(canvas, left, start, centerY)
            val textX = end - rightTextWidth
            drawLines(canvas, right, textX, centerY)
            if (markWidth <= 0f) return
            val markEnd = if (right.isEmpty()) end else textX - separatorWidth
            val markStart = markEnd - markWidth
            if (logo != null) {
                val half = sizes.logoHeight / 2
                logo.draw(canvas, RectF(markStart, centerY - half, markEnd, centerY + half))
            } else if (brand != null && brandPaint != null) {
                canvas.drawText(brand, markStart, centerY + (EM_ASCENT - 0.5f) * sizes.brandText, brandPaint)
            }
            if (right.isNotEmpty()) {
                val dividerX = textX - sizes.groupGap - sizes.dividerWidth
                val half = min(sizes.dividerHeight, barHeight * 0.6f) / 2
                canvas.drawRect(dividerX, centerY - half, dividerX + sizes.dividerWidth, centerY + half, dividerPaint)
            }
        }

        /** 把一列文字在边框条里上下居中。 */
        private fun drawLines(canvas: Canvas, lines: List<Pair<String, TextPaint>>, x: Float, centerY: Float) {
            if (lines.isEmpty()) return
            val blockHeight = lines.sumOf { it.second.textSize.toDouble() }.toFloat() + sizes.lineGap * (lines.size - 1)
            var top = centerY - blockHeight / 2
            for ((line, paint) in lines) {
                canvas.drawText(line, x, top + EM_ASCENT * paint.textSize, paint)
                top += paint.textSize + sizes.lineGap
            }
        }
    }
}
