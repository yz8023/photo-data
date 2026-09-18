package com.omni.image.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.omni.image.engine.ConvertEngine
import java.io.File
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

    fun shareFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "com.omni.image.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (file.extension == "svg") "image/svg+xml" else "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享 ${file.name}"))
    }
}
