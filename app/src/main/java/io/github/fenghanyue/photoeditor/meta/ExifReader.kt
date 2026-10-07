package io.github.fenghanyue.photoeditor.meta

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import io.github.fenghanyue.photoeditor.media.MediaPermissions

/** 从 content:// 地址读照片自带的信息，并判断位置是否可能被系统藏起来了。 */
class ExifReader(private val context: Context) {

    /**
     * @property exif 读不到时为 null
     * @property original 是否读到了原图（从媒体库读时，只有原图的 GPS 没被抹掉）
     * @property locationHidden 没有"读取照片位置"权限，系统会先抹掉 GPS
     */
    class Opened(val exif: ExifInterface?, val original: Boolean, val locationHidden: Boolean)

    fun openBest(uri: Uri): Opened {
        val fromMediaStore = uri.authority == MediaStore.AUTHORITY
        val canReadLocation = MediaPermissions.canReadLocation(context)
        // 从媒体库读照片时，要明确要求"原图"，GPS 才不会被抹掉
        val original = if (fromMediaStore && canReadLocation) open(MediaStore.setRequireOriginal(uri)) else null
        return Opened(
            exif = original ?: open(uri),
            original = original != null,
            locationHidden = fromMediaStore && !canReadLocation,
        )
    }

    fun read(uri: Uri, fileDateTakenMillis: Long?): PhotoReadResult {
        val opened = openBest(uri)
        val meta = ExifMetaParser.parse(opened.exif, fileDateTakenMillis)
        val status = when {
            meta.gps != null -> LocationStatus.PRESENT
            opened.locationHidden -> LocationStatus.NO_PERMISSION
            opened.original -> LocationStatus.ABSENT
            else -> LocationStatus.UNVERIFIED
        }
        return PhotoReadResult(meta, status)
    }

    private fun open(uri: Uri): ExifInterface? {
        val resolver = context.contentResolver
        // 优先用文件描述符：HEIF 之类的格式需要随机读取
        try {
            resolver.openFileDescriptor(uri, "r")?.use { return ExifInterface(it.fileDescriptor) }
        } catch (e: Exception) {
            // 有的地址给不出能随机读取的文件描述符，下面改用数据流再试一次
        }
        return try {
            resolver.openInputStream(uri)?.use { ExifInterface(it) }
        } catch (e: Exception) {
            // 文件被删、权限不够、格式不支持等情况都会走到这里，当作照片里没有拍摄信息
            null
        }
    }
}
