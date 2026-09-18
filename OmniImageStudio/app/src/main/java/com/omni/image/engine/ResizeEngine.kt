package com.omni.image.engine

import android.graphics.Bitmap

object ResizeEngine {

    fun resize(src: Bitmap, width: Int, height: Int, keepRatio: Boolean): Bitmap {
        if (width <= 0 || height <= 0) return src
        val (w, h) = if (keepRatio) {
            val ratio = src.width.toFloat() / src.height
            var w2 = width
            var h2 = (w2 / ratio).toInt()
            if (h2 > height) {
                h2 = height
                w2 = (h2 * ratio).toInt()
            }
            w2 to h2
        } else width to height
        if (w <= 0 || h <= 0) return src
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    fun fitToFileSize(
        src: Bitmap,
        format: String,
        targetKb: Int,
        startQuality: Int = 92
    ): Pair<Bitmap, Int> {
        if (targetKb <= 0) return src to startQuality
        var bmp = src
        var quality = startQuality
        var bytes = ConvertEngine.encodeBytes(bmp, format, quality) ?: return src to quality

        while (bytes.size > targetKb * 1024 && quality > 40) {
            quality -= 8
            bytes = ConvertEngine.encodeBytes(bmp, format, quality) ?: break
        }
        var guard = 0
        while (bytes.size > targetKb * 1024 && guard < 6) {
            val f = 0.9f
            bmp = Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * f).toInt().coerceAtLeast(1),
                (bmp.height * f).toInt().coerceAtLeast(1),
                true
            )
            bytes = ConvertEngine.encodeBytes(bmp, format, quality) ?: break
            guard++
        }
        return bmp to quality
    }
}
