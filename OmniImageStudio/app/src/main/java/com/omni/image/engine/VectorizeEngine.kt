package com.omni.image.engine

import android.graphics.Bitmap

object VectorizeEngine {

    data class TraceOptions(
        val threshold: Int = 128,
        val colorLayers: Int = 1,
        val simplifyTolerance: Float = 0.5f,
        val maxDimension: Int = 256
    )

    fun vectorizeToSvg(bitmap: Bitmap, opts: TraceOptions = TraceOptions()): String {
        val layers = quantizeLayers(bitmap, opts.colorLayers, opts.threshold)
        val sb = StringBuilder()
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\"")
        sb.append(" width=\"${bitmap.width}\" height=\"${bitmap.height}\"")
        sb.append(" viewBox=\"0 0 ${bitmap.width} ${bitmap.height}\">\n")
        for ((color, grid) in layers) {
            val paths = traceContours(grid, bitmap.width, bitmap.height, opts.simplifyTolerance)
            if (paths.isEmpty()) continue
            val pathData = paths.joinToString(" ") { "M ${it.joinToString(" L ")} Z" }
            sb.append("  <path fill=\"#${hexColor(color)}\" d=\"${pathData}\" />\n")
        }
        sb.append("</svg>\n")
        return sb.toString()
    }

    fun vectorizeToVectorDrawable(bitmap: Bitmap, opts: TraceOptions = TraceOptions()): String {
        val layers = quantizeLayers(bitmap, opts.colorLayers, opts.threshold)
        val sb = StringBuilder()
        val w = bitmap.width
        val h = bitmap.height
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        sb.append("<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n")
        sb.append("    android:width=\"${w}dp\" android:height=\"${h}dp\"\n")
        sb.append("    android:viewportWidth=\"$w\" android:viewportHeight=\"$h\">\n")
        for ((color, grid) in layers) {
            val paths = traceContours(grid, bitmap.width, bitmap.height, opts.simplifyTolerance)
            if (paths.isEmpty()) continue
            val pathData = paths.joinToString(" ") { "M ${it.joinToString(" L ")} Z" }
            sb.append("    <path android:fillColor=\"#${hexColor(color)}\" android:pathData=\"$pathData\" />\n")
        }
        sb.append("</vector>\n")
        return sb.toString()
    }

    fun svgToVectorDrawableXml(svg: String): String? {
        val pathData = extractSvgPaths(svg) ?: return null
        val width = extractViewBox(svg)?.first ?: 512
        val height = extractViewBox(svg)?.second ?: 512
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        sb.append("<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n")
        sb.append("    android:width=\"${width}dp\" android:height=\"${height}dp\"\n")
        sb.append("    android:viewportWidth=\"$width\" android:viewportHeight=\"$height\">\n")
        for ((fill, d) in pathData) {
            sb.append("    <path android:fillColor=\"$fill\" android:pathData=\"$d\" />\n")
        }
        sb.append("</vector>\n")
        return sb.toString()
    }

    private fun extractSvgPaths(svg: String): List<Pair<String, String>>? {
        val out = mutableListOf<Pair<String, String>>()
        val re = Regex("<path([^>]*)>")
        for (m in re.findAll(svg)) {
            val attrs = m.groupValues[1]
            val d = Regex("d=\"([^\"]*)\"").find(attrs)?.groupValues?.get(1) ?: continue
            val fill = Regex("fill=\"([^\"]*)\"").find(attrs)?.groupValues?.get(1) ?: "#000000"
            out.add(fill to d)
        }
        return out.ifEmpty { null }
    }

    private fun extractViewBox(svg: String): Pair<Int, Int>? {
        val m = Regex("viewBox=\"[^\\s]+\\s+[^\\s]+\\s+([^\\s]+)\\s+([^\"]+)\"").find(svg) ?: return null
        val w = m.groupValues[1].toIntOrNull() ?: return null
        val h = m.groupValues[2].toIntOrNull() ?: return null
        return w to h
    }

    private fun hexColor(argb: Int): String {
        return String.format("%06X", argb and 0xFFFFFF)
    }

    private fun quantizeLayers(
        bitmap: Bitmap,
        layers: Int,
        threshold: Int
    ): List<Pair<Int, BooleanArray>> {
        val n = layers.coerceAtLeast(1).coerceAtMost(8)
        if (n <= 1) {
            val grid = toBinary(bitmap, threshold)
            return listOf(0xFF000000.toInt() to grid)
        }
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val centers = IntArray(n)
        val minC = pixels.minOrNull() ?: 0
        val maxC = pixels.maxOrNull() ?: 0xFF
        for (i in 0 until n) {
            centers[i] = minC + (maxC - minC) * i / n
        }
        for (iter in 0 until 6) {
            val sums = LongArray(n)
            val counts = IntArray(n)
            for (p in pixels) {
                var best = 0
                var bestD = Int.MAX_VALUE
                for (i in 0 until n) {
                    val d = kotlin.math.abs((p and 0xFF) - (centers[i] and 0xFF))
                    if (d < bestD) {
                        bestD = d
                        best = i
                    }
                }
                sums[best] = sums[best] + (p and 0xFF)
                counts[best] = counts[best] + 1
            }
            for (i in 0 until n) {
                if (counts[i] > 0) centers[i] = (sums[i] / counts[i]).toInt()
            }
        }
        val result = mutableListOf<Pair<Int, BooleanArray>>()
        for (i in 0 until n) {
            val grid = BooleanArray(w * h)
            for (idx in pixels.indices) {
                var best = 0
                var bestD = Int.MAX_VALUE
                for (j in 0 until n) {
                    val d = kotlin.math.abs((pixels[idx] and 0xFF) - (centers[j] and 0xFF))
                    if (d < bestD) {
                        bestD = d
                        best = j
                    }
                }
                if (best == i) grid[idx] = true
            }
            val l = centers[i] and 0xFF
            val color = 0xFF000000.toInt() or (l shl 16) or (l shl 8) or l
            result.add(color to grid)
        }
        return result
    }

    private fun toBinary(bitmap: Bitmap, threshold: Int): BooleanArray {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val grid = BooleanArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val lum = (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
            grid[i] = lum < threshold
        }
        return grid
    }

    fun traceContours(binary: BooleanArray, w: Int, h: Int, tolerance: Float): List<List<String>> {
        val visited = HashSet<Long>()
        val contours = mutableListOf<List<String>>()

        for (y in 0 until h) {
            for (x in 0 until w) {
                val isInside = binary[y * w + x]
                if (!isInside) continue

                checkEdge(visited, x, y, 0, binary, w, h, contours, tolerance)
                checkEdge(visited, x, y, 2, binary, w, h, contours, tolerance)
                if (y == 0) {
                    checkEdge(visited, x, y, 3, binary, w, h, contours, tolerance)
                }
                if (y == h - 1) {
                    checkEdge(visited, x, y, 1, binary, w, h, contours, tolerance)
                }
            }
        }
        return contours
    }

    private fun checkEdge(
        visited: MutableSet<Long>,
        x: Int, y: Int, dir: Int,
        binary: BooleanArray, w: Int, h: Int,
        contours: MutableList<List<String>>,
        tolerance: Float
    ) {
        val key = edgeKey(x, y, dir)
        if (visited.contains(key)) return
        val neighborInside = when (dir) {
            0 -> y > 0 && binary[(y - 1) * w + x]
            1 -> y < h - 1 && binary[(y + 1) * w + x]
            2 -> x < w - 1 && binary[y * w + x + 1]
            3 -> x > 0 && binary[y * w + x - 1]
            else -> false
        }
        if (neighborInside) return

        visited.add(key)
        val pts = ArrayList<FloatArray>()
        var cx = x
        var cy = y
        var cdir = dir
        var guard = 0
        while (guard < w * h * 4) {
            val inside = binary[cy * w + cx]
            val nextDir = when (cdir) {
                0 -> if (inside) 3 else 1
                1 -> if (inside) 0 else 2
                2 -> if (inside) 1 else 3
                else -> if (inside) 2 else 0
            }
            when (cdir) {
                0 -> { pts.add(floatArrayOf(cx + 0.5f, cy.toFloat())); cy-- }
                1 -> { pts.add(floatArrayOf(cx + 0.5f, cy + 1f)); cy++ }
                2 -> { pts.add(floatArrayOf(cx.toFloat(), cy + 0.5f)); cx++ }
                3 -> { pts.add(floatArrayOf(cx + 1f, cy + 0.5f)); cx-- }
            }
            cdir = nextDir

            val nk = edgeKey(cx, cy, cdir)
            if (nk == key || visited.contains(nk)) break
            visited.add(nk)
            guard++
        }
        if (pts.size >= 3) {
            contours.add(simplifyAndFormat(pts, tolerance))
        }
    }

    private fun edgeKey(x: Int, y: Int, dir: Int): Long {
        return (x.toLong() shl 34) or (y.toLong() shl 4) or dir.toLong()
    }

    private fun simplifyAndFormat(pts: List<FloatArray>, tolerance: Float): List<String> {
        val simplified = if (tolerance <= 0f) pts else douglasPeucker(pts, tolerance)
        return simplified.map { "${fmt(it[0])} ${fmt(it[1])}" }
    }

    private fun douglasPeucker(pts: List<FloatArray>, tol: Float): List<FloatArray> {
        if (pts.size < 3) return pts
        var maxD = 0f
        var idx = -1
        val a = pts[0]
        val b = pts[pts.size - 1]
        val dx = b[0] - a[0]
        val dy = b[1] - a[1]
        val len2 = dx * dx + dy * dy
        for (i in 1 until pts.size - 1) {
            val d = if (len2 == 0f) {
                kotlin.math.hypot((pts[i][0] - a[0]).toDouble(), (pts[i][1] - a[1]).toDouble()).toFloat()
            } else {
                val t = (((pts[i][0] - a[0]) * dx + (pts[i][1] - a[1]) * dy) / len2).coerceIn(0f, 1f)
                val projX = a[0] + t * dx
                val projY = a[1] + t * dy
                kotlin.math.hypot((pts[i][0] - projX).toDouble(), (pts[i][1] - projY).toDouble()).toFloat()
            }
            if (d > maxD) {
                maxD = d
                idx = i
            }
        }
        return if (maxD > tol && idx > 0) {
            val left = douglasPeucker(pts.subList(0, idx + 1), tol)
            val right = douglasPeucker(pts.subList(idx, pts.size), tol)
            left.subList(0, left.size - 1) + right
        } else {
            listOf(a, b)
        }
    }

    private fun fmt(v: Float): String {
        return if (v == v.toInt().toFloat()) v.toInt().toString() else String.format("%.1f", v)
    }
}
