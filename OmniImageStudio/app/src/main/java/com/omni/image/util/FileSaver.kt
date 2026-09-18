package com.omni.image.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.omni.image.engine.ConvertEngine
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileSaver {

    fun outputDir(context: Context): File {
        return File(context.getExternalFilesDir(null), "output").apply { mkdirs() }
    }

    fun uniqueName(prefix: String, ext: String): String {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "${prefix}_$ts.$ext"
    }

    fun saveBitmap(context: Context, bitmap: Bitmap, format: String, quality: Int, prefix: String): File {
        val ext = ConvertEngine.outputExtension(format)
        val file = File(outputDir(context), uniqueName(prefix, ext))
        ConvertEngine.encode(bitmap, format, file, quality)
        return file
    }

    fun saveText(context: Context, text: String, ext: String, prefix: String): File {
        val file = File(outputDir(context), uniqueName(prefix, ext))
        file.writeText(text)
        return file
    }

    /** 文本（SVG/XML）也输出到源文件夹，重名自动追加序号。 */
    fun saveTextToSource(context: Context, sourceUri: Uri?, text: String, ext: String, prefix: String): SaveResult {
        val src = sourceUri?.let { resolveSource(context, it) }
        if (src != null && src.relativePath != null) {
            val name = uniqueInSourceDir(context, src, ext)
            val inserted = writeTextViaMediaStore(context, text, src.relativePath, name, ext)
            if (inserted != null) {
                return SaveResult(name, inserted, null, true)
            }
        }
        val file = File(outputDir(context), uniqueName(prefix, ext))
        file.writeText(text)
        return SaveResult(file.name, null, file, false)
    }

    private fun writeTextViaMediaStore(
        context: Context,
        text: String,
        relPath: String,
        displayName: String,
        ext: String
    ): Uri? {
        return runCatching {
            val mime = if (ext == "svg") "image/svg+xml" else "text/xml"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.IS_PENDING, 1)
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, relPath)
                }
            }
            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val uri = context.contentResolver.insert(collection, values) ?: return null
            val ok = context.contentResolver.openOutputStream(uri, "w")?.use { os ->
                os.write(text.toByteArray())
            } != null
            if (ok) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            } else {
                context.contentResolver.delete(uri, null, null)
                return null
            }
            uri
        }.getOrNull()
    }

    fun shareFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "com.omni.image.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (file.extension == "svg") "image/svg+xml" else "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享 ${file.name}"))
    }

    /** 保存结果：content uri（写回源文件夹时）或本地文件（回退时）。 */
    data class SaveResult(
        val displayName: String,
        val contentUri: Uri?,
        val file: File?,
        val savedToSource: Boolean
    ) {
        val description: String
            get() = if (savedToSource) "已保存到原图文件夹: $displayName"
            else "已保存: ${file?.absolutePath} (${(file?.length() ?: 0) / 1024} KB)"
    }

    fun shareUri(context: Context, result: SaveResult) {
        val uri = result.contentUri ?: result.file?.let {
            FileProvider.getUriForFile(context, "com.omni.image.fileprovider", it)
        } ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享 ${result.displayName}"))
    }

    /**
     * 输出到源图片所在文件夹，并在重名时自动追加序号（name.png -> name1.png -> name2.png）。
     * sourceUri 为 null 或无法解析目录时回退到私有 output 目录。
     */
    fun saveBitmapToSource(
        context: Context,
        sourceUri: Uri?,
        bitmap: Bitmap,
        format: String,
        quality: Int,
        prefix: String = ""
    ): SaveResult {
        val ext = ConvertEngine.outputExtension(format)
        val src = sourceUri?.let { resolveSource(context, it) }

        if (src != null && src.relativePath != null) {
            val name = uniqueInSourceDir(context, src, ext)
            val inserted = writeViaMediaStore(context, bitmap, format, quality, src.relativePath, name)
            if (inserted != null) {
                return SaveResult(name, inserted, null, true)
            }
        }

        val file = File(outputDir(context), uniqueName(prefix.ifEmpty { "output" }, ext))
        ConvertEngine.encode(bitmap, format, file, quality)
        return SaveResult(file.name, null, file, false)
    }

    private data class SourceInfo(
        val baseName: String,
        val relativePath: String?
    )

    private fun resolveSource(context: Context, uri: Uri): SourceInfo? {
        return runCatching {
            val isMedia = uri.scheme == "content" && (uri.authority == "media" ||
                uri.toString().contains("/media/external/images/"))
            if (!isMedia) {
                return@runCatching SourceInfo(nameFromUri(uri), null)
            }
            var base = nameFromUri(uri)
            var relPath: String? = null
            context.contentResolver.query(
                uri,
                arrayOf(
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.RELATIVE_PATH,
                    MediaStore.Images.Media.DATA
                ),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { if (it.isNotBlank()) base = it.substringBeforeLast('.', it) }
                    relPath = c.getString(1)
                    if (relPath.isNullOrBlank() && Build.VERSION.SDK_INT < 29) {
                        c.getString(2)?.let { path ->
                            File(path).parentFile?.absolutePath?.let { dir ->
                                val idx = dir.indexOf("/DCIM/")
                                if (idx >= 0) relPath = dir.substring(idx + 1)
                                else relPath = null
                            }
                        }
                    }
                }
            }
            SourceInfo(base, relPath)
        }.getOrNull() ?: uri.let { SourceInfo(nameFromUri(it), null) }
    }

    private fun nameFromUri(uri: Uri): String {
        val seg = uri.lastPathSegment ?: "image"
        return seg.substringBeforeLast('.', seg).ifBlank { "image" }
    }

    private fun uniqueInSourceDir(context: Context, src: SourceInfo, ext: String): String {
        val rel = src.relativePath ?: return "${src.baseName}.$ext"
        val base = src.baseName.ifBlank { "image" }
        var candidate = "$base.$ext"
        var idx = 1
        while (fileExistsInDir(context, rel, candidate)) {
            candidate = "$base$idx.$ext"
            idx++
        }
        return candidate
    }

    private fun fileExistsInDir(context: Context, relPath: String, displayName: String): Boolean {
        return runCatching {
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val selection = "${MediaStore.Images.Media.RELATIVE_PATH}=? AND ${MediaStore.Images.Media.DISPLAY_NAME}=?"
            val args = arrayOf(relPath, displayName)
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, selection, args, null
            )?.use { c -> c.count > 0 } ?: false
        }.getOrDefault(false)
    }

    private fun writeViaMediaStore(
        context: Context,
        bitmap: Bitmap,
        format: String,
        quality: Int,
        relPath: String,
        displayName: String
    ): Uri? {
        return runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeFor(format))
                put(MediaStore.Images.Media.IS_PENDING, 1)
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, relPath)
                }
            }
            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val uri = context.contentResolver.insert(collection, values) ?: return null
            val ok = context.contentResolver.openOutputStream(uri, "w")?.use { os ->
                if (format == "BMP" || format == "ICO") {
                    writeManualFormat(bitmap, format, os)
                } else {
                    bitmap.compress(ConvertEngine.compressFormat(format) ?: Bitmap.CompressFormat.PNG, quality, os)
                }
            } ?: false
            if (ok) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            } else {
                context.contentResolver.delete(uri, null, null)
                return null
            }
            uri
        }.getOrNull()
    }

    private fun writeManualFormat(bitmap: Bitmap, format: String, os: java.io.OutputStream): Boolean {
        return try {
            val tmp = File.createTempFile("fmt", ".tmp")
            val ok = ConvertEngine.encode(bitmap, format, tmp, 100)
            if (ok) tmp.inputStream().use { it.copyTo(os) }
            tmp.delete()
            ok
        } catch (e: Exception) {
            false
        }
    }

    private fun mimeFor(format: String): String {
        return when (format) {
            "JPEG", "JPG" -> "image/jpeg"
            "WEBP" -> "image/webp"
            "BMP" -> "image/bmp"
            "ICO" -> "image/x-icon"
            else -> "image/png"
        }
    }
}
