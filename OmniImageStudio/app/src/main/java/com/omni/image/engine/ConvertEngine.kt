package com.omni.image.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object ConvertEngine {

    val OUTPUT_FORMATS = listOf(
        "PNG", "JPEG", "WEBP", "BMP", "ICO"
    )

    val VECTOR_FORMATS = listOf("SVG", "XML_VECTOR")

    val NOT_YET_FORMATS = listOf(
        "GIF", "TIFF", "HEIC", "AVIF", "TGA", "PDF", "EPS"
    )

    fun detectMime(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.getType(uri) ?: guessMimeByPath(uri.lastPathSegment)
        }.getOrNull()
    }

    fun guessMimeByPath(name: String?): String? {
        if (name == null) return null
        return when (name.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "gif" -> "image/gif"
            "ico" -> "image/x-icon"
            "heic", "heif" -> "image/heic"
            "avif" -> "image/avif"
            "tiff", "tif" -> "image/tiff"
            "svg" -> "image/svg+xml"
            "xml" -> "text/xml"
            else -> null
        }
    }

    fun decodeUri(context: Context, uri: Uri, maxDim: Int = 4096): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= 28) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val sample = info.size.width.coerceAtLeast(info.size.height)
                    if (sample > maxDim) {
                        decoder.setTargetSampleSize(sample / maxDim + 1)
                    }
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                val sample = bounds.outWidth.coerceAtLeast(bounds.outHeight)
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = if (sample > maxDim) sample / maxDim + 1 else 1
                }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun decodeFile(path: String, maxDim: Int = 4096): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            val sample = bounds.outWidth.coerceAtLeast(bounds.outHeight)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = if (sample > maxDim) sample / maxDim + 1 else 1
            }
            BitmapFactory.decodeFile(path, opts)
        } catch (e: Exception) {
            null
        }
    }

    fun compressFormat(format: String): Bitmap.CompressFormat? {
        return when (format) {
            "PNG" -> Bitmap.CompressFormat.PNG
            "JPEG", "JPG" -> Bitmap.CompressFormat.JPEG
            "WEBP", "WEBP_LOSSY" -> Bitmap.CompressFormat.WEBP
            "WEBP_LOSSLESS" -> if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP
            else -> null
        }
    }

    fun encode(
        bitmap: Bitmap,
        format: String,
        outFile: File,
        quality: Int
    ): Boolean {
        return when (format) {
            "PNG", "JPEG", "WEBP" -> {
                val cf = compressFormat(format) ?: return false
                try {
                    FileOutputStream(outFile).use { fos ->
                        bitmap.compress(cf, quality, fos)
                    }
                    true
                } catch (e: Exception) {
                    false
                }
            }
            "BMP" -> writeBmp(bitmap, outFile)
            "ICO" -> writeIco(bitmap, outFile)
            else -> false
        }
    }

    fun encodeBytes(bitmap: Bitmap, format: String, quality: Int): ByteArray? {
        val cf = compressFormat(format) ?: return null
        return ByteArrayOutputStream().use { bos ->
            if (bitmap.compress(cf, quality, bos)) bos.toByteArray() else null
        }
    }

    fun writeBmp(bitmap: Bitmap, outFile: File): Boolean {
        return try {
            val w = bitmap.width
            val h = bitmap.height
            val rowSize = (w * 3 + 3) / 4 * 4
            val dataSize = rowSize * h
            val fileSize = 14 + 40 + dataSize
            FileOutputStream(outFile).use { out ->
                val head = ByteArrayOutputStream()
                head.write('B'.code); head.write('M'.code)
                writeLeInt(head, fileSize)
                writeLeInt(head, 0)
                writeLeInt(head, 14 + 40)
                writeLeInt(head, 40)
                writeLeInt(head, w)
                writeLeInt(head, h)
                head.write(1); head.write(0)
                head.write(24); head.write(0)
                writeLeInt(head, 0)
                writeLeInt(head, dataSize)
                writeLeInt(head, 2835)
                writeLeInt(head, 2835)
                writeLeInt(head, 0)
                writeLeInt(head, 0)
                head.writeTo(out)

                val pixels = IntArray(w * h)
                bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
                val row = ByteArray(rowSize)
                for (y in 0 until h) {
                    val srcY = h - 1 - y
                    for (x in 0 until w) {
                        val c = pixels[srcY * w + x]
                        row[x * 3] = (c and 0xFF).toByte()
                        row[x * 3 + 1] = ((c shr 8) and 0xFF).toByte()
                        row[x * 3 + 2] = ((c shr 16) and 0xFF).toByte()
                    }
                    out.write(row)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun writeIco(bitmap: Bitmap, outFile: File): Boolean {
        return try {
            val w = bitmap.width.coerceAtMost(256)
            val h = bitmap.height.coerceAtMost(256)
            val bmp = if (bitmap.width != w || bitmap.height != h) {
                Bitmap.createScaledBitmap(bitmap, w, h, true)
            } else bitmap
            val pixels = IntArray(w * h)
            bmp.getPixels(pixels, 0, w, 0, 0, w, h)
            val xorSize = w * h * 4
            val andRow = (w + 31) / 32 * 4
            val andSize = andRow * h
            val imgSize = 40 + xorSize + andSize

            FileOutputStream(outFile).use { out ->
                out.write(0); out.write(0); out.write(1); out.write(0)
                out.write(1); out.write(0)
                out.write(if (w >= 256) 0 else w); out.write(if (h >= 256) 0 else h)
                out.write(0); out.write(0)
                out.write(1); out.write(0)
                out.write(32); out.write(0)
                writeLeInt(out, imgSize)
                writeLeInt(out, 22)
                writeLeInt(out, 40)
                writeLeInt(out, w)
                writeLeInt(out, h * 2)
                out.write(1); out.write(0)
                out.write(32); out.write(0)
                writeLeInt(out, 0)
                writeLeInt(out, xorSize)
                writeLeInt(out, 0)
                writeLeInt(out, 0)
                writeLeInt(out, 0)
                writeLeInt(out, 0)

                val alphaMask = BooleanArray(w * h)
                for (y in 0 until h) {
                    for (x in 0 until w) {
                        val c = pixels[y * w + x]
                        out.write(c and 0xFF)
                        out.write((c shr 8) and 0xFF)
                        out.write((c shr 16) and 0xFF)
                        out.write((c shr 24) and 0xFF)
                        alphaMask[y * w + x] = ((c shr 24) and 0xFF) < 128
                    }
                }
                val andRowBytes = ByteArray(andRow)
                for (y in 0 until h) {
                    andRowBytes.fill(0)
                    for (x in 0 until w) {
                        if (alphaMask[y * w + x]) {
                            andRowBytes[x / 8] = (andRowBytes[x / 8].toInt() or (0x80 shr (x % 8))).toByte()
                        }
                    }
                    out.write(andRowBytes)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun writeStream(bitmap: Bitmap, format: String, os: OutputStream, quality: Int): Boolean {
        return when (format) {
            "PNG", "JPEG", "WEBP" -> bitmap.compress(compressFormat(format) ?: return false, quality, os)
            else -> false
        }
    }

    private fun writeLeInt(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
        out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF)
    }

    private fun writeLeInt(out: FileOutputStream, v: Int) {
        out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
        out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF)
    }

    fun outputExtension(format: String): String {
        return when (format) {
            "JPEG" -> "jpg"
            "XML_VECTOR" -> "xml"
            else -> format.lowercase()
        }
    }
}
