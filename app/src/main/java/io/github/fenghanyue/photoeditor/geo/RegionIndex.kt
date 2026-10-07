package io.github.fenghanyue.photoeditor.geo

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 离线的全国省、市、区县数据（assets/regions.bin，格式见 tools/regions/build_regions.py）。
 *
 * 只有最底层的地区带边界。按坐标查地区时，先用外接矩形筛出候选，再判断点在不在边界里
 * （奇偶规则：边界里有洞时，洞里的点算在外面）。边界一直以压缩的形式放在内存里，查的时候边读边算。
 */
class RegionIndex private constructor(
    private val data: ByteArray,
    /** 全部地区，按代码排序，上级排在下级前面。 */
    val regions: List<Region>,
    private val shapes: List<Shape>,
) {
    private val byCode: Map<Int, Region> = regions.associateBy { it.code }
    private val childrenOf: Map<Region?, List<Region>> = regions.groupBy { it.parent }

    val provinces: List<Region> get() = children(null)

    /** 下一级地区；region 为 null 时返回省级列表。 */
    fun children(region: Region?): List<Region> = childrenOf[region].orEmpty()

    fun hasChildren(region: Region): Boolean = childrenOf.containsKey(region)

    fun find(code: Int): Region? = byCode[code]

    /**
     * 按名字搜索。可以用空格隔开几个词，比如"酒泉 敦煌"，每个词都要出现在这个地区或它的上级的名字里。
     * 名字以最后一个词开头的排在前面，其次是名字里包含它的，再其次是只有上级包含它的（"酒泉"会带出酒泉市下面的区县）。
     */
    fun search(query: String, limit: Int = SEARCH_LIMIT): List<Region> {
        val words = query.split(WHITESPACE).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val last = words.last()
        return regions
            .filter { region -> words.all { word -> region.path.any { word in it.name } } }
            .sortedWith(
                compareBy<Region>(
                    { region ->
                        when {
                            region.name.startsWith(last) -> 0
                            last in region.name -> 1
                            else -> 2
                        }
                    },
                    { it.level },
                    { it.code },
                ),
            )
            .take(limit)
    }

    /** 按照片里的 GPS 坐标（WGS-84）查最底层的地区，查不到返回 null。 */
    fun lookupWgs84(lat: Double, lon: Double): Region? =
        CoordTransform.wgs84ToGcj02(lat, lon).let { lookup(it.lat, it.lon) }

    /**
     * 按 GCJ-02 坐标查最底层的地区。不在任何地区里时（比如在海边，边界数据简化过，
     * 点落在海岸线外一点点），取 [NEAR_METERS] 以内最近的地区；再远就返回 null。
     */
    fun lookup(lat: Double, lon: Double): Region? {
        val x = lon * SCALE
        val y = lat * SCALE
        shapes.firstOrNull { it.covers(x, y, 0.0, 0.0) && contains(it, x, y) }?.let { return it.region }

        val cosLat = cos(Math.toRadians(lat))
        val marginY = NEAR_METERS / METERS_PER_DEGREE * SCALE
        val marginX = marginY / cosLat.coerceAtLeast(0.1)
        var nearest: Region? = null
        var nearestDistance = marginY
        for (shape in shapes) {
            if (!shape.covers(x, y, marginX, marginY)) continue
            val distance = distance(shape, x, y, cosLat)
            if (distance <= nearestDistance) {
                nearest = shape.region
                nearestDistance = distance
            }
        }
        return nearest
    }

    /** 奇偶规则：从点向右的射线和边界相交奇数次，点就在里面。 */
    private fun contains(shape: Shape, x: Double, y: Double): Boolean {
        var inside = false
        forEachEdge(shape) { x1, y1, x2, y2 ->
            if ((y1 > y) != (y2 > y) && x < x1 + (y - y1) * (x2 - x1) / (y2 - y1)) inside = !inside
        }
        return inside
    }

    /** 点到边界的最短距离，单位和纬度方向的坐标一样（1/100000 度）。 */
    private fun distance(shape: Shape, x: Double, y: Double, cosLat: Double): Double {
        var best = Double.MAX_VALUE
        forEachEdge(shape) { x1, y1, x2, y2 ->
            // 以点为原点，经度方向按纬度缩短，近似成平面
            val ax = (x1 - x) * cosLat
            val ay = y1 - y
            val dx = (x2 - x1) * cosLat
            val dy = (y2 - y1).toDouble()
            val lengthSquared = dx * dx + dy * dy
            val t = if (lengthSquared == 0.0) 0.0 else (-(ax * dx + ay * dy) / lengthSquared).coerceIn(0.0, 1.0)
            val px = ax + t * dx
            val py = ay + t * dy
            best = min(best, px * px + py * py)
        }
        return sqrt(best)
    }

    private inline fun forEachEdge(shape: Shape, action: (x1: Int, y1: Int, x2: Int, y2: Int) -> Unit) {
        val reader = VarintReader(data, shape.offset)
        var x = shape.minX
        var y = shape.minY
        repeat(reader.unsigned()) {
            val count = reader.unsigned()
            x += reader.signed()
            y += reader.signed()
            val firstX = x
            val firstY = y
            repeat(count - 1) {
                val lastX = x
                val lastY = y
                x += reader.signed()
                y += reader.signed()
                action(lastX, lastY, x, y)
            }
            action(x, y, firstX, firstY)
        }
    }

    /** 一个带边界的地区：外接矩形，以及边界数据在文件里的位置。坐标单位是 1/100000 度。 */
    private class Shape(
        val region: Region,
        val minX: Int,
        val minY: Int,
        val maxX: Int,
        val maxY: Int,
        val offset: Int,
    ) {
        fun covers(x: Double, y: Double, marginX: Double, marginY: Double): Boolean =
            x >= minX - marginX && x <= maxX + marginX && y >= minY - marginY && y <= maxY + marginY
    }

    private class VarintReader(private val data: ByteArray, private var position: Int) {
        fun unsigned(): Int {
            var result = 0
            var shift = 0
            while (true) {
                val byte = data[position++].toInt()
                result = result or ((byte and 0x7F) shl shift)
                if (byte and 0x80 == 0) return result
                shift += 7
            }
        }

        /** zigzag 编码的有符号数。 */
        fun signed(): Int {
            val value = unsigned()
            return (value ushr 1) xor -(value and 1)
        }
    }

    companion object {
        private const val FILE = "regions.bin"
        private const val SCALE = 100_000.0
        private const val METERS_PER_DEGREE = 111_320.0
        private const val SEARCH_LIMIT = 50
        private val MAGIC = "RGN1".toByteArray()
        private val WHITESPACE = Regex("[\\s\\u3000]+")

        /** 海边、湖边的照片，离最近的地区不超过这个距离时算到这个地区。 */
        const val NEAR_METERS = 2_000.0

        @Volatile
        private var loaded: RegionIndex? = null
        private val loadLock = Mutex()

        /** 第一次调用时在后台读入数据，之后直接返回同一份。 */
        suspend fun load(context: Context): RegionIndex =
            loaded ?: loadLock.withLock {
                loaded ?: withContext(Dispatchers.IO) {
                    parse(context.applicationContext.assets.open(FILE).use { it.readBytes() })
                }.also { loaded = it }
            }

        fun parse(data: ByteArray): RegionIndex {
            val buffer = ByteBuffer.wrap(data)
            val magic = ByteArray(MAGIC.size).also { buffer.get(it) }
            require(magic.contentEquals(MAGIC)) { "不是地区数据文件" }
            val count = buffer.short.toInt() and 0xFFFF
            val regions = ArrayList<Region>(count)
            val byCode = HashMap<Int, Region>(count * 2)
            val shapes = ArrayList<Shape>()
            repeat(count) {
                val code = buffer.int
                val parentCode = buffer.int
                val level = RegionLevel.entries[buffer.get() - 1]
                val nameLength = buffer.get().toInt() and 0xFF
                val name = String(data, buffer.position(), nameLength, Charsets.UTF_8)
                buffer.position(buffer.position() + nameLength)
                val shapeLength = buffer.int
                val parent = if (parentCode == 0) {
                    null
                } else {
                    requireNotNull(byCode[parentCode]) { "地区 $code 的上级 $parentCode 没有排在它前面" }
                }
                val region = Region(code, name, level, parent)
                regions += region
                byCode[code] = region
                if (shapeLength > 0) {
                    val minX = buffer.int
                    val minY = buffer.int
                    val maxX = buffer.int
                    val maxY = buffer.int
                    shapes += Shape(region, minX, minY, maxX, maxY, buffer.position())
                    buffer.position(buffer.position() + shapeLength)
                }
            }
            require(!buffer.hasRemaining()) { "地区数据文件末尾有多余的内容" }
            return RegionIndex(data, regions, shapes)
        }
    }
}
