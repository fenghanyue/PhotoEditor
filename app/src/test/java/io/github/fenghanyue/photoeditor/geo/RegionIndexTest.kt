package io.github.fenghanyue.photoeditor.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 用 App 里真实的地区数据测试。测试点都离区县边界 1 公里以上，结果不受数据精度影响。 */
class RegionIndexTest {

    private val index = TestRegions.index

    private fun lookup(lat: Double, lon: Double): List<String>? = index.lookupWgs84(lat, lon)?.path?.map { it.name }

    @Test
    fun findsRegionByPhotoGps() {
        assertEquals(listOf("甘肃省", "酒泉市", "敦煌市"), lookup(40.0886, 94.6705)) // 敦煌月牙泉
        assertEquals(listOf("西藏自治区", "拉萨市", "城关区"), lookup(29.6578, 91.1169)) // 布达拉宫
        assertEquals(listOf("北京市", "朝阳区"), lookup(39.9842, 116.4950)) // 798 艺术区
        assertEquals(listOf("广东省", "东莞市"), lookup(23.0207, 113.7518)) // 东莞市区
        assertEquals(listOf("湖北省", "仙桃市"), lookup(30.3627, 113.4549)) // 仙桃市区
        assertEquals(listOf("香港特别行政区", "中西区"), lookup(22.2796, 114.1588)) // 香港中环
        assertEquals(listOf("台湾省"), lookup(25.0340, 121.5645)) // 台北 101
    }

    @Test
    fun pointsAbroadOrFarAtSeaHaveNoRegion() {
        assertNull(lookup(35.6812, 139.7671)) // 东京
        assertNull(lookup(37.5665, 126.9780)) // 首尔：在换算坐标的大致范围里，但不在任何地区里
        assertNull(lookup(20.1500, 110.2000)) // 琼州海峡中间，离岸约 8 公里
    }

    @Test
    fun pointJustOffTheCoastGoesToNearestRegion() {
        // 北戴河海边的一个点，落在简化过的海岸线外约 20 米
        assertEquals(listOf("河北省", "秦皇岛市", "北戴河区"), lookup(39.8380, 119.5200))
    }

    @Test
    fun hasEveryProvince() {
        val provinces = index.provinces
        assertEquals(34, provinces.size)
        for (province in provinces) {
            // 只有台湾省没有下级数据
            assertEquals(province.name, province.name != "台湾省", index.hasChildren(province))
        }
        assertTrue("一共 ${index.regions.size} 个地区", index.regions.size in 3000..3500)
    }

    @Test
    fun levelsDifferByPlace() {
        val beijing = index.provinces.single { it.name == "北京市" }
        val chaoyang = index.children(beijing).single { it.name == "朝阳区" }
        assertEquals(RegionLevel.COUNTY, chaoyang.level)
        assertFalse(index.hasChildren(chaoyang))

        val dongguan = requireNotNull(index.find(441900))
        assertEquals("东莞市", dongguan.name)
        assertEquals(RegionLevel.CITY, dongguan.level)
        assertFalse(index.hasChildren(dongguan))
    }

    @Test
    fun searchByName() {
        val dunhuang = index.search("敦煌")
        assertEquals(listOf("甘肃省", "酒泉市", "敦煌市"), dunhuang.first().path.map { it.name })

        // 搜市名时市排在最前，后面带出它下面的区县
        val jiuquan = index.search("酒泉")
        assertEquals("酒泉市", jiuquan.first().name)
        assertTrue(jiuquan.any { it.name == "敦煌市" })

        // 好几个地方都叫朝阳；加上省名可以缩小范围
        assertTrue(index.search("朝阳").size > 3)
        assertEquals(listOf("北京市", "朝阳区"), index.search("北京 朝阳").single().path.map { it.name })

        assertTrue(index.search("  ").isEmpty())
        assertTrue(index.search("火星").isEmpty())
    }
}
