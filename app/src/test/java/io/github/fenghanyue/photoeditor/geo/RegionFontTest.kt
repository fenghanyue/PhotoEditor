package io.github.fenghanyue.photoeditor.geo

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer

/** 地名用到的每个字，内置的两个字体里都要有，否则印出来的字会换成系统字体，粗细不一致。 */
class RegionFontTest {

    @Test
    fun fontsHaveEveryCharacterOfRegionNames() {
        val needed = TestRegions.index.regions.flatMap { region -> region.name.codePoints().toArray().asList() }.toSortedSet()
        for (name in listOf("NotoSansSC-Regular.ttf", "NotoSansSC-Bold.ttf")) {
            val available = codePointsIn(File(TestRegions.assets, "fonts/$name"))
            val missing = needed.filterNot { it in available }
            assertTrue("$name 缺少：" + missing.joinToString("") { String(Character.toChars(it)) }, missing.isEmpty())
        }
    }

    /** 读字体的 cmap 表，返回有字形的全部码位。只认格式 4 和 12，思源黑体用的就是这两种。 */
    private fun codePointsIn(file: File): Set<Int> {
        val font = ByteBuffer.wrap(file.readBytes())
        fun u16(at: Int) = font.getShort(at).toInt() and 0xFFFF
        val cmap = (0 until u16(4)).map { 12 + 16 * it }
            .single { record -> String(ByteArray(4) { font.get(record + it) }, Charsets.US_ASCII) == "cmap" }
            .let { font.getInt(it + 8) }
        val result = HashSet<Int>()
        for (i in 0 until u16(cmap + 2)) {
            val table = cmap + font.getInt(cmap + 4 + 8 * i + 4)
            when (u16(table)) {
                4 -> {
                    val segments = u16(table + 6) / 2
                    val ends = table + 14
                    val starts = ends + 2 * segments + 2
                    val deltas = starts + 2 * segments
                    val rangeOffsets = deltas + 2 * segments
                    for (s in 0 until segments) {
                        val start = u16(starts + 2 * s)
                        val delta = font.getShort(deltas + 2 * s).toInt()
                        val rangeOffset = u16(rangeOffsets + 2 * s)
                        for (c in start..u16(ends + 2 * s)) {
                            val glyph = if (rangeOffset == 0) {
                                (c + delta) and 0xFFFF
                            } else {
                                u16(rangeOffsets + 2 * s + rangeOffset + 2 * (c - start)).let { if (it == 0) 0 else (it + delta) and 0xFFFF }
                            }
                            if (glyph != 0 && c != 0xFFFF) result += c
                        }
                    }
                }
                12 -> for (g in 0 until font.getInt(table + 12)) {
                    val group = table + 16 + 12 * g
                    val start = font.getInt(group)
                    val startGlyph = font.getInt(group + 8)
                    for (c in start..font.getInt(group + 4)) if (startGlyph + c - start != 0) result += c
                }
            }
        }
        return result
    }
}
