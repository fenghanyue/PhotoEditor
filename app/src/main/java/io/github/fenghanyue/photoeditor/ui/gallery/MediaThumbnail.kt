package io.github.fenghanyue.photoeditor.ui.gallery

import android.net.Uri
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 用系统缓存好的缩略图，比自己解码 2400 万像素的原图快得多。 */
@Composable
fun MediaThumbnail(uri: Uri, contentDescription: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState(initialValue = ThumbnailCache.get(uri), uri) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.loadThumbnail(uri, Size(THUMBNAIL_PX, THUMBNAIL_PX), null)
                        .asImageBitmap()
                } catch (e: Exception) {
                    // 文件刚被删除、格式不支持或权限被收回时，留一个空白格子
                    null
                }
            }?.also { ThumbnailCache.put(uri, it) }
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private const val THUMBNAIL_PX = 384

private object ThumbnailCache : LruCache<Uri, ImageBitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
    override fun sizeOf(key: Uri, value: ImageBitmap): Int = value.width * value.height * 4
}
