package io.github.fenghanyue.photoeditor.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import io.github.fenghanyue.photoeditor.BuildConfig
import io.github.fenghanyue.photoeditor.meta.ExifMetaParser
import io.github.fenghanyue.photoeditor.meta.ExifReader
import io.github.fenghanyue.photoeditor.render.WatermarkAssets
import io.github.fenghanyue.photoeditor.render.WatermarkContent
import io.github.fenghanyue.photoeditor.render.WatermarkOptions
import io.github.fenghanyue.photoeditor.render.WatermarkRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** 按原尺寸给照片加水印，另存到相册的 Pictures/PhotoEditor，原图不动。 */
class PhotoExporter(private val context: Context) {

    /** @property downscaled 照片太大或内存不够，长宽各缩小了一半 */
    class Saved(val uri: Uri, val displayName: String, val downscaled: Boolean)

    class Rendered(val width: Int, val height: Int, val downscaled: Boolean)

    suspend fun export(
        source: Uri,
        sourceName: String?,
        content: WatermarkContent,
        options: WatermarkOptions,
    ): Saved = withContext(Dispatchers.IO) {
        val temp = File.createTempFile("export-", ".jpg", context.cacheDir)
        try {
            val rendered = renderToFile(source, content, options, temp)
            val uri = saveToGallery(temp, outputName(sourceName))
            Saved(uri, displayName(uri) ?: outputName(sourceName), rendered.downscaled)
        } finally {
            temp.delete()
        }
    }

    /** 解码原图 → 画水印 → 存成 JPEG → 复制拍摄信息。不碰相册。 */
    fun renderToFile(source: Uri, content: WatermarkContent, options: WatermarkOptions, target: File): Rendered {
        val assets = WatermarkAssets.get(context)
        val (output, downscaled) = try {
            render(source, content, options, assets, forceHalf = false)
        } catch (e: OutOfMemoryError) {
            // 内存不够时缩小一半再试一次
            render(source, content, options, assets, forceHalf = true)
        }
        val width = output.width
        val height = output.height
        try {
            target.outputStream().buffered().use { output.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        } finally {
            output.recycle()
        }
        val sourceExif = ExifReader(context).openBest(source).exif
        val hasGps = ExifMetaParser.parse(sourceExif, fileDateTakenMillis = null).gps != null
        ExifCopier.copy(
            source = sourceExif,
            target = ExifInterface(target.absolutePath),
            width = width,
            height = height,
            copyGps = options.keepGps && hasGps,
            software = "PhotoEditor ${BuildConfig.VERSION_NAME}",
        )
        return Rendered(width, height, downscaled)
    }

    private fun render(
        source: Uri,
        content: WatermarkContent,
        options: WatermarkOptions,
        assets: WatermarkAssets,
        forceHalf: Boolean,
    ): Pair<Bitmap, Boolean> {
        val (photo, halved) = decode(source, forceHalf)
        try {
            val output = WatermarkRenderer.render(photo, content, options, assets)
            if (output !== photo) photo.recycle()
            return output to halved
        } catch (e: OutOfMemoryError) {
            photo.recycle()
            throw e
        }
    }

    /** 系统解码时会按方向标记把照片转正，这里不用再转。 */
    private fun decode(source: Uri, forceHalf: Boolean): Pair<Bitmap, Boolean> {
        var halved = forceHalf
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, source)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = true
            if (info.size.width.toLong() * info.size.height > MAX_PIXELS) halved = true
            if (halved) decoder.setTargetSampleSize(2)
        }
        return bitmap to halved
    }

    private fun saveToGallery(file: File, displayName: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, OUTPUT_FOLDER)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: throw IOException("相册拒绝创建新文件")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("无法写入相册")
            out.use { stream -> file.inputStream().use { it.copyTo(stream) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    /** 重名时系统会自动改名（比如加上 "(1)"），所以存完再查一次实际的文件名。 */
    private fun displayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (e: RuntimeException) {
        null
    }

    companion object {
        const val OUTPUT_FOLDER = "Pictures/PhotoEditor"
        private const val JPEG_QUALITY = 95

        /** 超过 6400 万像素的照片长宽各缩小一半，避免内存不够。 */
        private const val MAX_PIXELS = 64_000_000L

        fun outputName(sourceName: String?): String {
            val base = sourceName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
                ?: "photo_${System.currentTimeMillis()}"
            return "${base}_wm.jpg"
        }
    }
}
