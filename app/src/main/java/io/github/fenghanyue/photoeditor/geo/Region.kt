package io.github.fenghanyue.photoeditor.geo

/** 行政区的层级。直辖市、香港、澳门的区直接挂在省级下面，中间没有地级。 */
enum class RegionLevel { PROVINCE, CITY, COUNTY }

/** 地区印在照片上的写法。 */
enum class RegionStyle {
    /** 省 + 最后一级，例如"甘肃省敦煌市"。 */
    PROVINCE_COUNTY,

    /** 每一级都写，例如"甘肃省酒泉市敦煌市"。 */
    FULL,

    /** 只写最后一级，例如"敦煌市"。 */
    COUNTY,
}

/**
 * 一个行政区，parent 为 null 的是省级。
 * 数据里直辖市下面直接是区，所以"北京市朝阳区"不会写成"北京市北京市朝阳区"。
 */
class Region(val code: Int, val name: String, val level: RegionLevel, val parent: Region?) {

    /** 从省级到自己。 */
    val path: List<Region> = parent?.path.orEmpty() + this

    fun text(style: RegionStyle): String = when (style) {
        RegionStyle.PROVINCE_COUNTY -> if (parent == null) name else path.first().name + name
        RegionStyle.FULL -> path.joinToString("") { it.name }
        RegionStyle.COUNTY -> name
    }

    override fun equals(other: Any?): Boolean = other is Region && other.code == code

    override fun hashCode(): Int = code

    override fun toString(): String = text(RegionStyle.FULL)
}
