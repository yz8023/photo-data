package com.omni.image.engine

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Build
import com.omni.image.model.Layer

object LayerEngine {

    fun newCanvas(width: Int, height: Int, background: Int = android.graphics.Color.TRANSPARENT): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(background)
        return bmp
    }

    fun flatten(layers: List<Layer>, width: Int, height: Int, background: Int): Bitmap? {
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.eraseColor(background)
        val canvas = Canvas(result)
        for (layer in layers) {
            if (!layer.visible) continue
            val bmp = layer.bitmap ?: continue
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                alpha = (255 * layer.opacity.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
            }
            val mode = blendModeToXfermode(layer.blendMode)
            if (mode != null) {
                paint.xfermode = mode
            }
            canvas.drawBitmap(bmp, 0f, 0f, paint)
            paint.xfermode = null
        }
        return result
    }

    fun mergeLayers(layers: List<Layer>, targetIndex: Int): List<Layer> {
        if (layers.isEmpty()) return layers
        val idx = targetIndex.coerceIn(0, layers.size - 1)
        if (idx == 0) return layers
        val base = layers[idx - 1]
        val target = layers[idx]
        val baseBmp = base.bitmap
        val targetBmp = target.bitmap
        if (baseBmp == null || targetBmp == null) {
            return layers.toMutableList().also { it.removeAt(idx) }
        }
        val w = baseBmp.width.coerceAtLeast(targetBmp.width)
        val h = baseBmp.height.coerceAtLeast(targetBmp.height)
        val merged = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(merged)
        canvas.drawBitmap(baseBmp, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            alpha = (255 * target.opacity.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
        }
        canvas.drawBitmap(targetBmp, 0f, 0f, paint)
        val newList = layers.toMutableList()
        newList.removeAt(idx)
        newList[idx - 1] = base.copy(bitmap = merged)
        return newList
    }

    private fun blendModeToXfermode(mode: BlendMode): PorterDuffXfermode? {
        return when (mode) {
            BlendMode.SRC_OVER -> null
            BlendMode.MULTIPLY -> PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
            BlendMode.SCREEN -> PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            BlendMode.OVERLAY -> if (Build.VERSION.SDK_INT >= 29) PorterDuffXfermode(PorterDuff.Mode.OVERLAY) else null
            BlendMode.DARKEN -> PorterDuffXfermode(PorterDuff.Mode.DARKEN)
            BlendMode.LIGHTEN -> PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
            BlendMode.DIFFERENCE -> null
            BlendMode.PLUS -> PorterDuffXfermode(PorterDuff.Mode.ADD)
            else -> null
        }
    }
}
