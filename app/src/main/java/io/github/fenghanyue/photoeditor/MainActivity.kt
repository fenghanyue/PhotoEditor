package io.github.fenghanyue.photoeditor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import io.github.fenghanyue.photoeditor.ui.Route
import io.github.fenghanyue.photoeditor.ui.RouteStackSaver
import io.github.fenghanyue.photoeditor.ui.detail.PhotoDetailScreen
import io.github.fenghanyue.photoeditor.ui.editor.EditorScreen
import io.github.fenghanyue.photoeditor.ui.gallery.GalleryScreen
import io.github.fenghanyue.photoeditor.ui.theme.PhotoEditorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 从系统相册"分享"进来时，直接给这张照片加水印
        val firstRoute = sharedImageUri(intent)?.let { Route.Editor(it) } ?: Route.Gallery
        setContent {
            PhotoEditorTheme {
                var backStack by rememberSaveable(stateSaver = RouteStackSaver) {
                    mutableStateOf(listOf(firstRoute))
                }
                val goBack = {
                    if (backStack.size > 1) backStack = backStack.dropLast(1) else finish()
                }
                BackHandler(enabled = backStack.size > 1, onBack = goBack)
                when (val route = backStack.last()) {
                    Route.Gallery -> GalleryScreen(
                        onOpenPhoto = { image -> backStack = backStack + Route.Editor(image.uri) },
                    )
                    is Route.Editor -> EditorScreen(
                        uri = route.uri,
                        onBack = goBack,
                        onOpenInfo = { backStack = backStack + Route.Detail(route.uri) },
                    )
                    is Route.Detail -> PhotoDetailScreen(uri = route.uri, onBack = goBack)
                }
            }
        }
    }

    private fun sharedImageUri(intent: Intent?): Uri? =
        intent?.takeIf { it.action == Intent.ACTION_SEND }
            ?.let { IntentCompat.getParcelableExtra(it, Intent.EXTRA_STREAM, Uri::class.java) }
}
