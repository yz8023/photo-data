package com.omni.image.engine

import android.graphics.Bitmap
import kotlin.math.pow
import kotlin.math.roundToInt

object FilterEngine {

    enum class FilterType(val label: String) {
        GRAYSCALE("灰度"),
        SEPIA("复古"),
        INVERT("反转"),
        RELIEF("浮雕"),
        MOSAIC("马赛克"),
        OIL("油画"),
        VIGNETTE("暗角"),
        NEGATIVE("负片")
    }

    fun applyFilter(src: Bitmap, type: FilterType): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val w = out.width
        val h = out.height
        val pixels = IntArray(w * h)
        out.getPixels(pixels, 0, w, 0, 0, w, h)
        when (type) {
            FilterType.GRAYSCALE -> {
                for (i in pixels.indices) {
                    val c = pixels[i]
                    val r = (c shr 16) and 0xFF
                    val g = (c shr 8) and 0xFF
                    val b = c and 0xFF
                    val lum = (r * 299 + g * 587 + b * 114) / 1000
                    pixels[i] = (c and 0xFF000000.toInt()) or (lum shl 16) or (lum shl 8) or lum
                }
            }
            FilterType.SEPIA -> {
                for (i in pixels.indices) {
                    val c = pixels[i]
                    val r = (c shr 16) and 0xFF
                    val g = (c shr 8) and 0xFF
                    val b = c and 0xFF
                    val tr = (r * 0.393 + g * 0.769 + b * 0.189).toInt().coerceIn(0, 255)
                    val tg = (r * 0.349 + g * 0.686 + b * 0.168).toInt().coerceIn(0, 255)
                    val tb = (r * 0.272 + g * 0.534 + b * 0.131).toInt().coerceIn(0, 255)
                    pixels[i] = (c and 0xFF000000.toInt()) or (tr shl 16) or (tg shl 8) or tb
                }
            }
            FilterType.INVERT, FilterType.NEGATIVE -> {
                for (i in pixels.indices) {
                    val c = pixels[i]
                    pixels[i] = (c and 0xFF000000.toInt()) or
                        ((255 - ((c shr 16) and 0xFF)) shl 16) or
                        ((255 - ((c shr 8) and 0xFF)) shl 8) or
                        (255 - (c and 0xFF))
                }
            }
            FilterType.RELIEF -> {
                for (y in 1 until h) {
                    for (x in 0 until w) {
                        val idx = y * w + x
                        val cur = pixels[idx]
                        val up = pixels[(y - 1) * w + x]
                        val dr = (((cur shr 16) and 0xFF) - ((up shr 16) and 0xFF)).coerceIn(0, 255)
                        val dg = (((cur shr 8) and 0xFF) - ((up shr 8) and 0xFF)).coerceIn(0, 255)
                        val db = ((cur and 0xFF) - (up and 0xFF)).coerceIn(0, 255)
                        val gray = (dr * 299 + dg * 587 + db * 114) / 1000 + 128
                        val v = gray.coerceIn(0, 255)
                        pixels[idx] = 0xFF000000.toInt() or (v shl 16) or (v shl 8) or v
                    }
                }
            }
            FilterType.MOSAIC -> {
                val block = 12
                for (by in 0 until h step block) {
                    for (bx in 0 until w step block) {
                        var sumR = 0; var sumG = 0; var sumB = 0; var cnt = 0
                        for (y in by until (by + block).coerceAtMost(h)) {
                            for (x in bx until (bx + block).coerceAtMost(w)) {
                                val c = pixels[y * w + x]
                                sumR += (c shr 16) and 0xFF
                                sumG += (c shr 8) and 0xFF
                                sumB += c and 0xFF
                                cnt++
                            }
                        }
                        if (cnt == 0) continue
                        val r = sumR / cnt; val g = sumG / cnt; val b = sumB / cnt
                        for (y in by until (by + block).coerceAtMost(h)) {
                            for (x in bx until (bx + block).coerceAtMost(w)) {
                                pixels[y * w + x] = 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
                            }
                        }
                    }
                }
            }
            FilterType.OIL -> {
                val radius = 3
                val levels = 32
                for (y in 0 until h) {
                    for (x in 0 until w) {
                        val histSum = Array(levels) { IntArray(3) }
                        val histCount = IntArray(levels)
                        var maxIdx = 0
                        var maxCount = 0
                        for (dy in -radius..radius) {
                            for (dx in -radius..radius) {
                                val xx = (x + dx).coerceIn(0, w - 1)
                                val yy = (y + dy).coerceIn(0, h - 1)
                                val c = pixels[yy * w + xx]
                                val lum = (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
                                val lv = (lum * (levels - 1) / 255).coerceIn(0, levels - 1)
                                histSum[lv][0] += (c shr 16) and 0xFF
                                histSum[lv][1] += (c shr 8) and 0xFF
                                histSum[lv][2] += c and 0xFF
                                histCount[lv]++
                                if (histCount[lv] > maxCount) {
                                    maxCount = histCount[lv]
                                    maxIdx = lv
                                }
                            }
                        }
                        if (maxCount == 0) continue
                        val avgR = histSum[maxIdx][0] / maxCount
                        val avgG = histSum[maxIdx][1] / maxCount
                        val avgB = histSum[maxIdx][2] / maxCount
                        pixels[y * w + x] = 0xFF000000.toInt() or (avgR shl 16) or (avgG shl 8) or avgB
                    }
                }
            }
            FilterType.VIGNETTE -> {
                val cx = w / 2f
                val cy = h / 2f
                val maxDist = kotlin.math.sqrt((cx * cx + cy * cy).toDouble()).toFloat()
                for (y in 0 until h) {
                    for (x in 0 until w) {
                        val dist = kotlin.math.sqrt(((x - cx) * (x - cx) + (y - cy) * (y - cy)).toDouble()).toFloat()
                        val factor = (1f - (dist / maxDist) * 0.6f).coerceIn(0.4f, 1f)
                        val idx = y * w + x
                        val c = pixels[idx]
                        val r = (((c shr 16) and 0xFF) * factor).toInt()
                        val g = (((c shr 8) and 0xFF) * factor).toInt()
                        val b = ((c and 0xFF) * factor).toInt()
                        pixels[idx] = 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
                    }
                }
            }
        }
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    data class AdjustParams(
        var brightness: Float = 0f,
        var contrast: Float = 1f,
        var saturation: Float = 1f,
        var colorTemp: Float = 0f,
        var exposure: Float = 0f,
        var shadows: Float = 0f,
        var highlights: Float = 0f
    )

    fun adjust(src: Bitmap, params: AdjustParams): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val w = out.width
        val h = out.height
        val pixels = IntArray(w * h)
        out.getPixels(pixels, 0, w, 0, 0, w, h)

        val bright = params.brightness
        val contrast = params.contrast
        val sat = params.saturation
        val temp = params.colorTemp
        val exposure = params.exposure
        val shad = params.shadows
        val high = params.highlights

        for (i in pixels.indices) {
            var r = (pixels[i] shr 16) and 0xFF
            var g = (pixels[i] shr 8) and 0xFF
            var b = pixels[i] and 0xFF

            r += (bright * 255).toInt()
            g += (bright * 255).toInt()
            b += (bright * 255).toInt()

            r = ((r - 128) * contrast + 128).toInt()
            g = ((g - 128) * contrast + 128).toInt()
            b = ((b - 128) * contrast + 128).toInt()

            val lum = (r * 299 + g * 587 + b * 114) / 1000
            r = (lum + (r - lum) * sat).toInt()
            g = (lum + (g - lum) * sat).toInt()
            b = (lum + (b - lum) * sat).toInt()

            r += (temp * 30).toInt()
            b -= (temp * 30).toInt()

            val expFactor = (1f + exposure).coerceIn(0.1f, 3f)
            r = (r * expFactor).toInt()
            g = (g * expFactor).toInt()
            b = (b * expFactor).toInt()

            val adj = lum / 255f
            if (shad > 0f) {
                val boost = (1f - adj) * shad
                r = (r + 255 * boost).toInt()
                g = (g + 255 * boost).toInt()
                b = (b + 255 * boost).toInt()
            }
            if (high > 0f) {
                val damp = adj * high
                r = (r - 255 * damp).toInt()
                g = (g - 255 * damp).toInt()
                b = (b - 255 * damp).toInt()
            }

            pixels[i] = 0xFF000000.toInt() or
                (r.coerceIn(0, 255) shl 16) or
                (g.coerceIn(0, 255) shl 8) or
                b.coerceIn(0, 255)
        }
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    fun applyCurve(src: Bitmap, rPoints: List<Float>, gPoints: List<Float>, bPoints: List<Float>): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val w = out.width
        val h = out.height
        val pixels = IntArray(w * h)
        out.getPixels(pixels, 0, w, 0, 0, w, h)
        val lutR = buildLut(rPoints)
        val lutG = buildLut(gPoints)
        val lutB = buildLut(bPoints)
        for (i in pixels.indices) {
            val c = pixels[i]
            val nr = lutR[(c shr 16) and 0xFF]
            val ng = lutG[(c shr 8) and 0xFF]
            val nb = lutB[c and 0xFF]
            pixels[i] = (c and 0xFF000000.toInt()) or (nr shl 16) or (ng shl 8) or nb
        }
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    fun applyLevels(
        src: Bitmap,
        inBlack: Int, inWhite: Int, gamma: Float,
        outBlack: Int, outWhite: Int
    ): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val w = out.width
        val h = out.height
        val pixels = IntArray(w * h)
        out.getPixels(pixels, 0, w, 0, 0, w, h)
        val lut = IntArray(256)
        val range = (inWhite - inBlack).coerceAtLeast(1)
        val outRange = (outWhite - outBlack)
        for (v in 0..255) {
            val norm = ((v - inBlack) / range.toFloat()).coerceIn(0f, 1f)
            val gammaCorrected = if (gamma != 1f) norm.pow(1f / gamma) else norm
            lut[v] = (outBlack + gammaCorrected * outRange).toInt().coerceIn(0, 255)
        }
        for (i in pixels.indices) {
            val c = pixels[i]
            pixels[i] = (c and 0xFF000000.toInt()) or
                (lut[(c shr 16) and 0xFF] shl 16) or
                (lut[(c shr 8) and 0xFF] shl 8) or
                lut[c and 0xFF]
        }
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun buildLut(points: List<Float>): IntArray {
        val lut = IntArray(256)
        if (points.size < 2) {
            for (v in 0..255) lut[v] = v
            return lut
        }
        val xs = points.mapIndexed { i, _ -> i.toFloat() / (points.size - 1) }
        for (v in 0..255) {
            val x = v / 255f
            var lo = 0
            var hi = xs.size - 1
            for (i in 0 until xs.size - 1) {
                if (x >= xs[i] && x <= xs[i + 1]) {
                    lo = i
                    hi = i + 1
                    break
                }
            }
            val t = if (xs[hi] == xs[lo]) 0f else (x - xs[lo]) / (xs[hi] - xs[lo])
            val y = points[lo] + (points[hi] - points[lo]) * t
            lut[v] = (y * 255).toInt().coerceIn(0, 255)
        }
        return lut
    }

    fun toParams(type: FilterType): Map<String, Float> {
        return when (type) {
            FilterType.GRAYSCALE -> mapOf("saturation" to 0f)
            FilterType.SEPIA -> mapOf("sepia" to 1f)
            FilterType.INVERT -> mapOf("invert" to 1f)
            FilterType.NEGATIVE -> mapOf("invert" to 1f)
            FilterType.RELIEF -> mapOf("relief" to 1f)
            FilterType.MOSAIC -> mapOf("mosaic" to 1f)
            FilterType.OIL -> mapOf("oil" to 1f)
            FilterType.VIGNETTE -> mapOf("vignette" to 1f)
        }
    }
}
