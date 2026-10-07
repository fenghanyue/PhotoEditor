package io.github.fenghanyue.photoeditor.render

import io.github.fenghanyue.photoeditor.geo.Region
import io.github.fenghanyue.photoeditor.geo.RegionLevel
import io.github.fenghanyue.photoeditor.geo.RegionStyle
import io.github.fenghanyue.photoeditor.meta.GeoPoint
import io.github.fenghanyue.photoeditor.meta.MetaSource
import io.github.fenghanyue.photoeditor.meta.PhotoMeta
import io.github.fenghanyue.photoeditor.meta.Sourced
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class WatermarkContentTest {

    private val meta = PhotoMeta(
        dateTime = Sourced(LocalDateTime.of(2025, 5, 20, 8, 30, 15), MetaSource.EXIF),
        make = "NIKON CORPORATION",
        model = "NIKON Z5_2",
        lensModel = "NIKKOR Z 24-200mm f/4-6.3 VR",
        focalLengthMm = 52.0,
        focalLength35mm = 52,
        fNumber = 5.6,
        exposureTimeSec = 1.0 / 60,
        iso = 1400,
        gps = GeoPoint(39.9087, 116.3975, 44.0),
    )

    @Test
    fun buildsAllLines() {
        val dongcheng = Region(110101, "东城区", RegionLevel.COUNTY, Region(110000, "北京市", RegionLevel.PROVINCE, null))
        val content = WatermarkContent.from(meta, "Nikon Z5II", WatermarkOptions(placeName = " 天安门 "), dongcheng)

        assertEquals("Nikon", content.brand)
        assertEquals("Nikon Z5II", content.deviceName)
        assertEquals("NIKKOR Z 24-200mm f/4-6.3 VR", content.lens)
        assertEquals("52mm  f/5.6  1/60s  ISO 1400", content.params)
        assertEquals("2025.05.20 08:30", content.dateTime)
        assertEquals("08:30", content.time)
        assertEquals("2025.05.20", content.date)
        assertEquals("星期二", content.weekday)
        assertEquals("北京市东城区 · 天安门", content.location)
        assertEquals("39°54'31\"N 116°23'51\"E", content.coordinates)
        assertEquals("海拔 44m", content.altitude)
    }

    @Test
    fun locationIsRegionInChosenStyleThenPlaceName() {
        val gansu = Region(620000, "甘肃省", RegionLevel.PROVINCE, null)
        val dunhuang = Region(620982, "敦煌市", RegionLevel.COUNTY, Region(620900, "酒泉市", RegionLevel.CITY, gansu))
        fun location(style: RegionStyle, placeName: String, region: Region? = dunhuang) =
            WatermarkContent.from(meta, null, WatermarkOptions(regionStyle = style, placeName = placeName), region).location

        assertEquals("甘肃省敦煌市 · 鸣沙山月牙泉", location(RegionStyle.PROVINCE_COUNTY, "鸣沙山月牙泉"))
        assertEquals("甘肃省酒泉市敦煌市", location(RegionStyle.FULL, " "))
        assertEquals("敦煌市", location(RegionStyle.COUNTY, ""))
        // 没有地区时只印地点名称
        assertEquals("鸣沙山月牙泉", location(RegionStyle.PROVINCE_COUNTY, "鸣沙山月牙泉", region = null))
    }

    @Test
    fun phoneWithoutEquivalentFocalUsesActualFocal() {
        val phone = meta.copy(focalLength35mm = null, focalLengthMm = 5.56)
        assertEquals("5.6mm  f/5.6  1/60s  ISO 1400", WatermarkContent.from(phone, null, WatermarkOptions()).params)
    }

    @Test
    fun missingFieldsAreSkipped() {
        val content = WatermarkContent.from(
            PhotoMeta(fNumber = 2.0),
            deviceName = "  ",
            options = WatermarkOptions(signature = " ", note = ""),
        )

        assertEquals("f/2", content.params)
        assertNull(content.deviceName)
        assertNull(content.brand)
        assertNull(content.dateTime)
        assertNull(content.weekday)
        assertNull(content.location)
        assertNull(content.coordinates)
        assertNull(content.altitude)
        assertNull(content.signature)
        assertNull(content.note)
    }

    @Test
    fun noExposureInfoMeansNoParamsLine() {
        assertNull(WatermarkContent.from(PhotoMeta(), null, WatermarkOptions()).params)
    }
}
