package com.omni.image.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

object WatermarkEngine {

    val POSITIONS = listOf(
        "topLeft" to "左上", "topCenter" to "上中", "topRight" to "右上",
        "centerLeft" to "左中", "center" to "正中", "centerRight" to "右中",
        "bottomLeft" to "左下", "bottomCenter" to "下中", "bottomRight" to "右下"
    )

    data class WatermarkStyle(
        val text: String,
        val color: Long,
        val sizePx: Float,
        val rotation: Float,
        val opacity: Float,
        val shadow: Boolean,
        val typeface: Typeface? = null
    )

    fun applyText(src: Bitmap, style: WatermarkStyle, position: String): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = style.color.toInt()
            textSize = style.sizePx
            alpha = (255 * style.opacity.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
            this.typeface = style.typeface
        }
        val bounds = android.graphics.Rect()
        paint.getTextBounds(style.text, 0, style.text.length, bounds)
        val wText = paint.measureText(style.text)
        val hText = bounds.height().toFloat()

        if (style.shadow) {
            paint.setShadowLayer(8f, 2f, 2f, Color.argb(160, 0, 0, 0))
        }

        val (x, y) = positionOffset(position, out.width, out.height, wText, hText)

        if (style.rotation != 0f) {
            canvas.save()
            canvas.rotate(style.rotation, x + wText / 2, y - hText / 2)
            canvas.drawText(style.text, x, y, paint)
            canvas.restore()
        } else {
            canvas.drawText(style.text, x, y, paint)
        }
        return out
    }

    fun applyImage(src: Bitmap, watermark: Bitmap, opacity: Float, position: String, rotation: Float): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val scale = (out.width * 0.2f / watermark.width).coerceIn(0.1f, 0.4f)
        val wmW = (watermark.width * scale).toInt()
        val wmH = (watermark.height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(watermark, wmW, wmH, true)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (255 * opacity.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
        }
        val (x, y) = positionOffset(position, out.width, out.height, wmW.toFloat(), wmH.toFloat())
        canvas.save()
        canvas.rotate(rotation, x + wmW / 2f, y + wmH / 2f)
        canvas.drawBitmap(scaled, x, y, paint)
        canvas.restore()
        scaled.recycle()
        return out
    }

    fun applyTileText(src: Bitmap, style: WatermarkStyle, gap: Int): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = style.color.toInt()
            textSize = style.sizePx
            alpha = (255 * style.opacity.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
            this.typeface = style.typeface
        }
        if (style.shadow) {
            paint.setShadowLayer(6f, 2f, 2f, Color.argb(140, 0, 0, 0))
        }
        val wText = paint.measureText(style.text)
        val bounds = android.graphics.Rect()
        paint.getTextBounds(style.text, 0, style.text.length, bounds)
        val hText = bounds.height().toFloat()
        canvas.save()
        canvas.rotate(style.rotation)
        val step = gap.coerceAtLeast(50).toFloat()
        var y = -hText
        while (y < out.height + hText) {
            var x = -wText
            while (x < out.width + wText) {
                canvas.drawText(style.text, x, y, paint)
                x += wText + step
            }
            y += hText + step
        }
        canvas.restore()
        return out
    }

    fun applyTileImage(src: Bitmap, watermark: Bitmap, opacity: Float, gap: Int): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val scale = (out.width * 0.15f / watermark.width).coerceIn(0.08f, 0.25f)
        val wmW = (watermark.width * scale).toInt()
        val wmH = (watermark.height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(watermark, wmW, wmH, true)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (255 * opacity.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
        }
        val step = gap.coerceAtLeast(50).toFloat()
        var y = 0f
        while (y < out.height) {
            var x = 0f
            while (x < out.width) {
                canvas.drawBitmap(scaled, x, y, paint)
                x += wmW + step
            }
            y += wmH + step
        }
        scaled.recycle()
        return out
    }

    fun positionOffset(position: String, canvasW: Int, canvasH: Int, itemW: Float, itemH: Float): Pair<Float, Float> {
        val margin = 24f
        val x = when (position) {
            "topLeft", "centerLeft", "bottomLeft" -> margin
            "topCenter", "center", "bottomCenter" -> (canvasW - itemW) / 2f
            else -> canvasW - itemW - margin
        }
        val y = when (position) {
            "topLeft", "topCenter", "topRight" -> margin + itemH
            "centerLeft", "center", "centerRight" -> (canvasH + itemH) / 2f
            else -> canvasH - margin
        }
        return x to y
    }
}
