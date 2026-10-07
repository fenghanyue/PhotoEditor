package io.github.fenghanyue.photoeditor.geo

import org.junit.Assert.assertEquals
import org.junit.Test

class RegionTest {

    private val gansu = Region(620000, "甘肃省", RegionLevel.PROVINCE, null)
    private val jiuquan = Region(620900, "酒泉市", RegionLevel.CITY, gansu)
    private val dunhuang = Region(620982, "敦煌市", RegionLevel.COUNTY, jiuquan)
    private val beijing = Region(110000, "北京市", RegionLevel.PROVINCE, null)
    private val chaoyang = Region(110105, "朝阳区", RegionLevel.COUNTY, beijing)
    private val guangdong = Region(440000, "广东省", RegionLevel.PROVINCE, null)
    private val dongguan = Region(441900, "东莞市", RegionLevel.CITY, guangdong)
    private val taiwan = Region(710000, "台湾省", RegionLevel.PROVINCE, null)

    @Test
    fun countyWithCity() {
        assertEquals("甘肃省敦煌市", dunhuang.text(RegionStyle.PROVINCE_COUNTY))
        assertEquals("甘肃省酒泉市敦煌市", dunhuang.text(RegionStyle.FULL))
        assertEquals("敦煌市", dunhuang.text(RegionStyle.COUNTY))
    }

    @Test
    fun municipalityIsNotRepeated() {
        assertEquals("北京市朝阳区", chaoyang.text(RegionStyle.PROVINCE_COUNTY))
        assertEquals("北京市朝阳区", chaoyang.text(RegionStyle.FULL))
        assertEquals("朝阳区", chaoyang.text(RegionStyle.COUNTY))
    }

    @Test
    fun cityWithoutCounties() {
        assertEquals("广东省东莞市", dongguan.text(RegionStyle.PROVINCE_COUNTY))
        assertEquals("广东省东莞市", dongguan.text(RegionStyle.FULL))
        assertEquals("东莞市", dongguan.text(RegionStyle.COUNTY))
    }

    @Test
    fun choosingOnlyACityOrProvince() {
        assertEquals("甘肃省酒泉市", jiuquan.text(RegionStyle.PROVINCE_COUNTY))
        assertEquals("酒泉市", jiuquan.text(RegionStyle.COUNTY))
        for (style in RegionStyle.entries) assertEquals("台湾省", taiwan.text(style))
    }

    @Test
    fun pathAndEquality() {
        assertEquals(listOf(gansu, jiuquan, dunhuang), dunhuang.path)
        assertEquals(dunhuang, Region(620982, "敦煌市", RegionLevel.COUNTY, null))
    }
}
