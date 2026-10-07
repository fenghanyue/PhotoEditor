package io.github.fenghanyue.photoeditor.geo

import org.junit.Assert.assertEquals
import org.junit.Test

class CoordTransformTest {

    /** 参考值由另一个独立实现 eviltransform 0.1.1（Python）算出。 */
    @Test
    fun matchesReferenceImplementation() {
        val cases = listOf(
            // WGS-84 纬度, 经度 -> GCJ-02 纬度, 经度
            doubleArrayOf(39.9087, 116.3975, 39.910103523, 116.403743678), // 北京天安门
            doubleArrayOf(40.1421, 94.6620, 40.142727003, 94.663044107), // 敦煌
            doubleArrayOf(29.6525, 91.1721, 29.649710562, 91.173567407), // 拉萨
            doubleArrayOf(22.2796, 114.1588, 22.276880294, 114.163793137), // 香港
            doubleArrayOf(43.8256, 87.6168, 43.826805414, 87.619649998), // 乌鲁木齐
            doubleArrayOf(52.9727, 122.5383, 52.974213934, 122.545329906), // 漠河
            doubleArrayOf(18.2528, 109.5119, 18.251094763, 109.515984364), // 三亚
        )
        for ((lat, lon, expectedLat, expectedLon) in cases) {
            val gcj = CoordTransform.wgs84ToGcj02(lat, lon)
            assertEquals("$lat, $lon 的纬度", expectedLat, gcj.lat, 1e-6)
            assertEquals("$lat, $lon 的经度", expectedLon, gcj.lon, 1e-6)
        }
    }

    @Test
    fun pointsOutsideChinaAreUnchanged() {
        assertEquals(LatLon(35.6812, 139.7671), CoordTransform.wgs84ToGcj02(35.6812, 139.7671)) // 东京
        assertEquals(LatLon(-33.8568, 151.2153), CoordTransform.wgs84ToGcj02(-33.8568, 151.2153)) // 悉尼
    }
}
