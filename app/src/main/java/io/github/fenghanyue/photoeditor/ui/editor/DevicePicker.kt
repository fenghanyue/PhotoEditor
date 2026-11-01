package io.github.fenghanyue.photoeditor.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.fenghanyue.photoeditor.R
import io.github.fenghanyue.photoeditor.meta.DeviceBrand
import io.github.fenghanyue.photoeditor.meta.DeviceCatalog
import io.github.fenghanyue.photoeditor.meta.DeviceModel
import io.github.fenghanyue.photoeditor.meta.DeviceType

internal const val DEVICE_PICKER_TAG = "devicePicker"
internal const val DEVICE_BRAND_FIELD_TAG = "deviceBrandField"
internal const val DEVICE_MODEL_FIELD_TAG = "deviceModelField"
internal const val DEVICE_CONFIRM_TAG = "deviceConfirm"

/** 品牌和型号只修改弹层里的临时结果，点确定才应用到照片。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicePickerSheet(current: DeviceModel?, onSelect: (DeviceModel) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        DevicePickerContent(
            current = current,
            onSelect = { model ->
                onSelect(model)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicePickerContent(
    current: DeviceModel?,
    onSelect: (DeviceModel) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var type by rememberSaveable { mutableStateOf(current?.type ?: DeviceType.CAMERA) }
    var browsingBrand by rememberSaveable { mutableStateOf(current?.brand) }
    var pendingModelId by rememberSaveable { mutableStateOf(current?.id) }
    val brands = DeviceCatalog.brands(type)
    val models = browsingBrand?.let { DeviceCatalog.models(type, it) }.orEmpty()
    val pendingModel = models.firstOrNull { it.id == pendingModelId }

    Column(
        modifier
            .fillMaxWidth()
            .testTag(DEVICE_PICKER_TAG)
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.device_picker_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.close))
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.device_type), style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                DeviceType.entries.forEachIndexed { index, value ->
                    SegmentedButton(
                        selected = type == value,
                        onClick = {
                            if (type != value) {
                                type = value
                                browsingBrand = null
                                pendingModelId = null
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, DeviceType.entries.size),
                        modifier = Modifier.testTag("deviceType-${value.name}"),
                    ) { Text(deviceTypeName(value)) }
                }
            }
            // 切换上一级时重建下拉框，避免还展开着不匹配的菜单。
            key(type) {
                DeviceDropdown(
                    title = stringResource(R.string.device_brand),
                    value = browsingBrand?.let { brandLabel(it) }.orEmpty(),
                    placeholder = stringResource(R.string.device_choose_brand),
                    options = brands,
                    optionLabel = { brandLabel(it) },
                    optionTag = { "deviceBrand-${it.name}" },
                    onSelect = { brand ->
                        if (browsingBrand != brand) {
                            browsingBrand = brand
                            pendingModelId = null
                        }
                    },
                    fieldTag = DEVICE_BRAND_FIELD_TAG,
                )
            }
            key(type, browsingBrand) {
                DeviceDropdown(
                    title = stringResource(R.string.device_model),
                    value = pendingModel?.modelName.orEmpty(),
                    placeholder = stringResource(
                        if (browsingBrand == null) R.string.device_choose_brand_first else R.string.device_choose_model,
                    ),
                    options = models,
                    optionLabel = { it.modelName },
                    optionTag = { "deviceModel-${it.id}" },
                    onSelect = { pendingModelId = it.id },
                    fieldTag = DEVICE_MODEL_FIELD_TAG,
                    enabled = browsingBrand != null,
                )
            }
        }
        Button(
            onClick = { pendingModel?.let(onSelect) },
            enabled = pendingModel != null,
            modifier = Modifier.fillMaxWidth().testTag(DEVICE_CONFIRM_TAG),
        ) {
            Text(stringResource(R.string.device_confirm))
        }
    }
}

/** 即使只有一个选项也能展开；品牌和型号菜单都只显示文字，不放勾选图标。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DeviceDropdown(
    title: String,
    value: String,
    placeholder: String,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    optionTag: (T) -> String,
    onSelect: (T) -> Unit,
    fieldTag: String,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = enabled && it }) {
            OutlinedTextField(
                value = value,
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                singleLine = true,
                placeholder = { Text(placeholder) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = enabled)
                    .fillMaxWidth()
                    .testTag(fieldTag)
                    .semantics { contentDescription = title },
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.testTag("$fieldTag-menu"),
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                        modifier = Modifier.testTag(optionTag(option)),
                    )
                }
            }
        }
    }
}

@Composable
private fun brandLabel(brand: DeviceBrand): String =
    stringResource(R.string.device_brand_label, deviceBrandName(brand), brand.watermarkName)

@Composable
internal fun deviceTypeName(type: DeviceType): String = stringResource(
    when (type) {
        DeviceType.CAMERA -> R.string.device_type_camera
        DeviceType.PHONE -> R.string.device_type_phone
    },
)

@Composable
internal fun deviceBrandName(brand: DeviceBrand): String = stringResource(
    when (brand) {
        DeviceBrand.NIKON -> R.string.device_brand_nikon
        DeviceBrand.ONEPLUS -> R.string.device_brand_oneplus
    },
)
