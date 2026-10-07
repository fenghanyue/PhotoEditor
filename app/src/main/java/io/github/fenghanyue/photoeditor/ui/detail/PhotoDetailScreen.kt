package io.github.fenghanyue.photoeditor.ui.detail

import android.Manifest
import android.app.Application
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fenghanyue.photoeditor.R
import io.github.fenghanyue.photoeditor.meta.DeviceNames
import io.github.fenghanyue.photoeditor.meta.LocationStatus
import io.github.fenghanyue.photoeditor.meta.MetaSource
import io.github.fenghanyue.photoeditor.meta.ParamFormatter
import io.github.fenghanyue.photoeditor.meta.PhotoMeta
import io.github.fenghanyue.photoeditor.meta.PhotoReadResult
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PhotoDetailScreen(uri: Uri, onBack: () -> Unit) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel = viewModel { PhotoDetailViewModel(application) }
    LaunchedEffect(uri) { viewModel.show(uri) }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.reload()
    }
    PhotoDetailContent(
        // 刚从另一张照片切过来时，先显示加载中，不要闪一下上一张的信息
        state = viewModel.state.takeIf { it.uri == uri } ?: PhotoDetailState(uri = uri),
        onBack = onBack,
        onRequestLocation = { locationPermission.launch(Manifest.permission.ACCESS_MEDIA_LOCATION) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDetailContent(
    state: PhotoDetailState,
    onBack: () -> Unit,
    onRequestLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { PhotoPreview(state) }
            val result = state.result
            if (result == null) {
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            } else {
                item { TimeSection(result.meta) }
                item { DeviceSection(result.meta) }
                item { ExposureSection(result.meta) }
                item { LocationSection(result, onRequestLocation) }
                item { FileSection(state, result.meta) }
                if (result.meta.rawTags.isNotEmpty()) item { RawTagsSection(result.meta.rawTags) }
            }
        }
    }
}

