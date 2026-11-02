package io.github.fenghanyue.photoeditor.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.fenghanyue.photoeditor.R
import io.github.fenghanyue.photoeditor.meta.CaptureParameterPresets
import io.github.fenghanyue.photoeditor.meta.CaptureParameters
import io.github.fenghanyue.photoeditor.meta.ParamFormatter
import io.github.fenghanyue.photoeditor.meta.PhotoMeta

private val draftSaver = listSaver<CaptureParameterDraft, String>(
    save = { it.asList() }, restore = { CaptureParameterDraft.fromList(it) },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureParameterSheet(
    meta: PhotoMeta,
    current: CaptureParameters,
    onApply: (CaptureParameters) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        CaptureParameterEditorContent(meta, current, { onApply(it); onDismiss() }, onDismiss)
    }
}

@Composable
fun CaptureParameterEditorContent(
    meta: PhotoMeta,
    current: CaptureParameters,
    onApply: (CaptureParameters) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable(current, stateSaver = draftSaver) { mutableStateOf(CaptureParameterDraft.from(current)) }
    var restored by rememberSaveable(current) { mutableStateOf(false) }
    val original = CaptureParameters.from(meta)
    val result = draft.validate(if (restored) original else current)
    val missing = stringResource(R.string.capture_missing)
    Column(
        modifier.fillMaxWidth().imePadding().testTag("capture-editor")
            .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.capture_edit), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.close))
            }
        }
        Column(
            Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.capture_description), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ParameterDropdown(
                    title = stringResource(R.string.capture_ev), value = draft.ev.toDoubleOrNull(),
                    options = CaptureParameterPresets.exposureBias(original.exposureBiasEv),
                    label = ParamFormatter::exposureBias, tag = "capture-ev", modifier = Modifier.weight(1f),
                    original = original.exposureBiasEv,
                    onSelect = { draft = draft.copy(ev = it?.toString().orEmpty()) },
                )
                ParameterInput(
                    title = stringResource(R.string.label_iso), value = draft.iso,
                    onChange = { draft = draft.copy(iso = it) }, tag = "capture-iso", prefix = "ISO",
                    error = CaptureField.ISO in result.errors, errorText = stringResource(R.string.capture_positive_integer),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ParameterInput(
                    title = stringResource(R.string.capture_focal_actual), value = draft.focal,
                    onChange = { draft = draft.copy(focal = it) }, tag = "capture-focal", suffix = "mm",
                    error = CaptureField.FOCAL in result.errors, errorText = stringResource(R.string.capture_positive_number),
                    modifier = Modifier.weight(1f),
                )
                ParameterInput(
                    title = stringResource(R.string.capture_focal_equivalent), value = draft.equivalent,
                    onChange = { draft = draft.copy(equivalent = it) }, tag = "capture-equivalent", suffix = "mm",
                    error = CaptureField.EQUIVALENT in result.errors, errorText = stringResource(R.string.capture_focal_error),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(stringResource(R.string.capture_focal_hint), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ParameterDropdown(
                    title = stringResource(R.string.label_aperture), value = draft.aperture.toDoubleOrNull(),
                    options = CaptureParameterPresets.apertures(meta, draft.focal.toDoubleOrNull(), draft.aperture.toDoubleOrNull()),
                    label = ParamFormatter::aperture, tag = "capture-aperture", modifier = Modifier.weight(1f),
                    original = original.fNumber,
                    onSelect = { draft = draft.copy(aperture = it?.toString().orEmpty()) },
                )
                ParameterInput(
                    title = stringResource(R.string.label_shutter), value = draft.shutter,
                    onChange = { draft = draft.copy(shutter = it) }, tag = "capture-shutter", suffix = "s",
                    error = CaptureField.SHUTTER in result.errors, errorText = stringResource(R.string.capture_shutter_error),
                    modifier = Modifier.weight(1f), keyboardType = KeyboardType.Ascii,
                )
            }
            Text(stringResource(R.string.capture_aperture_hint), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.capture_missing_hint, missing), style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { restored = true; draft = CaptureParameterDraft.from(original) },
                modifier = Modifier.testTag("capture-restore"),
            ) { Text(stringResource(R.string.capture_restore)) }
            Button(
                onClick = { result.values?.let(onApply) }, enabled = result.values != null,
                modifier = Modifier.weight(1f).testTag("capture-confirm"),
            ) { Text(stringResource(R.string.device_confirm)) }
        }
    }
}

@Composable
private fun ParameterInput(
    title: String, value: String, onChange: (String) -> Unit, tag: String,
    error: Boolean, errorText: String, modifier: Modifier = Modifier,
    prefix: String? = null, suffix: String? = null, keyboardType: KeyboardType = KeyboardType.Decimal,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium)
        OutlinedTextField(
            value = value, onValueChange = onChange, singleLine = true,
            placeholder = { Text(stringResource(R.string.capture_missing)) },
            prefix = prefix?.let { { Text(it) } }, suffix = suffix?.let { { Text(it) } },
            isError = error, supportingText = if (error) ({ Text(errorText) }) else null,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth().testTag(tag),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParameterDropdown(
    title: String, value: Double?, options: List<Double>, label: (Double) -> String,
    tag: String, onSelect: (Double?) -> Unit, modifier: Modifier = Modifier,
    original: Double? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val missing = stringResource(R.string.capture_missing)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = value?.let(label) ?: missing, onValueChange = {}, readOnly = true, singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).testTag(tag),
            )
            ExposedDropdownMenu(expanded, onDismissRequest = { expanded = false }, modifier = Modifier.testTag("$tag-menu")) {
                (listOf<Double?>(null) + options).forEach { option ->
                    DropdownMenuItem(
                        text = {
                            val originalLabel = if (option != null && option == original) {
                                stringResource(R.string.capture_original)
                            } else ""
                            Text((option?.let(label) ?: missing) + originalLabel)
                        },
                        onClick = { onSelect(option); expanded = false },
                        modifier = Modifier.testTag("$tag-option-${option ?: "missing"}"),
                    )
                }
            }
        }
    }
}
