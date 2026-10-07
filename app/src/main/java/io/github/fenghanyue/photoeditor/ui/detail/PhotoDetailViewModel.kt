package io.github.fenghanyue.photoeditor.ui.detail

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.fenghanyue.photoeditor.media.FileInfo
import io.github.fenghanyue.photoeditor.media.MediaRepository
import io.github.fenghanyue.photoeditor.meta.ExifReader
import io.github.fenghanyue.photoeditor.meta.PhotoReadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PhotoDetailState(
    val uri: Uri? = null,
    val loading: Boolean = true,
    val fileInfo: FileInfo? = null,
    val result: PhotoReadResult? = null,
    /** 照片里实际存的像素尺寸（还没按方向标记旋转）。 */
    val storedSize: Size? = null,
    val preview: ImageBitmap? = null,
    val previewFailed: Boolean = false,
)

/** 只保留当前打开的这一张照片，看过的照片不会一直占着内存。 */
class PhotoDetailViewModel(application: Application) : AndroidViewModel(application) {

    private var loadJob: Job? = null

    var state by mutableStateOf(PhotoDetailState())
        private set

    fun show(uri: Uri) {
        if (state.uri == uri) return
        state = PhotoDetailState(uri = uri)
        load(uri, withPreview = true)
    }

    /** 授权读取位置之后，重新读一遍拍摄信息。 */
    fun reload() {
        val uri = state.uri ?: return
        load(uri, withPreview = state.preview == null)
    }

    private fun load(uri: Uri, withPreview: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val app = getApplication<Application>()
            val fileInfo = MediaRepository(app).loadFileInfo(uri)
            val result = withContext(Dispatchers.IO) { ExifReader(app).read(uri, fileInfo.dateTakenMillis) }
            state = state.copy(fileInfo = fileInfo, result = result)
            if (withPreview) {
                val storedSize = withContext(Dispatchers.IO) { readStoredSize(app, uri) }
                val preview = withContext(Dispatchers.IO) { decodePreview(app, uri) }
                state = state.copy(storedSize = storedSize, preview = preview, previewFailed = preview == null)
            }
            state = state.copy(loading = false)
        }
    }

    private fun readStoredSize(context: Context, uri: Uri): Size? = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, options)
            if (options.outWidth > 0 && options.outHeight > 0) Size(options.outWidth, options.outHeight) else null
        }
    } catch (e: Exception) {
        null
    }

    /** 解码一张长边约 1000～2000 像素的预览图，用来确认照片显示的方向对不对。 */
    private fun decodePreview(context: Context, uri: Uri): ImageBitmap? = try {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longSide = maxOf(info.size.width, info.size.height)
            var sampleSize = 1
            while (longSide / (sampleSize * 2) >= PREVIEW_MIN_LONG_SIDE) sampleSize *= 2
            decoder.setTargetSampleSize(sampleSize)
        }.asImageBitmap()
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val PREVIEW_MIN_LONG_SIDE = 1000
    }
}
