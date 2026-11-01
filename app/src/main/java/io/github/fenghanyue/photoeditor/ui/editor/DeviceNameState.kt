package io.github.fenghanyue.photoeditor.ui.editor

import io.github.fenghanyue.photoeditor.meta.DeviceModel

enum class DeviceNameMode { MANUAL, PICKER }

/** 两种方式各留一份内容，只有当前方式的名称和品牌用于水印。 */
data class DeviceNameState(
    val manualName: String = "",
    val selectedModel: DeviceModel? = null,
    val mode: DeviceNameMode = DeviceNameMode.MANUAL,
) {
    init {
        require(mode != DeviceNameMode.PICKER || selectedModel != null)
    }

    val displayName: String
        get() = if (mode == DeviceNameMode.PICKER) requireNotNull(selectedModel).displayName else manualName

    /** 手填名称时使用照片自己的品牌；选择机型时使用目录中的品牌。 */
    val brandOverride: String?
        get() = selectedModel?.brand?.watermarkName?.takeIf { mode == DeviceNameMode.PICKER }

    fun edit(name: String): DeviceNameState =
        if (mode == DeviceNameMode.MANUAL) copy(manualName = name) else this

    fun useMode(mode: DeviceNameMode): DeviceNameState =
        if (mode == DeviceNameMode.PICKER && selectedModel == null) this else copy(mode = mode)

    fun select(model: DeviceModel): DeviceNameState = copy(selectedModel = model, mode = DeviceNameMode.PICKER)
}
