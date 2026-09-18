package com.omni.image.engine

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File

object ExifEngine {

    val READ_TAGS: List<Pair<String, String>> = listOf(
        "Make" to "相机品牌",
        "Model" to "相机型号",
        "LensMake" to "镜头品牌",
        "LensModel" to "镜头型号",
        "FNumber" to "光圈",
        "ExposureTime" to "曝光时间",
        "PhotographicSensitivity" to "ISO",
        "FocalLength" to "焦距",
        "DateTime" to "修改时间",
        "DateTimeOriginal" to "拍摄时间",
        "DateTimeDigitized" to "数字化时间",
        "WhiteBalance" to "白平衡",
        "Flash" to "闪光灯",
        "Orientation" to "方向",
        "Software" to "软件",
        "Artist" to "作者",
        "Copyright" to "版权",
        "ImageDescription" to "描述",
        "ImageLength" to "高度",
        "ImageWidth" to "宽度",
        "GPSLatitude" to "GPS 纬度",
        "GPSLongitude" to "GPS 经度",
        "GPSAltitude" to "GPS 海拔",
        "GPSDateTime" to "GPS 时间"
    )

    private val TAG_KEYS = READ_TAGS.map { it.first }.toSet() + setOf(
        "GPSLatitudeRef", "GPSLongitudeRef", "GPSAltitudeRef", "GPSSpeed",
        "GPSImgDirection", "GPSDestLatitude", "GPSDestLongitude"
    )

    fun copyToCache(context: Context, uri: Uri): File? {
        return runCatching {
            val name = (uri.lastPathSegment ?: "exif_${System.currentTimeMillis()}").substringAfterLast('/')
            val cache = File(context.cacheDir, name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                cache.outputStream().use { out -> input.copyTo(out) }
            }
            cache
        }.getOrNull()
    }

    fun readAll(context: Context, uri: Uri): Map<String, String> {
        return try {
            val file = copyToCache(context, uri) ?: return emptyMap()
            val exif = ExifInterface(file.absolutePath)
            val out = LinkedHashMap<String, String>()
            for ((tag, label) in READ_TAGS) {
                val v = exif.getAttribute(tag)
                if (!v.isNullOrEmpty()) out[label] = v
            }
            out
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun getRawTags(context: Context, uri: Uri): Map<String, String> {
        return try {
            val file = copyToCache(context, uri) ?: return emptyMap()
            val exif = ExifInterface(file.absolutePath)
            val out = LinkedHashMap<String, String>()
            for (tag in TAG_KEYS) {
                val v = exif.getAttribute(tag)
                if (!v.isNullOrEmpty()) out[tag] = v
            }
            out
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun writeFields(context: Context, uri: Uri, fields: Map<String, String>): Boolean {
        return try {
            val file = copyToCache(context, uri) ?: return false
            val exif = ExifInterface(file.absolutePath)
            for ((tag, value) in fields) {
                if (tag in TAG_KEYS) {
                    exif.setAttribute(tag, value)
                }
            }
            exif.saveAttributes()
            copyBack(context, uri, file)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun clearGps(context: Context, uri: Uri): Boolean {
        return try {
            val file = copyToCache(context, uri) ?: return false
            val exif = ExifInterface(file.absolutePath)
            val gpsTags = listOf(
                "GPSLatitude", "GPSLatitudeRef", "GPSLongitude", "GPSLongitudeRef",
                "GPSAltitude", "GPSAltitudeRef", "GPSDateTime", "GPSProcessingMethod",
                "GPSDateStamp", "GPSTimeStamp", "GPSSpeed", "GPSImgDirection",
                "GPSDestLatitude", "GPSDestLongitude"
            )
            for (t in gpsTags) {
                runCatching { exif.setAttribute(t, "") }
            }
            exif.saveAttributes()
            copyBack(context, uri, file)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun clearAll(context: Context, uri: Uri): Boolean {
        return try {
            val file = copyToCache(context, uri) ?: return false
            val exif = ExifInterface(file.absolutePath)
            for (tag in TAG_KEYS) {
                runCatching { exif.setAttribute(tag, "") }
            }
            exif.saveAttributes()
            copyBack(context, uri, file)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun copyBack(context: Context, uri: Uri, file: File): Boolean {
        return runCatching {
            context.contentResolver.openOutputStream(uri, "w")?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            } ?: return false
            true
        }.getOrDefault(false)
    }
}
