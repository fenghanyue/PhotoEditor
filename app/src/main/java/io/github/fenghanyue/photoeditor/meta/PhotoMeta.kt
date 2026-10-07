package io.github.fenghanyue.photoeditor.meta

import java.time.LocalDateTime

/** 一个值是从哪来的。界面上会标出来，免得把来路不明的信息印到照片上。 */
enum class MetaSource {
    /** 照片自带的拍摄信息（EXIF）。 */
    EXIF,

    /** 照片自带的"修改时间"，不一定是拍摄时间。 */
    EXIF_MODIFIED,

    /** 系统媒体库记录的时间，可能是拷贝或下载的时间。 */
    FILE,
}

data class Sourced<T>(val value: T, val source: MetaSource)

data class GeoPoint(val latitude: Double, val longitude: Double, val altitudeMeters: Double?)

/** 照片自带的拍摄信息；照片里没有的字段为 null。 */
data class PhotoMeta(
    val dateTime: Sourced<LocalDateTime>? = null,
    val offsetTime: String? = null,
    val make: String? = null,
    val model: String? = null,
    val lensMake: String? = null,
    val lensModel: String? = null,
    val focalLengthMm: Double? = null,
    val focalLength35mm: Int? = null,
    val fNumber: Double? = null,
    val exposureTimeSec: Double? = null,
    val iso: Int? = null,
    val exposureBiasEv: Double? = null,
    val flashFired: Boolean? = null,
    val gps: GeoPoint? = null,
    /** 照片的方向标记要求显示时顺时针旋转的角度。 */
    val rotationDegrees: Int = 0,
    /** 详情页"原始标签"里列出的 (标签名, 原始值)。 */
    val rawTags: List<Pair<String, String>> = emptyList(),
)

/** 为什么有或者没有位置。 */
enum class LocationStatus {
    PRESENT,

    /** 已经按原图读取，照片里确实没有位置。 */
    ABSENT,

    /** 没有"读取照片位置"权限，系统可能已经把位置抹掉了。 */
    NO_PERMISSION,

    /** 没能读取原图（比如别的 App 分享来的），说不准是本来没有还是被删了。 */
    UNVERIFIED,
}

data class PhotoReadResult(val meta: PhotoMeta, val locationStatus: LocationStatus)
