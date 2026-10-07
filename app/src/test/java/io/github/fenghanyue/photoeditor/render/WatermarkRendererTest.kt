package io.github.fenghanyue.photoeditor.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 用真实的绘图引擎画水印。效果图存在 app/build/watermark-samples/，方便肉眼检查。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WatermarkRendererTest {

    private val assets = WatermarkAssets.get(ApplicationProvider.getApplicationContext<Context>())

    private val content = WatermarkContent(
        brand = "Nikon",
        deviceName = "Nikon Z5II",
        lens = "NIKKOR Z 24-200mm f/4-6.3 VR",
        params = "52mm  f/5.6  1/60s  ISO 1400",
        dateTime = "2025.05.20 08:30",
        time = "08:30",
        date = "2025.05.20",
        weekday = "星期二",
        location = "杭州 · 西湖",
        coordinates = "30°14'46\"N 120°08'42\"E",
        altitude = "海拔 12m",
        note = "早上的断桥，人还不多",
    )

    /** 一张假的风景照：上面天空，下面沙地。 */
    private fun photo(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(0xFF7FB2E5.toInt(), 0xFFE9D7B5.toInt(), 0xFFC9A36B.toInt()),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        Canvas(bitmap).drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        return bitmap
    }

    private fun save(bitmap: Bitmap, name: String) {
        val dir = File("build/watermark-samples").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun frameAddsWhiteBarBelowPhoto() {
        val photo = photo(1200, 800)
        val out = WatermarkRenderer.render(photo, content, WatermarkOptions(), assets)

        assertEquals(1200, out.width)
        assertEquals(800 + 72, out.height)
        assertEquals(photo.getPixel(10, 10), out.getPixel(10, 10))
        assertEquals(Color.WHITE, out.getPixel(2, 870))
        save(out, "frame-white-landscape")
    }

    @Test
    fun frameBlackOnPortrait() {
        val out = WatermarkRenderer.render(
            photo(800, 1200),
            content,
            WatermarkOptions(frameColor = FrameColor.BLACK, frameRightDetail = FrameRightDetail.COORDINATES),
            assets,
        )

        assertEquals(800, out.width)
        assertEquals(1200 + 72, out.height)
        assertEquals(0xFF111111.toInt(), out.getPixel(2, 1270))
        save(out, "frame-black-portrait")
    }

    @Test
    fun frameWithoutKnownLogoPrintsBrandName() {
        val phone = content.copy(brand = "OPPO", deviceName = "OPPO Find X8", lens = null)
        val out = WatermarkRenderer.render(photo(1200, 800), phone, WatermarkOptions(), assets)
        save(out, "frame-brand-text")
    }

    @Test
    fun frameFitsVeryLongTexts() {
        val long = content.copy(
            deviceName = "一个非常非常长的机型名称用来测试会不会超出边框",
            location = "甘肃省酒泉市敦煌市 · 鸣沙山月牙泉景区南门停车场旁边的沙丘顶上",
        )
        val out = WatermarkRenderer.render(photo(400, 1000), long, WatermarkOptions(), assets)
        assertEquals(1000 + 36, out.height)
        save(out, "frame-long-texts")
    }

    @Test
    fun overlayDrawsOnPhotoInPlace() {
        val photo = photo(1200, 800)
        val before = photo.getPixel(60, 760)
        val out = WatermarkRenderer.render(photo, content, WatermarkOptions(template = TemplateKind.OVERLAY), assets)

        assertSame(photo, out)
        assertEquals(1200, out.width)
        assertEquals(800, out.height)
        // 左下角垫了半透明黑底，变暗了
        val after = out.getPixel(60, 760)
        assertNotEquals(before, after)
        assertTrue(Color.red(after) < Color.red(before))
        save(out, "overlay-bottom-left")
    }

    @Test
    fun overlayTopRightWithoutBackdrop() {
        val out = WatermarkRenderer.render(
            photo(800, 1200),
            content,
            WatermarkOptions(template = TemplateKind.OVERLAY, corner = Corner.TOP_RIGHT, backdrop = false),
            assets,
        )
        save(out, "overlay-top-right-no-backdrop")
    }

    @Test
    fun overlayWithNothingToShowLeavesPhotoUntouched() {
        val photo = photo(600, 400)
        val copy = photo.copy(Bitmap.Config.ARGB_8888, false)
        WatermarkRenderer.render(photo, WatermarkContent(), WatermarkOptions(template = TemplateKind.OVERLAY), assets)
        assertTrue(photo.sameAs(copy))
    }
}
