package io.github.fenghanyue.photoeditor.export

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import io.github.fenghanyue.photoeditor.meta.ExifMetaParser
import io.github.fenghanyue.photoeditor.render.TemplateKind
import io.github.fenghanyue.photoeditor.render.WatermarkContent
import io.github.fenghanyue.photoeditor.render.WatermarkOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 导出往返测试：用测试图（60×40，方向标记要求转 90°，带 GPS 和序列号）
 * 走一遍"解码 → 画水印 → JPEG → 复制拍摄信息"，再读回来检查。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoExporterTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun export(options: WatermarkOptions): Pair<PhotoExporter.Rendered, File> {
        val source = File(context.cacheDir, "camera_portrait.jpg")
        requireNotNull(javaClass.classLoader?.getResourceAsStream("exif/camera_portrait.jpg")).use { input ->
            source.outputStream().use { input.copyTo(it) }
        }
        val meta = ExifMetaParser.parse(ExifInterface(source.absolutePath), fileDateTakenMillis = null)
        val content = WatermarkContent.from(meta, "Nikon Z5II", options)
        val target = File(context.cacheDir, "exported.jpg")
        val rendered = PhotoExporter(context).renderToFile(Uri.fromFile(source), content, options, target)
        return rendered to target
    }

    @Test
    fun frameExportIsUprightAndKeepsShootingInfo() {
        val (rendered, file) = export(WatermarkOptions())

        // 转正后是 40×60，再加短边 9%（约 4 像素）高的边框条
        assertEquals(40, rendered.width)
        assertEquals(64, rendered.height)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        assertEquals(40, bounds.outWidth)
        assertEquals(64, bounds.outHeight)

        val exif = ExifInterface(file.absolutePath)
        assertEquals(ExifInterface.ORIENTATION_NORMAL, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1))
        assertEquals("40", exif.getAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION))
        assertEquals("64", exif.getAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION))
        assertEquals("2025:05:20 08:30:15", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        assertEquals("+08:00", exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL))
        assertEquals("NIKON CORPORATION", exif.getAttribute(ExifInterface.TAG_MAKE))
        assertEquals("NIKON Z5_2", exif.getAttribute(ExifInterface.TAG_MODEL))
        assertEquals("NIKKOR Z 24-200mm f/4-6.3 VR", exif.getAttribute(ExifInterface.TAG_LENS_MODEL))
        assertEquals(1.0 / 60, exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0), 1e-9)
        assertEquals(5.6, exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0), 1e-9)
        assertEquals(1400, exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0))
        assertEquals(-1.0 / 3, exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_BIAS_VALUE, 0.0), 1e-6)
        assertEquals(52, exif.getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0))
        assertTrue(exif.getAttribute(ExifInterface.TAG_SOFTWARE).orEmpty().startsWith("PhotoEditor "))

        val latLong = requireNotNull(exif.latLong) { "GPS 应该被保留" }
        assertEquals(39.9087, latLong[0], 1e-4)
        assertEquals(116.3975, latLong[1], 1e-4)

        assertNull(exif.getAttribute(ExifInterface.TAG_BODY_SERIAL_NUMBER))
        assertNull(exif.getAttribute(ExifInterface.TAG_LENS_SERIAL_NUMBER))
    }

    @Test
    fun gpsIsDroppedWhenSwitchedOff() {
        val (_, file) = export(WatermarkOptions(keepGps = false))
        val exif = ExifInterface(file.absolutePath)

        assertNull(exif.latLong)
        assertEquals("NIKON Z5_2", exif.getAttribute(ExifInterface.TAG_MODEL))
    }

    @Test
    fun overlayExportKeepsSize() {
        val (rendered, _) = export(WatermarkOptions(template = TemplateKind.OVERLAY))

        assertEquals(40, rendered.width)
        assertEquals(60, rendered.height)
    }

    @Test
    fun outputNameAddsSuffix() {
        assertEquals("DSC_7053_wm.jpg", PhotoExporter.outputName("DSC_7053.JPG"))
        assertEquals("IMG_1234_wm.jpg", PhotoExporter.outputName("IMG_1234.HEIC"))
        assertTrue(PhotoExporter.outputName(null).endsWith("_wm.jpg"))
    }
}
