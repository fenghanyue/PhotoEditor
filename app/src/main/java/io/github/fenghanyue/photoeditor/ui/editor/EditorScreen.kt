package io.github.fenghanyue.photoeditor.ui.editor

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fenghanyue.photoeditor.R
import io.github.fenghanyue.photoeditor.export.PhotoExporter
import io.github.fenghanyue.photoeditor.geo.Region
import io.github.fenghanyue.photoeditor.geo.RegionStyle
import io.github.fenghanyue.photoeditor.render.Corner
import io.github.fenghanyue.photoeditor.render.FrameColor
import io.github.fenghanyue.photoeditor.render.FrameLeftDetail
import io.github.fenghanyue.photoeditor.render.FrameRightDetail
import io.github.fenghanyue.photoeditor.render.TemplateKind
import io.github.fenghanyue.photoeditor.render.WatermarkOptions
import kotlinx.coroutines.launch

@Composable
fun EditorScreen(uri: Uri, onBack: () -> Unit, onOpenInfo: () -> Unit) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel = viewModel { EditorViewModel(application) }
    LaunchedEffect(uri) { viewModel.show(uri) }
    // 刚从另一张照片切过来时先显示加载中，不要闪一下上一张
    val state = viewModel.state.takeIf { it.uri == uri }
        ?: EditorState(uri = uri, options = viewModel.state.options, saving = viewModel.state.saving)
    EditorContent(
        state = state,
        onBack = onBack,
        onOpenInfo = onOpenInfo,
        onOptionsChange = viewModel::updateOptions,
        onDeviceNameChange = viewModel::updateDeviceName,
        onRegionSelect = viewModel::selectRegion,
        onSave = viewModel::save,
        onSaveResultShown = viewModel::consumeSaveResult,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorContent(
    state: EditorState,
    onBack: () -> Unit,
    onOpenInfo: () -> Unit,
    onOptionsChange: ((WatermarkOptions) -> WatermarkOptions) -> Unit,
    onDeviceNameChange: (String) -> Unit,
    onRegionSelect: (Region?) -> Unit,
    onSave: () -> Unit,
    onSaveResultShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var pickingRegion by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val result = state.saveResult
    val viewLabel = stringResource(R.string.view)
    val message = when (result) {
        is SaveResult.Success -> stringResource(
            if (result.downscaled) R.string.saved_downscaled else R.string.saved,
            PhotoExporter.OUTPUT_FOLDER,
            result.displayName,
        )
        is SaveResult.Failure -> stringResource(R.string.save_failed, result.reason)
        null -> null
    }
    LaunchedEffect(result) {
        if (result == null || message == null) return@LaunchedEffect
        onSaveResultShown()
        // 提示放在独立的协程里，清掉保存结果后提示也不会被打断
        scope.launch {
            val action = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = viewLabel.takeIf { result is SaveResult.Success },
                duration = SnackbarDuration.Long,
            )
            if (action == SnackbarResult.ActionPerformed && result is SaveResult.Success) {
                openImage(context, result.uri)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.editor_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = onOpenInfo) {
                        Icon(painterResource(R.drawable.ic_info), contentDescription = stringResource(R.string.editor_info))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
        ) {
            PreviewArea(
                state,
                Modifier
                    .fillMaxWidth()
                    .weight(0.45f),
            )
            OptionsPanel(
                state = state,
                onOptionsChange = onOptionsChange,
                onDeviceNameChange = onDeviceNameChange,
                onPickRegion = { pickingRegion = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.55f)
                    .verticalScroll(rememberScrollState()),
            )
            Button(
                onClick = onSave,
                enabled = !state.saving && state.meta != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                if (state.saving) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }

    val regionIndex = state.regionIndex
    if (pickingRegion && regionIndex != null) {
        RegionPickerSheet(
            index = regionIndex,
            current = state.region,
            gpsRegion = state.gpsRegion,
            recent = state.recentRegions,
            onSelect = onRegionSelect,
            onDismiss = { pickingRegion = false },
        )
    }
}

@Composable
private fun PreviewArea(state: EditorState, modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        val preview = state.preview
        when {
            preview != null -> Image(
                bitmap = preview,
                contentDescription = state.fileName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
            )
            state.previewFailed -> Text(stringResource(R.string.detail_preview_failed), color = MaterialTheme.colorScheme.error)
            else -> CircularProgressIndicator()
        }
    }
}

@Composable
private fun OptionsPanel(
    state: EditorState,
    onOptionsChange: ((WatermarkOptions) -> WatermarkOptions) -> Unit,
    onDeviceNameChange: (String) -> Unit,
    onPickRegion: () -> Unit,
    modifier: Modifier,
) {
    val options = state.options
    Column(
        modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Choices(
            items = listOf(
                TemplateKind.FRAME to stringResource(R.string.template_frame),
                TemplateKind.OVERLAY to stringResource(R.string.template_overlay),
            ),
            selected = options.template,
            onSelect = { value -> onOptionsChange { it.copy(template = value) } },
        )
        if (options.template == TemplateKind.FRAME) {
            LabeledChoices(
                label = stringResource(R.string.option_frame_color),
                items = listOf(
                    FrameColor.WHITE to stringResource(R.string.frame_white),
                    FrameColor.BLACK to stringResource(R.string.frame_black),
                ),
                selected = options.frameColor,
                onSelect = { value -> onOptionsChange { it.copy(frameColor = value) } },
            )
            LabeledChoices(
                label = stringResource(R.string.option_left_detail),
                items = listOf(
                    FrameLeftDetail.TIME to stringResource(R.string.detail_time),
                    FrameLeftDetail.LENS to stringResource(R.string.detail_lens),
                    FrameLeftDetail.SIGNATURE to stringResource(R.string.detail_signature),
                ),
                selected = options.frameLeftDetail,
                onSelect = { value -> onOptionsChange { it.copy(frameLeftDetail = value) } },
            )
            LabeledChoices(
                label = stringResource(R.string.option_right_detail),
                items = listOf(
                    FrameRightDetail.LOCATION to stringResource(R.string.detail_location),
                    FrameRightDetail.COORDINATES to stringResource(R.string.detail_coordinates),
                    FrameRightDetail.LENS to stringResource(R.string.detail_lens),
                    FrameRightDetail.NONE to stringResource(R.string.detail_none),
                ),
                selected = options.frameRightDetail,
                onSelect = { value -> onOptionsChange { it.copy(frameRightDetail = value) } },
            )
            SwitchRow(stringResource(R.string.option_show_logo), options.showLogo) { checked ->
                onOptionsChange { it.copy(showLogo = checked) }
            }
            OutlinedTextField(
                value = state.deviceName,
                onValueChange = onDeviceNameChange,
                label = { Text(stringResource(R.string.field_device_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LabeledChoices(
                label = stringResource(R.string.option_corner),
                items = listOf(
                    Corner.BOTTOM_LEFT to stringResource(R.string.corner_bottom_left),
                    Corner.BOTTOM_RIGHT to stringResource(R.string.corner_bottom_right),
                    Corner.TOP_LEFT to stringResource(R.string.corner_top_left),
                    Corner.TOP_RIGHT to stringResource(R.string.corner_top_right),
                ),
                selected = options.corner,
                onSelect = { value -> onOptionsChange { it.copy(corner = value) } },
            )
            SwitchRow(stringResource(R.string.option_backdrop), options.backdrop) { checked ->
                onOptionsChange { it.copy(backdrop = checked) }
            }
            if (state.hasGps) {
                SwitchRow(stringResource(R.string.option_show_coordinates), options.showCoordinates) { checked ->
                    onOptionsChange { it.copy(showCoordinates = checked) }
                }
            }
        }
        RegionRow(state, onPickRegion)
        if (state.region != null) {
            LabeledChoices(
                label = stringResource(R.string.option_region_style),
                items = listOf(
                    RegionStyle.PROVINCE_COUNTY to stringResource(R.string.region_style_province_county),
                    RegionStyle.FULL to stringResource(R.string.region_style_full),
                    RegionStyle.COUNTY to stringResource(R.string.region_style_county),
                ),
                selected = options.regionStyle,
                onSelect = { value -> onOptionsChange { it.copy(regionStyle = value) } },
                showIcon = false,
            )
        }
        OutlinedTextField(
            value = options.placeName,
            onValueChange = { text -> onOptionsChange { it.copy(placeName = text) } },
            label = { Text(stringResource(R.string.field_place_name)) },
            placeholder = { Text(stringResource(R.string.field_place_name_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (options.template == TemplateKind.FRAME) {
            OutlinedTextField(
                value = options.signature,
                onValueChange = { text -> onOptionsChange { it.copy(signature = text) } },
                label = { Text(stringResource(R.string.field_signature)) },
                placeholder = { Text(stringResource(R.string.field_signature_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            OutlinedTextField(
                value = options.note,
                onValueChange = { text -> onOptionsChange { it.copy(note = text) } },
                label = { Text(stringResource(R.string.field_note)) },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.hasGps) {
            SwitchRow(stringResource(R.string.option_keep_gps), options.keepGps) { checked ->
                onOptionsChange { it.copy(keepGps = checked) }
            }
        }
    }
}

/** 地区：当前地区和它是怎么来的，右边是"选择"按钮。 */
@Composable
private fun RegionRow(state: EditorState, onPick: () -> Unit) {
    val region = state.region
    val note = when {
        state.loading -> null
        region != null -> when (state.regionSource) {
            RegionSource.GPS -> R.string.region_source_gps
            RegionSource.MANUAL -> R.string.region_source_manual
            RegionSource.PREVIOUS -> R.string.region_source_previous
            null -> null
        }
        // 只在真的查不到时提示；查到了但被手动清除的，不算
        state.hasGps && state.gpsRegion == null && state.regionIndex != null -> R.string.region_gps_not_found
        else -> null
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.field_region), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(56.dp))
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    region != null -> region.text(state.options.regionStyle)
                    state.loading -> ""
                    else -> stringResource(R.string.region_none)
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            note?.let {
                Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // 照片还在读取时先不让选，免得选好的地区被读完后的结果覆盖
        TextButton(onClick = onPick, enabled = state.regionIndex != null && !state.loading) {
            Text(stringResource(R.string.region_pick))
        }
    }
}

/** showIcon = false 时选中的一项不显示对勾，文字长的选项也放得下。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Choices(
    items: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    showIcon: Boolean = true,
) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        items.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, items.size),
                icon = { if (showIcon) SegmentedButtonDefaults.Icon(value == selected) },
            ) { Text(label, maxLines = 1) }
        }
    }
}

@Composable
private fun <T> LabeledChoices(
    label: String,
    items: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    showIcon: Boolean = true,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(56.dp))
        Choices(items, selected, onSelect, Modifier.weight(1f), showIcon)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun openImage(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "image/jpeg")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // 手机上没有能看图片的 App 时什么也不做
    }
}
