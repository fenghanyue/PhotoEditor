package io.github.fenghanyue.photoeditor.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** App 能看到多少照片。 */
enum class MediaAccess { FULL, PARTIAL, NONE }

object MediaPermissions {

    /** 需要一起申请的权限：读照片 + 读照片里的位置。不同系统版本的权限名不一样。 */
    fun toRequest(): Array<String> = buildList {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                add(Manifest.permission.READ_MEDIA_IMAGES)
            else -> add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        add(Manifest.permission.ACCESS_MEDIA_LOCATION)
    }.toTypedArray()

    fun access(context: Context): MediaAccess = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.granted(Manifest.permission.READ_MEDIA_IMAGES) -> MediaAccess.FULL
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            context.granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> MediaAccess.PARTIAL
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
            context.granted(Manifest.permission.READ_EXTERNAL_STORAGE) -> MediaAccess.FULL
        else -> MediaAccess.NONE
    }

    /** 有这个权限才能读到照片里的 GPS；没有的话，系统会在交给 App 之前把位置抹掉。 */
    fun canReadLocation(context: Context): Boolean =
        context.granted(Manifest.permission.ACCESS_MEDIA_LOCATION)

    private fun Context.granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
