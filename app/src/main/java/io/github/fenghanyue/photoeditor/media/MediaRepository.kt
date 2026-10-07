package io.github.fenghanyue.photoeditor.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 系统媒体库里的一张照片。 */
data class MediaImage(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val bucketId: Long,
    val bucketName: String,
    val dateTakenMillis: Long?,
)

/** 一个相册文件夹，比如"Camera"、"Download"。 */
data class MediaBucket(val id: Long, val name: String, val count: Int)

/** 详情页用的文件信息；别的 App 分享来的照片可能只有一部分。 */
data class FileInfo(
    val displayName: String? = null,
    val sizeBytes: Long? = null,
    val mimeType: String? = null,
    val relativePath: String? = null,
    val dateTakenMillis: Long? = null,
)

/** 从系统媒体库查照片。 */
class MediaRepository(private val context: Context) {

    suspend fun loadImages(): List<MediaImage> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
        )
        val selection = "${MediaStore.Images.Media.MIME_TYPE} IN " +
            SUPPORTED_MIME_TYPES.joinToString(prefix = "(", postfix = ")") { "?" }
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC, " +
            "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        context.contentResolver.query(COLLECTION, projection, selection, SUPPORTED_MIME_TYPES, sortOrder)
            ?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val bucketIdCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                val bucketNameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                buildList(c.count) {
                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        add(
                            MediaImage(
                                id = id,
                                uri = ContentUris.withAppendedId(COLLECTION, id),
                                displayName = c.getStringOrNull(nameCol).orEmpty(),
                                bucketId = c.getLong(bucketIdCol),
                                bucketName = c.getStringOrNull(bucketNameCol).orEmpty(),
                                dateTakenMillis = c.getLongOrNull(dateCol)?.takeIf { it > 0 },
                            ),
                        )
                    }
                }
            }
            .orEmpty()
    }

    suspend fun loadFileInfo(uri: Uri): FileInfo = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val fromMediaStore = uri.authority == MediaStore.AUTHORITY
        // 别的 App 的分享地址只认识 OpenableColumns 里的列，问多了可能直接报错
        val projection = if (fromMediaStore) {
            arrayOf(
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.RELATIVE_PATH,
                MediaStore.Images.Media.DATE_TAKEN,
            )
        } else {
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        }
        val info = try {
            resolver.query(uri, projection, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                fun string(column: String) = c.getColumnIndex(column).takeIf { it >= 0 }?.let(c::getStringOrNull)
                fun long(column: String) = c.getColumnIndex(column).takeIf { it >= 0 }?.let(c::getLongOrNull)
                FileInfo(
                    displayName = string(OpenableColumns.DISPLAY_NAME),
                    sizeBytes = long(OpenableColumns.SIZE),
                    mimeType = string(MediaStore.Images.Media.MIME_TYPE),
                    relativePath = string(MediaStore.Images.Media.RELATIVE_PATH),
                    dateTakenMillis = long(MediaStore.Images.Media.DATE_TAKEN)?.takeIf { it > 0 },
                )
            }
        } catch (e: RuntimeException) {
            // 分享方的 ContentProvider 不支持查询时，只能显示照片本身的信息
            null
        } ?: FileInfo()
        if (info.mimeType != null) info else info.copy(mimeType = resolver.getType(uri))
    }

    companion object {
        private val COLLECTION: Uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

        /** 能解码处理的格式。RAW（比如尼康的 NEF）不在其中。 */
        private val SUPPORTED_MIME_TYPES = arrayOf(
            "image/jpeg",
            "image/heic",
            "image/heif",
            "image/png",
            "image/webp",
            "image/avif",
        )

        fun buckets(images: List<MediaImage>): List<MediaBucket> =
            images.groupBy { it.bucketId }
                .map { (id, items) -> MediaBucket(id, items.first().bucketName, items.size) }
                .sortedByDescending { it.count }
    }
}
