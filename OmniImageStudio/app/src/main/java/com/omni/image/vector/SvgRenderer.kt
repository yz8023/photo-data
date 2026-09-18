package com.omni.image.vector

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.max

object SvgRenderer {

    fun render(svg: String, width: Int, height: Int): Bitmap? {
        val shapes = SvgParser.parse(svg)
        if (shapes.isEmpty()) return null
        return renderShapes(shapes, width, height)
    }

    fun renderShapes(shapes: List<SvgShape>, width: Int, height: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
        for (s in shapes) {
            if (s.fill != null && s.fillOpacity > 0f) {
                fillPaint.color = s.fill
                fillPaint.alpha = (255 * s.fillOpacity).toInt().coerceIn(0, 255)
                canvas.drawPath(s.path, fillPaint)
            }
            if (s.stroke != null && s.strokeWidth > 0f) {
                strokePaint.color = s.stroke
                strokePaint.strokeWidth = s.strokeWidth
                canvas.drawPath(s.path, strokePaint)
            }
        }
        return bmp
    }

    fun fitBounds(svg: String, maxSize: Int): Pair<Int, Int> {
        val vb = SvgParser.viewBox(svg)
        val baseW = vb?.first ?: 512
        val baseH = vb?.second ?: 512
        if (baseW <= 0 || baseH <= 0) return 512 to 512
        val scale = maxSize.toFloat() / max(baseW, baseH)
        return (baseW * scale).toInt() to (baseH * scale).toInt()
    }
}
