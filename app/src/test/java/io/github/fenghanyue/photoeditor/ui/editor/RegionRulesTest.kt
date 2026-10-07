package io.github.fenghanyue.photoeditor.ui.editor

import io.github.fenghanyue.photoeditor.geo.Region
import io.github.fenghanyue.photoeditor.geo.RegionLevel
import org.junit.Assert.assertEquals
import org.junit.Test

/** 新打开一张照片时地区怎么来。 */
class RegionRulesTest {

    private val dunhuang = Region(620982, "敦煌市", RegionLevel.COUNTY, Region(620000, "甘肃省", RegionLevel.PROVINCE, null))
    private val hangzhou = Region(330100, "杭州市", RegionLevel.CITY, Region(330000, "浙江省", RegionLevel.PROVINCE, null))

    @Test
    fun gpsInChinaIsLookedUp() {
        assertEquals(dunhuang to RegionSource.GPS, regionForNewPhoto(hasGps = true, gpsRegion = dunhuang, previous = hangzhou))
    }

    @Test
    fun gpsAbroadPrintsNoRegion() {
        // 照片在国外：不沿用上一张的国内地区，水印上只有坐标
        assertEquals(null to null, regionForNewPhoto(hasGps = true, gpsRegion = null, previous = hangzhou))
    }

    @Test
    fun noGpsCarriesOverPreviousRegion() {
        assertEquals(hangzhou to RegionSource.PREVIOUS, regionForNewPhoto(hasGps = false, gpsRegion = null, previous = hangzhou))
        assertEquals(null to null, regionForNewPhoto(hasGps = false, gpsRegion = null, previous = null))
    }
}
