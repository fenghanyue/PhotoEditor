package io.github.fenghanyue.photoeditor.ui.editor

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.fenghanyue.photoeditor.export.PhotoExporter
import io.github.fenghanyue.photoeditor.media.MediaRepository
import io.github.fenghanyue.photoeditor.meta.DeviceNames
import io.github.fenghanyue.photoeditor.meta.ExifReader
import io.github.fenghanyue.photoeditor.meta.PhotoMeta
import io.github.fenghanyue.photoeditor.render.TemplateKind
import io.github.fenghanyue.photoeditor.render.WatermarkAssets
import io.github.fenghanyue.photoeditor.render.WatermarkContent
import io.github.fenghanyue.photoeditor.render.WatermarkOptions
import io.github.fenghanyue.photoeditor.render.WatermarkRenderer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditorState(
    val uri: Uri? = null,
    val loading: Boolean = true,
    val meta: PhotoMeta? = null,
    val fileName: String? = null,
    /** 机型名称输入框里的文字。 */
    val deviceName: String = "",
    val options: WatermarkOptions = WatermarkOptions(),
    val preview: ImageBitmap? = null,
    val previewFailed: Boolean = false,
    val saving: Boolean = false,
    /** 保存结果；界面显示一次提示后清掉。 */
    val saveResult: SaveResult? = null,
) {
    val hasGps: Boolean get() = meta?.gps != null
}

sealed interface SaveResult {
    data class Success(val uri: Uri, val displayName: String, val downscaled: Boolean) : SaveResult

    data class Failure(val reason: String) : SaveResult
}

/**
 * 加水印页。只保留当前这一张照片；选项在换照片时保持不变，
 * 改过的机型名称按"品牌 + 机型"记住，同一台相机的照片都会用上。
 */
class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private var previewSource: Bitmap? = null
    private var loadJob: Job? = null
    private var renderJob: Job? = null
    private val deviceNameOverrides = mutableMapOf<String, String>()

    var state by mutableStateOf(EditorState())
        private set

    fun show(uri: Uri) {
        if (state.uri == uri) return
        loadJob?.cancel()
        renderJob?.cancel()
        previewSource = null
        state = EditorState(uri = uri, options = state.options, saving = state.saving)
        loadJob = viewModelScope.launch {
            val app = getApplication<Application>()
            val fileInfo = MediaRepository(app).loadFileInfo(uri)
            val meta = withContext(Dispatchers.IO) { ExifReader(app).read(uri, fileInfo.dateTakenMillis).meta }
            val source = withContext(Dispatchers.IO) { decodePreviewSource(app, uri) }
            previewSource = source
            state = state.copy(
                loading = false,
                meta = meta,
                fileName = fileInfo.displayName,
                deviceName = deviceNameOverrides[deviceKey(meta)] ?: autoDeviceName(meta),
                previewFailed = source == null,
            )
            renderPreview(debounce = false)
        }
    }

    fun updateOptions(change: (WatermarkOptions) -> WatermarkOptions) {
        state = state.copy(options = change(state.options))
        renderPreview()
    }

    fun updateDeviceName(name: String) {
        val meta = state.meta ?: return
        if (name == autoDeviceName(meta)) {
            deviceNameOverrides.remove(deviceKey(meta))
        } else {
            deviceNameOverrides[deviceKey(meta)] = name
        }
        state = state.copy(deviceName = name)
        renderPreview()
    }

    fun save() {
        val uri = state.uri ?: return
        val meta = state.meta ?: return
        if (state.saving) return
        val fileName = state.fileName
        val content = content(meta)
        val options = state.options
        state = state.copy(saving = true)
        viewModelScope.launch {
            val result = try {
                val saved = PhotoExporter(getApplication()).export(uri, fileName, content, options)
                SaveResult.Success(saved.uri, saved.displayName, saved.downscaled)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SaveResult.Failure(e.message ?: e.javaClass.simpleName)
            } catch (e: OutOfMemoryError) {
                SaveResult.Failure("内存不够")
            }
            state = state.copy(saving = false, saveResult = result)
        }
    }

    fun consumeSaveResult() {
        state = state.copy(saveResult = null)
    }

    private fun content(meta: PhotoMeta) = WatermarkContent.from(meta, state.deviceName, state.options)

    /** 改选项后在后台重画预览；连续输入时只画最后一次。 */
    private fun renderPreview(debounce: Boolean = true) {
        val source = previewSource ?: return
        val meta = state.meta ?: return
        val content = content(meta)
        val options = state.options
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            if (debounce) delay(PREVIEW_DEBOUNCE_MS)
            val rendered = withContext(Dispatchers.Default) {
                // 信息叠加直接画在图上，所以先复制一份，别把缓存的原图画脏了
                val photo = if (options.template == TemplateKind.OVERLAY) source.copy(Bitmap.Config.ARGB_8888, true) else source
                WatermarkRenderer.render(photo, content, options, WatermarkAssets.get(getApplication()))
            }
            state = state.copy(preview = rendered.asImageBitmap())
        }
    }

    /** 解码一张长边 1200～2400 像素的软件位图，后面每次改选项都在它上面重画预览。 */
    private fun decodePreviewSource(context: Context, uri: Uri): Bitmap? = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            // 屏幕按 sRGB 显示预览；统一成 sRGB 也避开了 HDR 照片的特殊色彩空间
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            val longSide = maxOf(info.size.width, info.size.height)
            var sampleSize = 1
            while (longSide / (sampleSize * 2) >= PREVIEW_MIN_LONG_SIDE) sampleSize *= 2
            decoder.setTargetSampleSize(sampleSize)
        }
    } catch (e: Exception) {
        null
    }

    private fun autoDeviceName(meta: PhotoMeta): String = DeviceNames.displayName(meta.make, meta.model).orEmpty()

    private fun deviceKey(meta: PhotoMeta) = "${meta.make}|${meta.model}"

    private companion object {
        const val PREVIEW_MIN_LONG_SIDE = 1200
        const val PREVIEW_DEBOUNCE_MS = 60L
    }
}
