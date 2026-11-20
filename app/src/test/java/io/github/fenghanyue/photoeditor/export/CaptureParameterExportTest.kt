package io.github.fenghanyue.photoeditor.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import io.github.fenghanyue.photoeditor.meta.CaptureParameterEdits
import io.github.fenghanyue.photoeditor.meta.CaptureParameters
import io.github.fenghanyue.photoeditor.meta.ExifMetaParser
import io.github.fenghanyue.photoeditor.render.WatermarkContent
import io.github.fenghanyue.photoeditor.render.WatermarkOptions
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.ln

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CaptureParameterExportTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun camera(): File {
        val file = File(context.cacheDir, "parameters-camera.jpg")
        requireNotNull(javaClass.classLoader?.getResourceAsStream("exif/camera_portrait.jpg")).use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_APERTURE_VALUE, "5/1")
            setAttribute(ExifInterface.TAG_SHUTTER_SPEED_VALUE, "6/1")
            setAttribute(ExifInterface.TAG_SENSITIVITY_TYPE, "1")
            setAttribute(ExifInterface.TAG_STANDARD_OUTPUT_SENSITIVITY, "1400")
            saveAttributes()
        }
        return file
    }

    private fun screenshot(): File {
        val file = File(context.cacheDir, "parameters-screenshot.png")
        val bitmap = Bitmap.createBitmap(100, 160, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    private fun render(source: File, updated: CaptureParameters): Pair<ExifInterface, WatermarkContent> {
        val meta = ExifMetaParser.parse(ExifInterface(source), null)
        val edits = CaptureParameterEdits(CaptureParameters.from(meta), updated)
        val options = WatermarkOptions()
        val content = WatermarkContent.from(updated.applyTo(meta), null, options)
        val target = File(context.cacheDir, "parameters-export.jpg")
        PhotoExporter(context).renderToFile(Uri.fromFile(source), content, options, target, edits)
        return ExifInterface(target) to content
    }

    @Test
    fun modifiedCameraParametersMatchWatermarkAndReadbackAndSourceIsUntouched() {
        val source = camera()
        val originalBytes = source.readBytes()
        val wanted = CaptureParameters(2.0 / 3, 100, 200.0, 200, 8.0, 1.0 / 250)
        val (saved, watermark) = render(source, wanted)
        val read = ExifMetaParser.parse(saved, null)
        assertEquals("200mm  f/8  1/250s  ISO 100  +0.7EV", watermark.params)
        assertEquals(watermark.params, WatermarkContent.from(read, null, WatermarkOptions()).params)
        assertEquals(wanted.focalLengthMm!!, read.focalLengthMm!!, 1e-6)
        assertEquals(wanted.exposureBiasEv!!, read.exposureBiasEv!!, 1e-9)
        assertEquals(6.0, saved.getAttributeDouble(ExifInterface.TAG_APERTURE_VALUE, 0.0), 1e-6)
        assertEquals(ln(250.0) / ln(2.0), saved.getAttributeDouble(ExifInterface.TAG_SHUTTER_SPEED_VALUE, 0.0), 1e-6)
        assertNull(saved.getAttribute(ExifInterface.TAG_STANDARD_OUTPUT_SENSITIVITY))
        assertEquals(100, saved.getAttributeInt(ExifInterface.TAG_ISO_SPEED, 0))
        assertNotNull(read.gps)
        assertArrayEquals(originalBytes, source.readBytes())
    }

    @Test
    fun screenshotCanGainOnlyTheParametersTheUserFilled() {
        val source = screenshot()
        val originalBytes = source.readBytes()
        val (saved, content) = render(source, CaptureParameters(iso = 200, fNumber = 2.8))
        val read = CaptureParameters.from(ExifMetaParser.parse(saved, null))
        assertEquals(CaptureParameters(iso = 200, fNumber = 2.8), read)
        assertEquals("f/2.8  ISO 200", content.params)
        assertNull(saved.getAttribute(ExifInterface.TAG_MAKE))
        assertNull(saved.getAttribute(ExifInterface.TAG_EXPOSURE_TIME))
        assertArrayEquals(originalBytes, source.readBytes())
    }

    @Test
    fun screenshotWithoutEditsDoesNotGainInventedShootingValues() {
        val (saved, content) = render(screenshot(), CaptureParameters())
        assertEquals(CaptureParameters(), CaptureParameters.from(ExifMetaParser.parse(saved, null)))
        assertNull(content.params)
    }

    @Test
    fun clearingValuesAlsoRemovesRelatedApexAndSensitivityTags() {
        val (saved, content) = render(camera(), CaptureParameters())
        assertEquals(CaptureParameters(), CaptureParameters.from(ExifMetaParser.parse(saved, null)))
        assertNull(content.params)
        listOf(
            ExifInterface.TAG_APERTURE_VALUE, ExifInterface.TAG_SHUTTER_SPEED_VALUE,
            ExifInterface.TAG_SENSITIVITY_TYPE, ExifInterface.TAG_ISO_SPEED,
            ExifInterface.TAG_STANDARD_OUTPUT_SENSITIVITY,
        ).forEach { assertNull(it, saved.getAttribute(it)) }
    }

    @Test
    fun highIsoUsesExtendedTagAndChangingIsoKeepsUneditedRationals() {
        val source = camera()
        val original = ExifInterface(source)
        val parameters = CaptureParameters.from(ExifMetaParser.parse(original, null))
        val (saved, _) = render(source, parameters.copy(iso = 128000))
        assertEquals(128000, ExifMetaParser.parse(saved, null).iso)
        assertEquals(65535, saved.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0))
        assertEquals(original.getAttribute(ExifInterface.TAG_EXPOSURE_TIME), saved.getAttribute(ExifInterface.TAG_EXPOSURE_TIME))
        assertEquals(original.getAttribute(ExifInterface.TAG_APERTURE_VALUE), saved.getAttribute(ExifInterface.TAG_APERTURE_VALUE))
    }
}
