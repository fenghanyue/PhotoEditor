package io.github.fenghanyue.photoeditor.meta

import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset

/** 测试图片由 tools/testdata/make_exif_fixtures.py 生成。 */
@RunWith(RobolectricTestRunner::class)
class ExifMetaParserTest {

    private fun resource(name: String) =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("exif/$name")) { "缺少测试图片 $name" }

    private fun exif(name: String): ExifInterface = resource(name).use { ExifInterface(it) }

    @Test
    fun readsCameraFields() {
        val meta = ExifMetaParser.parse(exif("camera_portrait.jpg"), fileDateTakenMillis = null, zone = ZoneOffset.UTC)

        assertEquals(Sourced(LocalDateTime.of(2025, 5, 20, 8, 30, 15), MetaSource.EXIF), meta.dateTime)
        assertEquals("+08:00", meta.offsetTime)
        assertEquals("NIKON CORPORATION", meta.make)
        assertEquals("NIKON Z5_2", meta.model)
        assertEquals("NIKKOR Z 24-200mm f/4-6.3 VR", meta.lensModel)
        assertEquals(1.0 / 60, meta.exposureTimeSec!!, 1e-9)
        assertEquals(5.6, meta.fNumber!!, 1e-9)
        assertEquals(1400, meta.iso)
        assertEquals(-1.0 / 3, meta.exposureBiasEv!!, 1e-6)
        assertEquals(52.0, meta.focalLengthMm!!, 1e-9)
        assertEquals(52, meta.focalLength35mm)
        assertEquals(false, meta.flashFired)
        assertEquals(90, meta.rotationDegrees)

        val gps = requireNotNull(meta.gps)
        assertEquals(39.9087, gps.latitude, 1e-4)
        assertEquals(116.3975, gps.longitude, 1e-4)
        assertEquals(44.0, gps.altitudeMeters!!, 1e-6)

        assertTrue(meta.rawTags.any { it == ExifInterface.TAG_LENS_MODEL to "NIKKOR Z 24-200mm f/4-6.3 VR" })
    }

    @Test
    fun noExifFallsBackToFileTime() {
        val fileTime = LocalDateTime.of(2025, 6, 1, 12, 0)
        val meta = ExifMetaParser.parse(
            exif("no_exif.jpg"),
            fileDateTakenMillis = fileTime.toInstant(ZoneOffset.UTC).toEpochMilli(),
            zone = ZoneOffset.UTC,
        )

        assertEquals(Sourced(fileTime, MetaSource.FILE), meta.dateTime)
        assertNull(meta.make)
        assertNull(meta.model)
        assertNull(meta.gps)
        assertEquals(0, meta.rotationDegrees)
        assertTrue(meta.rawTags.isEmpty())
    }

    @Test
    fun unreadableFileHasNoMeta() {
        assertEquals(PhotoMeta(), ExifMetaParser.parse(null, fileDateTakenMillis = null))
    }

    @Test
    fun zeroCoordinatesAreTreatedAsMissing() {
        // 系统抹掉位置时会留下 0,0 坐标
        val file = File.createTempFile("zero-gps", ".jpg")
        try {
            resource("no_exif.jpg").use { input -> file.outputStream().use { input.copyTo(it) } }
            ExifInterface(file.absolutePath).apply {
                setLatLong(0.0, 0.0)
                saveAttributes()
            }
            val reread = ExifInterface(file.absolutePath)
            assertTrue(reread.hasAttribute(ExifInterface.TAG_GPS_LATITUDE))
            assertNull(ExifMetaParser.parse(reread, fileDateTakenMillis = null).gps)
        } finally {
            file.delete()
        }
    }
}
