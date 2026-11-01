package io.github.fenghanyue.photoeditor.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.fenghanyue.photoeditor.R

internal const val DEVICE_NAME_INPUT_TAG = "deviceNameInput"
internal const val DEVICE_NAME_SELECTION_TAG = "deviceNameSelection"
internal const val DEVICE_NAME_EDITOR_TAG = "deviceNameEditor"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceNameEditor(
    state: DeviceNameState,
    enabled: Boolean,
    onNameChange: (String) -> Unit,
    onModeChange: (DeviceNameMode) -> Unit,
    onPickModel: () -> Unit,
) {
    val title = stringResource(R.string.field_device_name)
    Column(Modifier.testTag(DEVICE_NAME_EDITOR_TAG), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            DeviceNameMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = state.mode == mode,
                    onClick = { onModeChange(mode) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index, DeviceNameMode.entries.size),
                    modifier = Modifier.testTag("deviceNameMode-${mode.name}"),
                ) {
                    Text(
                        stringResource(if (mode == DeviceNameMode.MANUAL) R.string.device_mode_manual else R.string.device_mode_picker),
                    )
                }
            }
        }
        if (state.mode == DeviceNameMode.MANUAL) {
            OutlinedTextField(
                value = state.manualName,
                onValueChange = onNameChange,
                enabled = enabled,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(DEVICE_NAME_INPUT_TAG)
                    .semantics { contentDescription = title },
            )
        } else {
            val model = requireNotNull(state.selectedModel)
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(DEVICE_NAME_SELECTION_TAG),
            ) {
                Row(
                    Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(model.displayName, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.device_path, deviceTypeName(model.type), deviceBrandName(model.brand)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onPickModel, enabled = enabled) { Text(stringResource(R.string.device_change)) }
                }
            }
        }
    }
}
