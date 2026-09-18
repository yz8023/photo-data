package com.omni.image.engine

import android.graphics.Bitmap
import java.nio.charset.StandardCharsets
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object SteganoEngine {

    fun embedLsb(src: Bitmap, message: String): Bitmap? {
        val bytes = message.toByteArray(StandardCharsets.UTF_8)
        val payload = IntArray(4 + bytes.size)
        payload[0] = (bytes.size shr 24) and 0xFF
        payload[1] = (bytes.size shr 16) and 0xFF
        payload[2] = (bytes.size shr 8) and 0xFF
        payload[3] = bytes.size and 0xFF
        for (i in bytes.indices) payload[4 + i] = bytes[i].toInt() and 0xFF

        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val w = out.width
        val h = out.height
        val pixels = IntArray(w * h)
        out.getPixels(pixels, 0, w, 0, 0, w, h)

        val totalBits = payload.size * 8
        if (totalBits > pixels.size * 3) return null

        var bitIdx = 0
        for (p in 0 until payload.size) {
            val byte = payload[p]
            for (b in 7 downTo 0) {
                val bit = (byte shr b) and 1
                val pixelIdx = bitIdx / 3
                val channel = bitIdx % 3
                if (pixelIdx >= pixels.size) break
                val c = pixels[pixelIdx]
                val mask = 0xFFFFFF xor (1 shl (if (channel == 0) 16 else if (channel == 1) 8 else 0))
                val value = (c shr if (channel == 0) 16 else if (channel == 1) 8 else 0) and 0xFF
                val newValue = (value and 0xFE) or bit
                pixels[pixelIdx] = (c and mask) or (newValue shl (if (channel == 0) 16 else if (channel == 1) 8 else 0))
                bitIdx++
            }
        }
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    fun extractLsb(src: Bitmap): String? {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val lenBytes = ByteArray(4)
        for (byte in 0 until 4) {
            var value = 0
            for (b in 7 downTo 0) {
                val bitIdx = (byte * 8 + (7 - b)) * 1
                val pixelIdx = (byte * 8 + (7 - b)) / 3
                val channel = (byte * 8 + (7 - b)) % 3
                if (pixelIdx >= pixels.size) return null
                val c = pixels[pixelIdx]
                val v = (c shr if (channel == 0) 16 else if (channel == 1) 8 else 0) and 0xFF
                value = (value shl 1) or (v and 1)
            }
            lenBytes[byte] = value.toByte()
        }
        val length = ((lenBytes[0].toInt() and 0xFF) shl 24) or
            ((lenBytes[1].toInt() and 0xFF) shl 16) or
            ((lenBytes[2].toInt() and 0xFF) shl 8) or
            (lenBytes[3].toInt() and 0xFF)
        if (length <= 0 || length > 65535) return null

        val data = ByteArray(length)
        for (byte in 0 until length) {
            var value = 0
            for (b in 7 downTo 0) {
                val bitIdx = (4 + byte) * 8 + (7 - b)
                val pixelIdx = bitIdx / 3
                val channel = bitIdx % 3
                if (pixelIdx >= pixels.size) return null
                val c = pixels[pixelIdx]
                val v = (c shr if (channel == 0) 16 else if (channel == 1) 8 else 0) and 0xFF
                value = (value shl 1) or (v and 1)
            }
            data[byte] = value.toByte()
        }
        return String(data, StandardCharsets.UTF_8)
    }

    private val dctTable = Array(8) { u ->
        Array(8) { x ->
            val cu = if (u == 0) sqrt(1.0 / 8.0) else sqrt(2.0 / 8.0)
            cu * cos(((2 * x + 1) * u * Math.PI) / 16.0)
        }
    }

    fun embedDct(src: Bitmap, message: String, strength: Int = 8): Bitmap? {
        val bytes = message.toByteArray(StandardCharsets.UTF_8)
        val payload = IntArray(4 + bytes.size)
        payload[0] = (bytes.size shr 24) and 0xFF
        payload[1] = (bytes.size shr 16) and 0xFF
        payload[2] = (bytes.size shr 8) and 0xFF
        payload[3] = bytes.size and 0xFF
        for (i in bytes.indices) payload[4 + i] = bytes[i].toInt() and 0xFF

        val w = src.width
        val h = src.height
        val blockW = w / 8
        val blockH = h / 8
        val usable = blockW * blockH
        if (payload.size * 8 > usable) return null

        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(w * h)
        out.getPixels(pixels, 0, w, 0, 0, w, h)
        val lum = FloatArray(w * h)
        val orig = IntArray(w * h)
        for (i in pixels.indices) {
            orig[i] = pixels[i]
            val r = (pixels[i] shr 16) and 0xFF
            val g = (pixels[i] shr 8) and 0xFF
            val b = pixels[i] and 0xFF
            lum[i] = (r * 0.299f + g * 0.587f + b * 0.114f)
        }

        var bitIdx = 0
        for (by in 0 until blockH) {
            for (bx in 0 until blockW) {
                if (bitIdx >= payload.size * 8) break
                val block = Array(8) { FloatArray(8) }
                for (y in 0 until 8) {
                    for (x in 0 until 8) {
                        block[y][x] = lum[(by * 8 + y) * w + (bx * 8 + x)]
                    }
                }
                val dct = dct2d(block)
                val bit = (payload[bitIdx / 8] shr (7 - (bitIdx % 8))) and 1
                val diff = (strength * if (bit == 1) 1f else -1f) * 0.5f
                dct[3][2] += diff
                dct[2][3] -= diff
                val back = idct2d(dct)
                for (y in 0 until 8) {
                    for (x in 0 until 8) {
                        val idx = (by * 8 + y) * w + (bx * 8 + x)
                        val c = orig[idx]
                        val r = ((c shr 16) and 0xFF).toFloat()
                        val g = ((c shr 8) and 0xFF).toFloat()
                        val b = (c and 0xFF).toFloat()
                        val oldLum = lum[idx]
                        val newLum = back[y][x]
                        val delta = (newLum - oldLum)
                        val nr = (r + delta).toInt().coerceIn(0, 255)
                        val ng = (g + delta).toInt().coerceIn(0, 255)
                        val nb = (b + delta).toInt().coerceIn(0, 255)
                        pixels[idx] = (c and 0xFF000000.toInt()) or (nr shl 16) or (ng shl 8) or nb
                    }
                }
                bitIdx++
            }
        }
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    fun extractDct(src: Bitmap, expectedLength: Int? = null): String? {
        val w = src.width
        val h = src.height
        val blockW = w / 8
        val blockH = h / 8
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val lum = FloatArray(w * h)
        for (i in pixels.indices) {
            val r = (pixels[i] shr 16) and 0xFF
            val g = (pixels[i] shr 8) and 0xFF
            val b = pixels[i] and 0xFF
            lum[i] = (r * 0.299f + g * 0.587f + b * 0.114f)
        }

        fun readBit(bx: Int, by: Int): Int {
            val block = Array(8) { FloatArray(8) }
            for (y in 0 until 8) {
                for (x in 0 until 8) {
                    block[y][x] = lum[(by * 8 + y) * w + (bx * 8 + x)]
                }
            }
            val dct = dct2d(block)
            val diff = dct[3][2] - dct[2][3]
            return if (diff >= 0) 1 else 0
        }

        fun readBits(bitStart: Int, byteCount: Int): ByteArray? {
            val data = ByteArray(byteCount)
            for (byte in 0 until byteCount) {
                var value = 0
                for (b in 7 downTo 0) {
                    val bitIdx = bitStart + byte * 8 + (7 - b)
                    val bx = bitIdx % blockW
                    val by = bitIdx / blockW
                    if (by >= blockH) return null
                    value = (value shl 1) or readBit(bx, by)
                }
                data[byte] = value.toByte()
            }
            return data
        }

        val lenBytes = readBits(0, 4) ?: return null
        val length = ((lenBytes[0].toInt() and 0xFF) shl 24) or
            ((lenBytes[1].toInt() and 0xFF) shl 16) or
            ((lenBytes[2].toInt() and 0xFF) shl 8) or
            (lenBytes[3].toInt() and 0xFF)
        val target = expectedLength ?: length
        if (target <= 0 || target > 65535) return null
        val data = readBits(32, target) ?: return null
        return String(data, StandardCharsets.UTF_8)
    }

    private fun dct2d(input: Array<FloatArray>): Array<FloatArray> {
        val n = 8
        val out = Array(n) { FloatArray(n) }
        for (u in 0 until n) {
            for (v in 0 until n) {
                var sum = 0.0
                for (x in 0 until n) {
                    for (y in 0 until n) {
                        sum += dctTable[u][x] * dctTable[v][y] * input[y][x]
                    }
                }
                out[v][u] = sum.toFloat()
            }
        }
        return out
    }

    private fun idct2d(input: Array<FloatArray>): Array<FloatArray> {
        val n = 8
        val out = Array(n) { FloatArray(n) }
        for (x in 0 until n) {
            for (y in 0 until n) {
                var sum = 0.0
                for (u in 0 until n) {
                    for (v in 0 until n) {
                        sum += dctTable[u][x] * dctTable[v][y] * input[v][u]
                    }
                }
                out[y][x] = sum.toFloat().coerceIn(0f, 255f)
            }
        }
        return out
    }
}
