package com.omni.image.engine

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object SymmetryBrush {

    enum class Mode(val label: String) {
        VERTICAL("垂直对称"),
        HORIZONTAL("水平对称"),
        QUADRANT("四象限"),
        RADIAL("径向 N 线"),
        KALEIDOSCOPE("万花筒")
    }

    data class Stroke(
        val points: List<PointF>,
        val paint: Paint,
        val width: Float,
        val color: Long,
        val softness: Float = 0f
    )

    fun buildStrokePath(points: List<PointF>): Path {
        val path = Path()
        if (points.isEmpty()) return path
        path.moveTo(points[0].x, points[0].y)
        if (points.size == 1) {
            path.lineTo(points[0].x + 0.01f, points[0].y + 0.01f)
            return path
        }
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val cur = points[i]
            val mx = (prev.x + cur.x) / 2f
            val my = (prev.y + cur.y) / 2f
            if (i == 1) {
                path.lineTo(mx, my)
            } else {
                path.quadTo(prev.x, prev.y, mx, my)
            }
        }
        path.lineTo(points.last().x, points.last().y)
        return path
    }

    fun buildPaint(width: Float, color: Long): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = width
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.color = color.toInt()
        }
    }

    fun drawSymmetry(
        canvas: Canvas,
        stroke: Stroke,
        mode: Mode,
        lines: Int,
        centerX: Float,
        centerY: Float,
        angleOffset: Float
    ) {
        val path = buildStrokePath(stroke.points)
        when (mode) {
            Mode.VERTICAL -> {
                val m = Matrix().apply { setScale(-1f, 1f, centerX, 0f) }
                drawWithMatrix(canvas, path, stroke, m)
            }
            Mode.HORIZONTAL -> {
                val m = Matrix().apply { setScale(1f, -1f, 0f, centerY) }
                drawWithMatrix(canvas, path, stroke, m)
            }
            Mode.QUADRANT -> {
                drawWithMatrix(canvas, path, stroke, Matrix().apply { setScale(-1f, 1f, centerX, 0f) })
                drawWithMatrix(canvas, path, stroke, Matrix().apply { setScale(1f, -1f, 0f, centerY) })
                drawWithMatrix(canvas, path, stroke, Matrix().apply { setScale(-1f, -1f, centerX, centerY) })
            }
            Mode.RADIAL, Mode.KALEIDOSCOPE -> {
                val n = lines.coerceAtLeast(2)
                val base = angleOffset * PI.toFloat() / 180f
                val step = 2f * PI.toFloat() / n
                for (i in 0 until n) {
                    val theta = base + i * step
                    val c = cos(theta.toDouble()).toFloat()
                    val s = sin(theta.toDouble()).toFloat()
                    val m = Matrix().apply {
                        setValues(floatArrayOf(c, -s, 0f, s, c, 0f, 0f, 0f, 1f))
                        postTranslate(-centerX, -centerY)
                        postScale(1f, 1f)
                        postTranslate(centerX, centerY)
                    }
                    drawWithMatrix(canvas, path, stroke, m)
                }
                if (mode == Mode.KALEIDOSCOPE) {
                    for (i in 0 until n) {
                        val theta = base + i * step
                        val c = cos(theta.toDouble()).toFloat()
                        val s = sin(theta.toDouble()).toFloat()
                        val mirror = Matrix().apply {
                            setValues(floatArrayOf(c, s, 0f, s, -c, 0f, 0f, 0f, 1f))
                            postTranslate(-centerX, -centerY)
                            postTranslate(centerX, centerY)
                        }
                        drawWithMatrix(canvas, path, stroke, mirror)
                    }
                }
            }
        }
    }

    private fun drawWithMatrix(canvas: Canvas, path: Path, stroke: Stroke, matrix: Matrix) {
        val paint = stroke.paint
        if (stroke.softness > 0f) {
            paint.strokeWidth = stroke.width
        }
        canvas.save()
        canvas.concat(matrix)
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    fun transformPoint(p: PointF, mode: Mode, lines: Int, centerX: Float, centerY: Float): PointF {
        val dx = p.x - centerX
        val dy = p.y - centerY
        val out = PointF()
        when (mode) {
            Mode.VERTICAL -> out.set(2 * centerX - p.x, p.y)
            Mode.HORIZONTAL -> out.set(p.x, 2 * centerY - p.y)
            Mode.QUADRANT -> out.set(2 * centerX - p.x, 2 * centerY - p.y)
            Mode.RADIAL, Mode.KALEIDOSCOPE -> {
                val angle = (360f / lines.coerceAtLeast(2)) * PI.toFloat() / 180f
                val c = cos(angle.toDouble()).toFloat()
                val s = sin(angle.toDouble()).toFloat()
                out.set(centerX + dx * c - dy * s, centerY + dx * s + dy * c)
            }
        }
        return out
    }
}
