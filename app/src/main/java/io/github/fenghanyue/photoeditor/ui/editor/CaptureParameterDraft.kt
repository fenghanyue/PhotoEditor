package io.github.fenghanyue.photoeditor.ui.editor

import io.github.fenghanyue.photoeditor.meta.CaptureParameters
import io.github.fenghanyue.photoeditor.meta.ParamFormatter
import java.math.BigDecimal

internal enum class CaptureField { EV, ISO, FOCAL, EQUIVALENT, APERTURE, SHUTTER }

/** 弹层草稿和已应用参数分开；没改过的快门不经过显示用的四舍五入。 */
internal data class CaptureParameterDraft(
    val ev: String = "",
    val iso: String = "",
    val focal: String = "",
    val equivalent: String = "",
    val aperture: String = "",
    val shutter: String = "",
) {
    fun validate(base: CaptureParameters): CaptureDraftResult {
        val errors = mutableSetOf<CaptureField>()
        fun decimal(text: String, field: CaptureField, positive: Boolean = true): Double? {
            if (text.isBlank()) return null
            val number = text.toDoubleOrNull()
            if (number == null || !number.isFinite() || (positive && number <= 0) || kotlin.math.abs(number) > 0xffffffffL) {
                errors += field
                return null
            }
            return number
        }
        fun integer(text: String, field: CaptureField, max: Int = Int.MAX_VALUE): Int? {
            if (text.isBlank()) return null
            val number = text.toIntOrNull()
            if (number == null || number !in 1..max) {
                errors += field
                return null
            }
            return number
        }
        fun clean(text: String) = text.trim().replace(" ", "")
        val exposure = clean(shutter).removeSuffix("s").removeSuffix("秒")
        val seconds = when {
            shutter == from(base).shutter -> base.exposureTimeSec
            exposure.isBlank() -> null
            '/' in exposure -> {
                val parts = exposure.split('/')
                val numerator = parts.getOrNull(0)?.toDoubleOrNull()
                val denominator = parts.getOrNull(1)?.toDoubleOrNull()
                if (parts.size != 2 || numerator == null || denominator == null ||
                    !numerator.isFinite() || !denominator.isFinite() || numerator <= 0 || denominator <= 0
                ) {
                    errors += CaptureField.SHUTTER
                    null
                } else decimal((numerator / denominator).toString(), CaptureField.SHUTTER)
            }
            else -> decimal(exposure, CaptureField.SHUTTER)
        }
        val values = CaptureParameters(
            exposureBiasEv = decimal(clean(ev), CaptureField.EV, positive = false),
            iso = integer(clean(iso).removePrefix("ISO").removePrefix("iso"), CaptureField.ISO),
            focalLengthMm = decimal(clean(focal).lowercase().removeSuffix("mm"), CaptureField.FOCAL),
            focalLength35mm = integer(clean(equivalent).lowercase().removeSuffix("mm"), CaptureField.EQUIVALENT, 65535),
            fNumber = decimal(clean(aperture), CaptureField.APERTURE),
            exposureTimeSec = seconds,
        )
        return CaptureDraftResult(values.takeIf { errors.isEmpty() }, errors)
    }

    fun asList(): List<String> = listOf(ev, iso, focal, equivalent, aperture, shutter)

    companion object {
        fun from(value: CaptureParameters) = CaptureParameterDraft(
            value.exposureBiasEv?.toString().orEmpty(), value.iso?.toString().orEmpty(),
            value.focalLengthMm?.let { BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() }.orEmpty(), value.focalLength35mm?.toString().orEmpty(),
            value.fNumber?.toString().orEmpty(), value.exposureTimeSec?.let { ParamFormatter.shutter(it).removeSuffix("s") }.orEmpty(),
        )

        fun fromList(values: List<String>) = CaptureParameterDraft(values[0], values[1], values[2], values[3], values[4], values[5])
    }
}

internal data class CaptureDraftResult(val values: CaptureParameters?, val errors: Set<CaptureField>)