@Composable
private fun PhotoPreview(state: PhotoDetailState) {
    val bitmap = state.preview
    when {
        bitmap != null -> Image(
            bitmap = bitmap,
            contentDescription = state.fileInfo?.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp),
        )
        state.previewFailed -> Text(
            text = stringResource(R.string.detail_preview_failed),
            color = MaterialTheme.colorScheme.error,
        )
        else -> Box(
            Modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

@Composable
private fun TimeSection(meta: PhotoMeta) {
    Section(stringResource(R.string.section_time)) {
        val time = meta.dateTime
        InfoRow(
            label = stringResource(R.string.label_time),
            value = time?.value?.format(TIME_FORMAT),
            note = time?.let { sourceNote(it.source) },
        )
        InfoRow(stringResource(R.string.label_offset), meta.offsetTime)
    }
}

@Composable
private fun DeviceSection(meta: PhotoMeta) {
    Section(stringResource(R.string.section_device)) {
        val raw = listOfNotNull(meta.make, meta.model).joinToString(" / ").takeIf { it.isNotEmpty() }
        InfoRow(
            label = stringResource(R.string.label_camera),
            value = DeviceNames.displayName(meta.make, meta.model),
            note = raw?.let { stringResource(R.string.note_raw_device, it) },
        )
        InfoRow(stringResource(R.string.label_lens), meta.lensModel, meta.lensMake)
    }
}

@Composable
private fun ExposureSection(meta: PhotoMeta) {
    Section(stringResource(R.string.section_exposure)) {
        val focal = meta.focalLengthMm
        val focal35 = meta.focalLength35mm
        when {
            focal35 != null -> InfoRow(
                label = stringResource(R.string.label_focal),
                value = ParamFormatter.focalLength(focal35.toDouble()),
                note = focal?.let { stringResource(R.string.note_focal_actual, ParamFormatter.focalLength(it)) }
                    ?: stringResource(R.string.note_focal_equivalent),
            )
            focal != null -> InfoRow(
                label = stringResource(R.string.label_focal),
                value = ParamFormatter.focalLength(focal),
                note = stringResource(R.string.note_focal_no_equivalent),
            )
            else -> InfoRow(stringResource(R.string.label_focal), null)
        }
        InfoRow(stringResource(R.string.label_aperture), meta.fNumber?.let(ParamFormatter::aperture))
        InfoRow(stringResource(R.string.label_shutter), meta.exposureTimeSec?.let(ParamFormatter::shutter))
        InfoRow(stringResource(R.string.label_iso), meta.iso?.let(ParamFormatter::iso))
        InfoRow(stringResource(R.string.label_bias), meta.exposureBiasEv?.let(ParamFormatter::exposureBias))
        InfoRow(
            label = stringResource(R.string.label_flash),
            value = meta.flashFired?.let {
                stringResource(if (it) R.string.value_flash_fired else R.string.value_flash_not_fired)
            },
        )
    }
}

@Composable
private fun LocationSection(result: PhotoReadResult, onRequestLocation: () -> Unit) {
    Section(stringResource(R.string.section_location)) {
        val gps = result.meta.gps
        val label = stringResource(R.string.label_coordinates)
        when (result.locationStatus) {
            LocationStatus.PRESENT -> if (gps != null) {
                InfoRow(
                    label = label,
                    value = ParamFormatter.coordinatesDecimal(gps.latitude, gps.longitude),
                    note = ParamFormatter.coordinatesDms(gps.latitude, gps.longitude),
                )
                InfoRow(stringResource(R.string.label_altitude), gps.altitudeMeters?.let(ParamFormatter::altitude))
            }
            LocationStatus.ABSENT -> InfoRow(
                label = label,
                value = stringResource(R.string.location_absent),
                note = stringResource(R.string.location_absent_note),
            )
            LocationStatus.NO_PERMISSION -> {
                InfoRow(
                    label = label,
                    value = stringResource(R.string.location_no_permission),
                    note = stringResource(R.string.location_no_permission_note),
                )
                TextButton(onClick = onRequestLocation) { Text(stringResource(R.string.location_grant)) }
            }
            LocationStatus.UNVERIFIED -> InfoRow(
                label = label,
                value = stringResource(R.string.location_unverified),
                note = stringResource(R.string.location_unverified_note),
            )
        }
    }
}

@Composable
private fun FileSection(state: PhotoDetailState, meta: PhotoMeta) {
    Section(stringResource(R.string.section_file)) {
        val info = state.fileInfo
        val context = LocalContext.current
        InfoRow(stringResource(R.string.label_file_name), info?.displayName)
        InfoRow(stringResource(R.string.label_format), info?.mimeType)
        InfoRow(
            label = stringResource(R.string.label_pixels),
            value = state.storedSize?.let { "${it.width} × ${it.height}" },
            note = meta.rotationDegrees.takeIf { it != 0 }?.let { stringResource(R.string.note_rotation, it) },
        )
        InfoRow(
            label = stringResource(R.string.label_size),
            value = info?.sizeBytes?.let { Formatter.formatShortFileSize(context, it) },
        )
        InfoRow(stringResource(R.string.label_folder), info?.relativePath)
    }
}

@Composable
private fun RawTagsSection(tags: List<Pair<String, String>>) {
    Section(stringResource(R.string.section_raw)) {
        tags.forEach { (tag, value) ->
            Column(Modifier.padding(vertical = 4.dp)) {
                Text(tag, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), content = content)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String?, note: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = value ?: stringResource(R.string.value_missing),
                style = MaterialTheme.typography.bodyMedium,
                color = if (value == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            )
            if (note != null) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun sourceNote(source: MetaSource): String = stringResource(
    when (source) {
        MetaSource.EXIF -> R.string.source_exif
        MetaSource.EXIF_MODIFIED -> R.string.source_exif_modified
        MetaSource.FILE -> R.string.source_file
    },
)

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss EEEE", Locale.CHINA)
