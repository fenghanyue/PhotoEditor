package io.github.fenghanyue.photoeditor.meta

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** 把拍摄参数格式化成水印上常见的写法：52mm、f/5.6、1/60s、ISO 1400、+0.3EV。 */
object ParamFormatter {

    fun focalLength(mm: Double): String = "${oneDecimal(mm)}mm"

    fun aperture(fNumber: Double): String = "f/${oneDecimal(fNumber)}"

    fun shutter(seconds: Double): String {
        if (seconds >= 1.0) return "${oneDecimal(seconds)}s"
        val denominator = 1.0 / seconds
        val shown = if (denominator >= 10) denominator.roundToInt().toString() else oneDecimal(denominator)
        return "1/${shown}s"
    }

    fun iso(iso: Int): String = "ISO $iso"

    fun exposureBias(ev: Double): String {
        val rounded = BigDecimal.valueOf(ev).setScale(1, RoundingMode.HALF_UP)
        val text = rounded.stripTrailingZeros().toPlainString()
        return when (rounded.signum()) {
            0 -> "0EV"
            1 -> "+${text}EV"
            else -> "${text}EV"
        }
    }

    /** 例如 39.9087°N, 116.3975°E */
    fun coordinatesDecimal(latitude: Double, longitude: Double): String =
        "${decimal(latitude, 'N', 'S')}, ${decimal(longitude, 'E', 'W')}"

    /** 例如 39°54'31"N 116°23'51"E */
    fun coordinatesDms(latitude: Double, longitude: Double): String =
        "${dms(latitude, 'N', 'S')} ${dms(longitude, 'E', 'W')}"

    fun altitude(meters: Double): String = "${meters.roundToInt()}m"

    private fun oneDecimal(value: Double): String =
        BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    private fun decimal(value: Double, positive: Char, negative: Char): String =
        String.format(Locale.ROOT, "%.4f°%c", abs(value), if (value >= 0) positive else negative)

    private fun dms(value: Double, positive: Char, negative: Char): String {
        val totalSeconds = (abs(value) * 3600).roundToLong()
        return String.format(
            Locale.ROOT,
            "%d°%02d'%02d\"%c",
            totalSeconds / 3600,
            totalSeconds % 3600 / 60,
            totalSeconds % 60,
            if (value >= 0) positive else negative,
        )
    }
}
