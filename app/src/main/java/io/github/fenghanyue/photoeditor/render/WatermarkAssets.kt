package io.github.fenghanyue.photoeditor.render

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.Typeface
import com.caverock.androidsvg.SVG
import java.util.concurrent.ConcurrentHashMap

/**
 * 画水印用的字体和品牌 Logo，整个 App 共用一份。
 * 字体是内置的思源黑体子集，不受手机系统字体影响；子集里没有的生僻字由系统字体补上。
 */
class WatermarkAssets private constructor(private val assets: AssetManager) {

    val regular: Typeface = Typeface.createFromAsset(assets, "fonts/NotoSansSC-Regular.ttf")
    val bold: Typeface = Typeface.createFromAsset(assets, "fonts/NotoSansSC-Bold.ttf")

    private val logos = ConcurrentHashMap<String, Logo>()

    /** 品牌 Logo；没有对应的 SVG 时返回 null，调用方改为印品牌名文字。 */
    fun logo(brand: String?): Logo? {
        val file = LOGO_FILES[brand] ?: return null
        return logos.getOrPut(file) { Logo(SVG.getFromAsset(assets, file)) }
    }

    class Logo(private val svg: SVG) {
        /** 宽 / 高 */
        val aspectRatio: Float = svg.documentViewBox?.let { it.width() / it.height() } ?: 1f

        init {
            // SVG 文件里写死了 400×400 的尺寸，改成撑满给定区域，才会按区域大小缩放
            svg.setDocumentWidth("100%")
            svg.setDocumentHeight("100%")
        }

        fun draw(canvas: Canvas, bounds: RectF) {
            // 预览和导出可能同时在画同一个 Logo
            synchronized(svg) { svg.renderToCanvas(canvas, bounds) }
        }
    }

    companion object {
        /** 品牌显示名（见 DeviceNames.brand）→ assets 里的 SVG。 */
        private val LOGO_FILES = mapOf("Nikon" to "logos/nikon.svg")

        @Volatile
        private var instance: WatermarkAssets? = null

        fun get(context: Context): WatermarkAssets =
            instance ?: synchronized(this) {
                instance ?: WatermarkAssets(context.applicationContext.assets).also { instance = it }
            }
    }
}
