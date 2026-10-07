package io.github.fenghanyue.photoeditor.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.createBitmap

/**
 * 把水印画到照片上。所有尺寸都按照片短边的百分比计算，
 * 所以缩小的预览图和原尺寸导出的效果一样。
 */
object WatermarkRenderer {

    /** 输出图的尺寸（宽, 高）。 */
    fun outputSize(width: Int, height: Int, options: WatermarkOptions): Pair<Int, Int> =
        when (options.template) {
            TemplateKind.FRAME -> width to height + FrameTemplate.barHeight(width, height)
            TemplateKind.OVERLAY -> width to height
        }

    /**
     * 参数边框会新建一张更高的图，photo 不变；
     * 信息叠加直接画在 photo 上（photo 必须可修改），返回的就是 photo。
     */
    fun render(photo: Bitmap, content: WatermarkContent, options: WatermarkOptions, assets: WatermarkAssets): Bitmap =
        when (options.template) {
            TemplateKind.FRAME -> {
                val (width, height) = outputSize(photo.width, photo.height, options)
                // 沿用原图的色彩空间，广色域照片（Display P3）不会被转成 sRGB；
                // HDR 照片的色彩空间（HLG、PQ）建不了 8 位的图，退回 sRGB
                val colorSpace = photo.colorSpace?.takeIf { it is ColorSpace.Rgb && it.transferParameters != null }
                    ?: ColorSpace.get(ColorSpace.Named.SRGB)
                val output = createBitmap(width, height, Bitmap.Config.ARGB_8888, hasAlpha = false, colorSpace = colorSpace)
                FrameTemplate.draw(Canvas(output), photo, content, options, assets)
                output
            }
            TemplateKind.OVERLAY -> {
                OverlayTemplate.draw(Canvas(photo), photo.width, photo.height, content, options, assets)
                photo
            }
        }
}

/**
 * 中文字体的字身框大约从基线上方 0.88 个字号延伸到基线下方 0.12 个字号。
 * 按这个框排版，中英文混排的一行在视觉上居中。
 */
internal const val EM_ASCENT = 0.88f

internal fun textPaint(typeface: Typeface, size: Float, color: Int): TextPaint =
    TextPaint(TextPaint.ANTI_ALIAS_FLAG or TextPaint.SUBPIXEL_TEXT_FLAG).apply {
        this.typeface = typeface
        textSize = size
        this.color = color
    }

/** 超过 maxWidth 时截断并加"…"。 */
internal fun ellipsize(text: String, paint: TextPaint, maxWidth: Float): String =
    if (paint.measureText(text) <= maxWidth) text
    else TextUtils.ellipsize(text, paint, maxWidth, TextUtils.TruncateAt.END).toString()
