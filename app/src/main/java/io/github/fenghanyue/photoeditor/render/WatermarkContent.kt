package io.github.fenghanyue.photoeditor.render

import io.github.fenghanyue.photoeditor.geo.Region
import io.github.fenghanyue.photoeditor.meta.DeviceNames
import io.github.fenghanyue.photoeditor.meta.ParamFormatter
import io.github.fenghanyue.photoeditor.meta.PhotoMeta
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** 要印到照片上的各行文字；照片里没有、也没有手填的为 null。 */
data class WatermarkContent(
    /** 品牌显示名，用来找 Logo；没有 Logo 时直接印这几个字。 */
    val brand: String? = null,
    val deviceName: String? = null,
    val lens: String? = null,
    /** 例如 "52mm  f/5.6  1/60s  ISO 1400" */
    val params: String? = null,
    /** 例如 "2026.10.03 19:14" */
    val dateTime: String? = null,
    val time: String? = null,
    val date: String? = null,
    val weekday: String? = null,
    /** 地区和地点名称，例如"甘肃省敦煌市 · 鸣沙山月牙泉" */
    val location: String? = null,
    /** 例如 39°54'31"N 116°23'51"E */
    val coordinates: String? = null,
    /** 例如 "海拔 44m" */
    val altitude: String? = null,
    val signature: String? = null,
    val note: String? = null,
) {
    companion object {
        private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private const val PARAM_SEPARATOR = "  "
        private const val LOCATION_SEPARATOR = " · "

        /**
         * @param deviceName 机型名称：改过的就用改过的，否则是从照片里读出来整理好的。
         * @param region 要印的地区，按 [WatermarkOptions.regionStyle] 的写法印。
         */
        fun from(meta: PhotoMeta, deviceName: String?, options: WatermarkOptions, region: Region? = null): WatermarkContent {
            val focal = meta.focalLength35mm?.toDouble() ?: meta.focalLengthMm
            val params = listOfNotNull(
                focal?.let(ParamFormatter::focalLength),
                meta.fNumber?.let(ParamFormatter::aperture),
                meta.exposureTimeSec?.let(ParamFormatter::shutter),
                meta.iso?.let(ParamFormatter::iso),
            ).joinToString(PARAM_SEPARATOR).ifEmpty { null }
            val dateTime = meta.dateTime?.value
            val gps = meta.gps
            val location = listOfNotNull(region?.text(options.regionStyle), options.placeName.trim().ifEmpty { null })
                .joinToString(LOCATION_SEPARATOR)
                .ifEmpty { null }
            return WatermarkContent(
                brand = DeviceNames.brand(meta.make),
                deviceName = deviceName?.trim()?.ifEmpty { null },
                lens = meta.lensModel,
                params = params,
                dateTime = dateTime?.let { "${it.format(DATE)} ${it.format(TIME)}" },
                time = dateTime?.format(TIME),
                date = dateTime?.format(DATE),
                weekday = dateTime?.dayOfWeek?.getDisplayName(TextStyle.FULL, Locale.CHINA),
                location = location,
                coordinates = gps?.let { ParamFormatter.coordinatesDms(it.latitude, it.longitude) },
                altitude = gps?.altitudeMeters?.let { "海拔 ${ParamFormatter.altitude(it)}" },
                signature = options.signature.trim().ifEmpty { null },
                note = options.note.trim().ifEmpty { null },
            )
        }
    }
}
