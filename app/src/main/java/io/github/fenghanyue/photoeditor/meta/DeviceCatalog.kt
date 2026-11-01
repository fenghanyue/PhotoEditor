package io.github.fenghanyue.photoeditor.meta

/** 机型选择先分相机和手机，再按品牌列出型号。 */
enum class DeviceType { CAMERA, PHONE }

enum class DeviceBrand(val watermarkName: String) {
    NIKON("Nikon"),
    ONEPLUS("OnePlus"),
}

data class DeviceModel(val id: String, val type: DeviceType, val brand: DeviceBrand, val modelName: String) {
    val displayName: String get() = "${brand.watermarkName} $modelName"
}

/** 只收录已经确认的型号；新增型号时补充这里的目录即可。 */
object DeviceCatalog {
    val models: List<DeviceModel> = listOf(
        DeviceModel("nikon-z5-ii", DeviceType.CAMERA, DeviceBrand.NIKON, "Z5 II"),
        DeviceModel("oneplus-15t", DeviceType.PHONE, DeviceBrand.ONEPLUS, "15T"),
    )

    fun brands(type: DeviceType): List<DeviceBrand> =
        models.filter { it.type == type }.map { it.brand }.distinct()

    fun models(type: DeviceType, brand: DeviceBrand): List<DeviceModel> = models.filter { it.type == type && it.brand == brand }
}
