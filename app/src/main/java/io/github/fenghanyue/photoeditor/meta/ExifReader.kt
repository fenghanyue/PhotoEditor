package io.github.fenghanyue.photoeditor.meta

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import io.github.fenghanyue.photoeditor.media.MediaPermissions

/** 从 content:// 地址读照片自带的信息，并判断位置是否可能被系统藏起来了。 */
class ExifReader(private val context: Context) {

    fun read(uri: Uri, fileDateTakenMillis: Long?): PhotoReadResult {
        val fromMediaStore = uri.authority == MediaStore.AUTHORITY
        val canReadLocation = MediaPermissions.canReadLocation(context)
        // 从媒体库读照片时，要明确要求"原图"，GPS 才不会被抹掉
        val original = if (fromMediaStore && canReadLocation) open(MediaStore.setRequireOriginal(uri)) else null
        val exif = original ?: open(uri)
        val meta = ExifMetaParser.parse(exif, fileDateTakenMillis)
        val status = when {
            meta.gps != null -> LocationStatus.PRESENT
            fromMediaStore && !canReadLocation -> LocationStatus.NO_PERMISSION
            original != null -> LocationStatus.ABSENT
            else -> LocationStatus.UNVERIFIED
        }
        return PhotoReadResult(meta, status)
    }

    private fun open(uri: Uri): ExifInterface? = try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { ExifInterface(it.fileDescriptor) }
    } catch (e: Exception) {
        // 文件被删、权限不够、格式不支持等情况都会走到这里，当作照片里没有拍摄信息
        null
    }
}
