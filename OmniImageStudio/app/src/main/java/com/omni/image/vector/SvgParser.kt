package com.omni.image.vector

import android.graphics.Path
import java.util.Locale

object SvgParser {

    fun parse(svg: String): List<SvgShape> {
        val shapes = mutableListOf<SvgShape>()
        parseElements(svg).forEach { el ->
            val attrs = el.second
            val kind = el.first
            val path = when (kind) {
                "path" -> parsePathData(attrs["d"] ?: "")
                "rect" -> parseRect(attrs)
                "circle" -> parseCircle(attrs)
                "ellipse" -> parseEllipse(attrs)
                "line" -> parseLine(attrs)
                "polygon", "polyline" -> parsePoly(attrs)
                else -> null
            }
            if (path != null) {
                val fillHex = attrs["fill"]?.let { normalizeColor(it) } ?: "#000000"
                val fill = parseColor(fillHex)
                val stroke = attrs["stroke"]?.let { parseColor(normalizeColor(it)) }
                val strokeWidth = attrs["stroke-width"]?.toFloatOrNull() ?: 1f
                val fillOpacity = attrs["fill-opacity"]?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f
                shapes.add(
                    SvgShape(
                        path = path,
                        fill = fill,
                        stroke = stroke,
                        strokeWidth = strokeWidth,
                        fillOpacity = fillOpacity,
                        fillColorHex = fillHex
                    )
                )
            }
        }
        return shapes
    }

    fun viewBox(svg: String): Pair<Int, Int>? {
        return Regex("viewBox=\"([^\"]*)\"").find(svg)?.let { m ->
            val parts = m.groupValues[1].trim().split(Regex("[\\s,]+"))
            if (parts.size >= 4) {
                val w = parts[2].toFloatOrNull()
                val h = parts[3].toFloatOrNull()
                if (w != null && h != null) w.toInt() to h.toInt() else null
            } else null
        }
    }

    private fun parseElements(svg: String): List<Pair<String, Map<String, String>>> {
        val out = mutableListOf<Pair<String, Map<String, String>>>()
        val re = Regex("<(path|rect|circle|ellipse|line|polygon|polyline)([^>]*)>")
        for (m in re.findAll(svg)) {
            val attrs = mutableMapOf<String, String>()
            val body = m.groupValues[2]
            val attrRe = Regex("([a-zA-Z0-9-]+)=\"([^\"]*)\"")
            for (a in attrRe.findAll(body)) {
                attrs[a.groupValues[1]] = a.groupValues[2]
            }
            out.add(m.groupValues[1] to attrs)
        }
        return out
    }

    fun parsePathData(d: String): Path {
        val path = Path()
        val tokens = tokenize(d)
        var i = 0
        var cx = 0f
        var cy = 0f
        var startX = 0f
        var startY = 0f
        var lastCmd = 'M'
        var lastControlX = 0f
        var lastControlY = 0f

        fun next(): Float? {
            if (i >= tokens.size) return null
            val v = tokens[i].toFloatOrNull()
            if (v != null) i++
            return v
        }

        fun peekIsNumber(): Boolean {
            return i < tokens.size && tokens[i].toFloatOrNull() != null
        }

        while (i < tokens.size) {
            var cmd = tokens[i][0]
            if (cmd.isDigit() || cmd == '-' || cmd == '.') {
                cmd = lastCmd
            } else {
                i++
            }
            when (cmd.uppercaseChar()) {
                'M' -> {
                    var x = next() ?: break
                    var y = next() ?: break
                    if (cmd == 'm') { x += cx; y += cy }
                    path.moveTo(x, y)
                    cx = x; cy = y
                    startX = x; startY = y
                    lastCmd = if (cmd == 'M') 'L' else 'l'
                    while (peekIsNumber()) {
                        var x2 = next() ?: break
                        var y2 = next() ?: break
                        if (cmd == 'm') { x2 += cx; y2 += cy }
                        path.lineTo(x2, y2)
                        cx = x2; cy = y2
                        lastCmd = if (cmd == 'M') 'L' else 'l'
                    }
                }
                'L' -> {
                    lastCmd = cmd
                    while (peekIsNumber()) {
                        var x = next() ?: break
                        var y = next() ?: break
                        if (cmd == 'l') { x += cx; y += cy }
                        path.lineTo(x, y)
                        cx = x; cy = y
                    }
                }
                'H' -> {
                    lastCmd = cmd
                    while (peekIsNumber()) {
                        var x = next() ?: break
                        if (cmd == 'h') x += cx
                        path.lineTo(x, cy)
                        cx = x
                    }
                }
                'V' -> {
                    lastCmd = cmd
                    while (peekIsNumber()) {
                        var y = next() ?: break
                        if (cmd == 'v') y += cy
                        path.lineTo(cx, y)
                        cy = y
                    }
                }
                'C' -> {
                    lastCmd = cmd
                    while (peekIsNumber()) {
                        var x1 = next() ?: break
                        var y1 = next() ?: break
                        var x2 = next() ?: break
                        var y2 = next() ?: break
                        var x = next() ?: break
                        var y = next() ?: break
                        if (cmd == 'c') {
                            x1 += cx; y1 += cy; x2 += cx; y2 += cy; x += cx; y += cy
                        }
                        path.cubicTo(x1, y1, x2, y2, x, y)
                        lastControlX = x2; lastControlY = y2
                        cx = x; cy = y
                    }
                }
                'S' -> {
                    lastCmd = cmd
                    while (peekIsNumber()) {
                        var x2 = next() ?: break
                        var y2 = next() ?: break
                        var x = next() ?: break
                        var y = next() ?: break
                        val px = if (lastCmd == 'C' || lastCmd == 'S') 2 * cx - lastControlX else cx
                        val py = if (lastCmd == 'C' || lastCmd == 'S') 2 * cy - lastControlY else cy
                        if (cmd == 's') { x2 += cx; y2 += cy; x += cx; y += cy }
                        path.cubicTo(px, py, x2, y2, x, y)
                        lastControlX = x2; lastControlY = y2
                        cx = x; cy = y
                    }
                }
                'Q' -> {
                    lastCmd = cmd
                    while (peekIsNumber()) {
                        var x1 = next() ?: break
                        var y1 = next() ?: break
                        var x = next() ?: break
                        var y = next() ?: break
                        if (cmd == 'q') { x1 += cx; y1 += cy; x += cx; y += cy }
                        path.quadTo(x1, y1, x, y)
                        lastControlX = x1; lastControlY = y1
                        cx = x; cy = y
                    }
                }
                'T' -> {
                    lastCmd = cmd
                    while (peekIsNumber()) {
                        var x = next() ?: break
                        var y = next() ?: break
                        val px = if (lastCmd == 'Q' || lastCmd == 'T') 2 * cx - lastControlX else cx
                        val py = if (lastCmd == 'Q' || lastCmd == 'T') 2 * cy - lastControlY else cy
                        if (cmd == 't') { x += cx; y += cy }
                        path.quadTo(px, py, x, y)
                        lastControlX = px; lastControlY = py
                        cx = x; cy = y
                    }
                }
                'Z' -> {
                    lastCmd = cmd
                    path.close()
                    cx = startX; cy = startY
                }
            }
        }
        return path
    }

