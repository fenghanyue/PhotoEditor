package io.github.fenghanyue.photoeditor.geo

import java.io.File

/** 测试直接读 App 里真实的地区数据和字体。单元测试的工作目录是 app/。 */
object TestRegions {
    val assets = File("src/main/assets")

    val index: RegionIndex by lazy { RegionIndex.parse(File(assets, "regions.bin").readBytes()) }
}
