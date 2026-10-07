package io.github.fenghanyue.photoeditor.ui.gallery

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fenghanyue.photoeditor.BuildConfig
import io.github.fenghanyue.photoeditor.R
import io.github.fenghanyue.photoeditor.media.MediaAccess
import io.github.fenghanyue.photoeditor.media.MediaImage
import io.github.fenghanyue.photoeditor.media.MediaPermissions

@Composable
fun GalleryScreen(onOpenPhoto: (MediaImage) -> Unit) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel = viewModel { GalleryViewModel(application) }
    var deniedOnce by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.refresh()
        if (MediaPermissions.access(context) == MediaAccess.NONE) deniedOnce = true
    }
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    GalleryContent(
        state = viewModel.state,
        versionName = BuildConfig.VERSION_NAME,
        deniedOnce = deniedOnce,
        onRequestAccess = { permissionLauncher.launch(MediaPermissions.toRequest()) },
        onOpenSettings = {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ),
            )
        },
        onSelectBucket = viewModel::selectBucket,
        onOpenPhoto = onOpenPhoto,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryContent(
    state: GalleryState,
    versionName: String,
    deniedOnce: Boolean,
    onRequestAccess: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectBucket: (Long?) -> Unit,
    onOpenPhoto: (MediaImage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name))
                        Text(
                            text = stringResource(R.string.version_label, versionName),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.access == MediaAccess.NONE ->
                    PermissionPrompt(deniedOnce, onRequestAccess, onOpenSettings)
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> Column(Modifier.fillMaxSize()) {
                    if (state.access == MediaAccess.PARTIAL) PartialAccessBanner(onRequestAccess)
                    if (state.buckets.size > 1) BucketChips(state, onSelectBucket)
                    if (state.visibleImages.isEmpty()) {
                        Text(
                            text = stringResource(R.string.gallery_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        PhotoGrid(state.visibleImages, onOpenPhoto)
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionPrompt(deniedOnce: Boolean, onRequestAccess: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.permission_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.permission_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequestAccess) { Text(stringResource(R.string.permission_grant)) }
        if (deniedOnce) {
            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.permission_open_settings)) }
        }
    }
}

@Composable
private fun PartialAccessBanner(onRequestAccess: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.partial_access_message),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRequestAccess) { Text(stringResource(R.string.partial_access_more)) }
        }
    }
}

@Composable
private fun BucketChips(state: GalleryState, onSelectBucket: (Long?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = state.selectedBucketId == null,
                onClick = { onSelectBucket(null) },
                label = {
                    Text(stringResource(R.string.bucket_label, stringResource(R.string.bucket_all), state.images.size))
                },
            )
        }
        items(state.buckets, key = { it.id }) { bucket ->
            FilterChip(
                selected = state.selectedBucketId == bucket.id,
                onClick = { onSelectBucket(bucket.id) },
                label = { Text(stringResource(R.string.bucket_label, bucket.name, bucket.count)) },
            )
        }
    }
}

@Composable
private fun PhotoGrid(images: List<MediaImage>, onOpenPhoto: (MediaImage) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 96.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(images, key = { it.id }) { image ->
            MediaThumbnail(
                uri = image.uri,
                contentDescription = image.displayName,
                modifier = Modifier
                    .aspectRatio(1f)
                    .clickable { onOpenPhoto(image) },
            )
        }
    }
}
