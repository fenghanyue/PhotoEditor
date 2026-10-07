package io.github.fenghanyue.photoeditor.meta

import androidx.exifinterface.media.ExifInterface
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.abs

/** 把 ExifInterface 读到的标签整理成 PhotoMeta。 */
object ExifMetaParser {

    private val EXIF_DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

    /** 详情页"原始标签"里列出的标签。相机序列号之类的隐私信息不列。 */
    private val RAW_TAGS = listOf(
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_LENS_MAKE,
        ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
        ExifInterface.TAG_OFFSET_TIME,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
        ExifInterface.TAG_EXPOSURE_PROGRAM,
        ExifInterface.TAG_METERING_MODE,
        ExifInterface.TAG_FLASH,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
        ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.TAG_PIXEL_X_DIMENSION,
        ExifInterface.TAG_PIXEL_Y_DIMENSION,
        ExifInterface.TAG_SOFTWARE,
        ExifInterface.TAG_ARTIST,
        ExifInterface.TAG_COPYRIGHT,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_TIMESTAMP,
    )

    /**
     * @param fileDateTakenMillis 媒体库记录的时间，照片里没有拍摄时间时才用，并标明来源。
     */
    fun parse(
        exif: ExifInterface?,
        fileDateTakenMillis: Long?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): PhotoMeta {
        val fileTime = fileDateTakenMillis?.let {
            Sourced(LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone), MetaSource.FILE)
        }
        if (exif == null) return PhotoMeta(dateTime = fileTime)

        val dateTime = exif.dateTime(ExifInterface.TAG_DATETIME_ORIGINAL)?.let { Sourced(it, MetaSource.EXIF) }
            ?: exif.dateTime(ExifInterface.TAG_DATETIME_DIGITIZED)?.let { Sourced(it, MetaSource.EXIF) }
            ?: exif.dateTime(ExifInterface.TAG_DATETIME)?.let { Sourced(it, MetaSource.EXIF_MODIFIED) }
            ?: fileTime

        return PhotoMeta(
            dateTime = dateTime,
            offsetTime = exif.text(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)
                ?: exif.text(ExifInterface.TAG_OFFSET_TIME),
            make = exif.text(ExifInterface.TAG_MAKE),
            model = exif.text(ExifInterface.TAG_MODEL),
            lensMake = exif.text(ExifInterface.TAG_LENS_MAKE),
            lensModel = exif.text(ExifInterface.TAG_LENS_MODEL),
            focalLengthMm = exif.positiveDouble(ExifInterface.TAG_FOCAL_LENGTH),
            focalLength35mm = exif.positiveInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM),
            fNumber = exif.positiveDouble(ExifInterface.TAG_F_NUMBER),
            exposureTimeSec = exif.positiveDouble(ExifInterface.TAG_EXPOSURE_TIME),
            iso = exif.positiveInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY),
            exposureBiasEv = exif.finiteDouble(ExifInterface.TAG_EXPOSURE_BIAS_VALUE),
            flashFired = if (exif.hasAttribute(ExifInterface.TAG_FLASH)) {
                exif.getAttributeInt(ExifInterface.TAG_FLASH, 0) and 1 == 1
            } else {
                null
            },
            gps = exif.geoPoint(),
            rotationDegrees = exif.rotationDegrees,
            rawTags = RAW_TAGS.mapNotNull { tag ->
                // 照片没有方向标记时，ExifInterface 会自己补一个 "0"，不算照片自带的
                exif.text(tag)?.takeUnless { tag == ExifInterface.TAG_ORIENTATION && it == "0" }?.let { tag to it }
            },
        )
    }

    private fun ExifInterface.text(tag: String): String? =
        getAttribute(tag)?.trim { it.isWhitespace() || it == '\u0000' }?.takeIf { it.isNotEmpty() }

    private fun ExifInterface.dateTime(tag: String): LocalDateTime? {
        val text = text(tag) ?: return null
        // 没设置时钟的相机会写 "0000:00:00 00:00:00"，解析会失败，当作没有
        return try {
            LocalDateTime.parse(text.take(19), EXIF_DATE_TIME)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    private fun ExifInterface.finiteDouble(tag: String): Double? =
        getAttributeDouble(tag, Double.NaN).takeIf { it.isFinite() }

    private fun ExifInterface.positiveDouble(tag: String): Double? = finiteDouble(tag)?.takeIf { it > 0 }

    private fun ExifInterface.positiveInt(tag: String): Int? = getAttributeInt(tag, 0).takeIf { it > 0 }

    private fun ExifInterface.geoPoint(): GeoPoint? {
        val (latitude, longitude) = latLong ?: return null
        // 系统抹掉位置时会把坐标填成 0，赤道和本初子午线的交点上不会有人拍照
        if (latitude == 0.0 && longitude == 0.0) return null
        if (abs(latitude) > 90 || abs(longitude) > 180) return null
        val altitude = if (hasAttribute(ExifInterface.TAG_GPS_ALTITUDE)) {
            getAltitude(Double.NaN).takeIf { it.isFinite() }
        } else {
            null
        }
        return GeoPoint(latitude, longitude, altitude)
    }
}