    private fun tokenize(d: String): List<String> {
        val out = mutableListOf<String>()
        val re = Regex("[A-Za-z]|-?\\d+\\.\\d+|-?\\.\\d+|-?\\d+")
        for (m in re.findAll(d)) out.add(m.value)
        return out
    }

    private fun parseRect(attrs: Map<String, String>): Path? {
        val x = attrs["x"]?.toFloatOrNull() ?: 0f
        val y = attrs["y"]?.toFloatOrNull() ?: 0f
        val w = attrs["width"]?.toFloatOrNull() ?: return null
        val h = attrs["height"]?.toFloatOrNull() ?: return null
        val rx = attrs["rx"]?.toFloatOrNull() ?: 0f
        val ry = attrs["ry"]?.toFloatOrNull() ?: 0f
        val p = Path()
        if (rx <= 0f || ry <= 0f) {
            p.addRect(x, y, x + w, y + h, Path.Direction.CW)
        } else {
            p.addRoundRect(x, y, x + w, y + h, rx, ry, Path.Direction.CW)
        }
        return p
    }

    private fun parseCircle(attrs: Map<String, String>): Path? {
        val cx = attrs["cx"]?.toFloatOrNull() ?: 0f
        val cy = attrs["cy"]?.toFloatOrNull() ?: 0f
        val r = attrs["r"]?.toFloatOrNull() ?: return null
        return Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
    }

    private fun parseEllipse(attrs: Map<String, String>): Path? {
        val cx = attrs["cx"]?.toFloatOrNull() ?: 0f
        val cy = attrs["cy"]?.toFloatOrNull() ?: 0f
        val rx = attrs["rx"]?.toFloatOrNull() ?: return null
        val ry = attrs["ry"]?.toFloatOrNull() ?: return null
        return Path().apply { addOval(cx - rx, cy - ry, cx + rx, cy + ry, Path.Direction.CW) }
    }

    private fun parseLine(attrs: Map<String, String>): Path? {
        val x1 = attrs["x1"]?.toFloatOrNull() ?: 0f
        val y1 = attrs["y1"]?.toFloatOrNull() ?: 0f
        val x2 = attrs["x2"]?.toFloatOrNull() ?: 0f
        val y2 = attrs["y2"]?.toFloatOrNull() ?: 0f
        return Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
    }

    private fun parsePoly(attrs: Map<String, String>): Path? {
        val points = attrs["points"] ?: return null
        val coords = points.trim().split(Regex("[\\s,]+")).mapNotNull { it.toFloatOrNull() }
        if (coords.size < 2) return null
        return Path().apply {
            moveTo(coords[0], coords[1])
            var i = 2
            while (i + 1 < coords.size) {
                lineTo(coords[i], coords[i + 1])
                i += 2
            }
        }
    }

    private fun normalizeColor(s: String): String {
        if (s.startsWith("rgb(")) {
            val nums = s.removePrefix("rgb(").removeSuffix(")").split(",").mapNotNull { it.trim().toIntOrNull() }
            if (nums.size >= 3) {
                return String.format(Locale.US, "#%02X%02X%02X", nums[0], nums[1], nums[2])
            }
        }
        if (s == "none") return "#00000000"
        return s
    }

    private fun parseColor(hex: String): Int? {
        if (hex.length == 9) {
            return hex.removePrefix("#").toLongOrNull(16)?.toInt()
        }
        if (hex.length == 7) {
            return 0xFF000000.toInt() or (hex.removePrefix("#").toLongOrNull(16)?.toInt() ?: return null)
        }
        return null
    }
}
