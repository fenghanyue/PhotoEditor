package io.github.fenghanyue.photoeditor.render

import io.github.fenghanyue.photoeditor.geo.RegionStyle

/** 水印模板。 */
enum class TemplateKind { FRAME, OVERLAY }

enum class FrameColor { WHITE, BLACK }

/** 参数边框左边第二行显示什么。 */
enum class FrameLeftDetail { TIME, LENS, SIGNATURE }

/** 参数边框右边第二行显示什么。 */
enum class FrameRightDetail { LOCATION, COORDINATES, LENS, NONE }

/** 信息叠加放在哪个角。 */
enum class Corner { BOTTOM_LEFT, BOTTOM_RIGHT, TOP_LEFT, TOP_RIGHT }

data class WatermarkOptions(
    val template: TemplateKind = TemplateKind.FRAME,
    val frameColor: FrameColor = FrameColor.WHITE,
    val showLogo: Boolean = true,
    val frameLeftDetail: FrameLeftDetail = FrameLeftDetail.TIME,
    val frameRightDetail: FrameRightDetail = FrameRightDetail.LOCATION,
    val corner: Corner = Corner.BOTTOM_LEFT,
    /** 信息叠加下面垫一块半透明的黑底，亮背景上也看得清。 */
    val backdrop: Boolean = true,
    val showCoordinates: Boolean = true,
    /** 地区的写法，例如"甘肃省敦煌市"还是"甘肃省酒泉市敦煌市"。 */
    val regionStyle: RegionStyle = RegionStyle.PROVINCE_COUNTY,
    /** 手填的地点名称，接在地区后面，例如"鸣沙山月牙泉"。 */
    val placeName: String = "",
    val signature: String = "",
    val note: String = "",
    /** 新图是否保留照片里的 GPS。 */
    val keepGps: Boolean = true,
)
