package io.github.fenghanyue.photoeditor.meta

import kotlin.math.abs

/** 这张图片的拍摄参数。null 表示未记录，0EV 则是明确记录了零补偿。 */
data class CaptureParameters(
    val exposureBiasEv: Double? = null,
    val iso: Int? = null,
    val focalLengthMm: Double? = null,
    val focalLength35mm: Int? = null,
    val fNumber: Double? = null,
    val exposureTimeSec: Double? = null,
) {
    fun applyTo(meta: PhotoMeta): PhotoMeta = meta.copy(
        exposureBiasEv = exposureBiasEv,
        iso = iso,
        focalLengthMm = focalLengthMm,
        focalLength35mm = focalLength35mm,
        fNumber = fNumber,
        exposureTimeSec = exposureTimeSec,
    )

    fun isValid(): Boolean =
        listOf(focalLengthMm, fNumber, exposureTimeSec).all { it == null || it.isFinite() && it > 0 } &&
            (exposureBiasEv == null || exposureBiasEv.isFinite()) &&
            (iso == null || iso > 0) &&
            (focalLength35mm == null || focalLength35mm in 1..65535)

    companion object {
        fun from(meta: PhotoMeta) = CaptureParameters(
            meta.exposureBiasEv, meta.iso, meta.focalLengthMm, meta.focalLength35mm,
            meta.fNumber, meta.exposureTimeSec,
        )
    }
}

/** 保存时只覆盖确实改过的项目，其余标签保持原值和精度。 */
data class CaptureParameterEdits(val original: CaptureParameters, val updated: CaptureParameters) {
    val hasChanges: Boolean get() = original != updated
}

object CaptureParameterPresets {
    // 三分之一档在界面显示成 0.3、0.7，内部保留分数对应的数值。
    fun exposureBias(original: Double?): List<Double> {
        val values = (-15..15).map { step ->
            val value = step / 3.0
            original?.takeIf { abs(it - value) < 0.000001 } ?: value
        }
        return (values + listOfNotNull(original)).distinct().sorted()
    }

    private val apertureStops = listOf(
        0.7, 0.8, 0.9, 1.0, 1.1, 1.2, 1.3, 1.4, 1.6, 1.8, 2.0, 2.2, 2.5,
        2.8, 3.2, 3.5, 4.0, 4.5, 5.0, 5.6, 6.3, 7.1, 8.0, 9.0, 10.0, 11.0,
        13.0, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0, 29.0, 32.0, 36.0, 40.0,
        45.0, 51.0, 57.0, 64.0,
    )

    fun apertures(meta: PhotoMeta, focalLength: Double?, current: Double?): List<Double> {
        val knownLens = meta.lensModel.orEmpty().replace("–", "-")
            .contains("NIKKOR Z 24-200mm f/4-6.3", ignoreCase = true)
        // 只使用尼康明确公布的范围；中间焦段不猜测完整的变焦光圈曲线。
        val range = when {
            !knownLens -> 0.0..Double.MAX_VALUE
            focalLength != null && abs(focalLength - 24.0) < 0.01 -> 4.0..22.0
            focalLength != null && abs(focalLength - 200.0) < 0.01 -> 6.3..36.0
            else -> 4.0..36.0
        }
        // 手机的 f/1.7 等值、以及原图已有的特殊值，不能被预设列表悄悄改掉。
        return (apertureStops.filter { it in range } + listOfNotNull(meta.fNumber, current)).distinct().sorted()
    }
}
