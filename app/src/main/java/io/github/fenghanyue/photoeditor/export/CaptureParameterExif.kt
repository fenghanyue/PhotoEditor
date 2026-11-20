package io.github.fenghanyue.photoeditor.export

import androidx.exifinterface.media.ExifInterface
import io.github.fenghanyue.photoeditor.meta.CaptureParameterEdits
import io.github.fenghanyue.photoeditor.meta.CaptureParameters
import io.github.fenghanyue.photoeditor.meta.ExifMetaParser
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToLong

/** 在复制完原标签后应用修改，避免原值把手填值重新覆盖。 */
internal object CaptureParameterExif {
    fun apply(target: ExifInterface, edits: CaptureParameterEdits) {
        val old = edits.original
        val value = edits.updated
        require(value.isValid()) { "拍摄参数无效" }
        if (old.focalLengthMm != value.focalLengthMm) {
            target.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, value.focalLengthMm?.let(::rational))
        }
        if (old.focalLength35mm != value.focalLength35mm) {
            target.setAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, value.focalLength35mm?.toString())
        }
        if (old.fNumber != value.fNumber) {
            target.setAttribute(ExifInterface.TAG_F_NUMBER, value.fNumber?.let(::rational))
            // ApertureValue 是 APEX 换算值，不能继续留下旧光圈的记录。
            val apex = value.fNumber?.let { 2 * ln(it) / ln(2.0) }
            target.setAttribute(ExifInterface.TAG_APERTURE_VALUE, apex?.takeIf { it >= 0 }?.let(::rational))
        }
        if (old.exposureTimeSec != value.exposureTimeSec) {
            target.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, value.exposureTimeSec?.let(::rational))
            target.setAttribute(
                ExifInterface.TAG_SHUTTER_SPEED_VALUE,
                value.exposureTimeSec?.let { rational(-ln(it) / ln(2.0), signed = true) },
            )
        }
        if (old.exposureBiasEv != value.exposureBiasEv) {
            target.setAttribute(ExifInterface.TAG_EXPOSURE_BIAS_VALUE, value.exposureBiasEv?.let { ev ->
                val thirds = (ev * 3).roundToLong()
                if (abs(ev - thirds / 3.0) < 1e-9) "$thirds/3" else rational(ev, signed = true)
            })
        }
        if (old.iso != value.iso) {
            listOf(
                ExifInterface.TAG_STANDARD_OUTPUT_SENSITIVITY, ExifInterface.TAG_RECOMMENDED_EXPOSURE_INDEX,
                ExifInterface.TAG_ISO_SPEED, ExifInterface.TAG_ISO_SPEED_LATITUDE_YYY,
                ExifInterface.TAG_ISO_SPEED_LATITUDE_ZZZ, ExifInterface.TAG_SENSITIVITY_TYPE,
            ).forEach { target.setAttribute(it, null) }
            target.setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, value.iso?.coerceAtMost(65535)?.toString())
            if (value.iso != null) {
                target.setAttribute(ExifInterface.TAG_SENSITIVITY_TYPE, ExifInterface.SENSITIVITY_TYPE_ISO_SPEED.toString())
                target.setAttribute(ExifInterface.TAG_ISO_SPEED, value.iso.toString())
            }
        }
    }

    /** 新图重新打开后检查实际写入结果；失败时不把有矛盾参数的文件放进相册。 */
    fun verify(target: ExifInterface, edits: CaptureParameterEdits) {
        val actual = CaptureParameters.from(ExifMetaParser.parse(target, null))
        val old = edits.original
        val expected = edits.updated
        fun changedMatches(before: Double?, after: Double?, read: Double?): Boolean =
            before == after || if (after == null) read == null else
                read != null && abs(after - read) <= max(1e-8, abs(after) * 1e-6)
        val matches = changedMatches(old.focalLengthMm, expected.focalLengthMm, actual.focalLengthMm) &&
            changedMatches(old.fNumber, expected.fNumber, actual.fNumber) &&
            changedMatches(old.exposureTimeSec, expected.exposureTimeSec, actual.exposureTimeSec) &&
            changedMatches(old.exposureBiasEv, expected.exposureBiasEv, actual.exposureBiasEv) &&
            (old.iso == expected.iso || expected.iso == actual.iso) &&
            (old.focalLength35mm == expected.focalLength35mm || expected.focalLength35mm == actual.focalLength35mm)
        if (!matches) throw IOException("拍摄参数保存后核对不一致，请调整数值后重试")
    }

    private fun rational(value: Double, signed: Boolean = false): String {
        require(value.isFinite() && (signed || value >= 0)) { "拍摄参数数值无效" }
        val limit = if (signed) Int.MAX_VALUE.toLong() else 0xffffffffL
        require(abs(value) <= limit) { "拍摄参数超出可保存的范围" }
        val denominator = minOf(1_000_000_000L, (limit / max(1.0, abs(value))).toLong()).coerceAtLeast(1)
        val numerator = BigDecimal.valueOf(value).multiply(BigDecimal.valueOf(denominator))
            .setScale(0, RoundingMode.HALF_UP).longValueExact()
        require(value == 0.0 || numerator != 0L) { "拍摄参数太小，无法准确保存" }
        return "$numerator/$denominator"
    }
}
