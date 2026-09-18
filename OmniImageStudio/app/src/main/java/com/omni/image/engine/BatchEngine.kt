package com.omni.image.engine

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException

object BatchEngine {

    data class BatchConfig(
        val convertFormat: String? = null,
        val quality: Int = 90,
        val resizeWidth: Int = 0,
        val resizeHeight: Int = 0,
        val keepRatio: Boolean = true,
        val watermark: WatermarkEngine.WatermarkStyle? = null,
        val watermarkPosition: String = "bottomRight",
        val watermarkTile: Boolean = false,
        val tileGap: Int = 200,
        val applyExifFields: Map<String, String> = emptyMap(),
        val clearExif: Boolean = false,
        val renamePrefix: String = "",
        val outputDir: String? = null
    )

    data class BatchItem(
        val inputFile: File,
        val relativePath: String,
        val outputFile: File?,
        val success: Boolean,
        val message: String
    )

    fun collectFiles(root: File, extensions: List<String>): List<Pair<File, String>> {
        val result = mutableListOf<Pair<File, String>>()
        val extSet = extensions.map { it.lowercase() }.toSet()
        val exts = if (extSet.isEmpty()) setOf("png", "jpg", "jpeg", "webp", "bmp", "gif", "tif", "tiff", "ico") else extSet
        fun walk(dir: File, rel: String) {
            dir.listFiles()?.sortedBy { it.name }?.forEach { f ->
                if (f.isDirectory) {
                    walk(f, if (rel.isEmpty()) f.name else "$rel/${f.name}")
                } else {
                    if (f.extension.lowercase() in exts) {
                        result.add(f to rel)
                    }
                }
            }
        }
        if (root.exists()) walk(root, "")
        return result
    }

    fun runBatch(
        context: Context,
        inputRoot: File,
        config: BatchConfig,
        onProgress: (Int, Int) -> Unit
    ): List<BatchItem> {
        val files = collectFiles(inputRoot, emptyList())
        val results = mutableListOf<BatchItem>()
        var done = 0
        for ((file, rel) in files) {
            val out = runCatching {
                processFile(context, file, rel, config, outputDir = config.outputDir)
            }.getOrElse {
                BatchItem(file, rel, null, false, it.message ?: it.toString())
            }
            results.add(out)
            done++
            onProgress(done, files.size)
        }
        return results
    }

    private fun processFile(context: Context, file: File, rel: String, config: BatchConfig, outputDir: String?): BatchItem {
        val bitmap = ConvertEngine.decodeFile(file.absolutePath) ?: return BatchItem(file, rel, null, false, "decode failed")
        var current = bitmap

        if (config.resizeWidth > 0 || config.resizeHeight > 0) {
            current = ResizeEngine.resize(current, config.resizeWidth, config.resizeHeight, config.keepRatio)
        }
        if (config.watermark != null) {
            if (config.watermarkTile) {
                current = WatermarkEngine.applyTileText(current, config.watermark, config.tileGap)
            } else {
                current = WatermarkEngine.applyText(current, config.watermark, config.watermarkPosition)
            }
        }

        val outExt = config.convertFormat?.let { ConvertEngine.outputExtension(it) } ?: file.extension
        val outName = buildOutputName(file.nameWithoutExtension, outExt, config.renamePrefix, doneCounter++)
        val outFile = resolveOutputFile(file, rel, outName, outputDir)

        if (config.convertFormat != null && ConvertEngine.compressFormat(config.convertFormat) != null) {
            ConvertEngine.encode(current, config.convertFormat, outFile, config.quality)
        } else {
            val cf = ConvertEngine.compressFormat(ConvertEngine.guessMimeByPath(file.name)?.substringAfter('/') ?: "png")
            outFile.outputStream().use { out ->
                if (cf != null) {
                    current.compress(cf, config.quality, out)
                } else {
                    current.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
            }
        }
        current.recycle()
        if (bitmap !== current) bitmap.recycle()

        if (config.clearExif) {
            runCatching {
                val cache = ExifEngine.copyToCache(context, Uri.fromFile(outFile))
                if (cache != null) {
                    val exif = androidx.exifinterface.media.ExifInterface(cache.absolutePath)
                    for (tag in ExifEngine.READ_TAGS.map { it.first }) {
                        runCatching { exif.setAttribute(tag, "") }
                    }
                    exif.saveAttributes()
                    cache.renameTo(outFile)
                }
            }
        } else if (config.applyExifFields.isNotEmpty()) {
            runCatching {
                val cache = ExifEngine.copyToCache(context, Uri.fromFile(outFile))
                if (cache != null) {
                    val exif = androidx.exifinterface.media.ExifInterface(cache.absolutePath)
                    for ((tag, value) in config.applyExifFields) {
                        runCatching { exif.setAttribute(tag, value) }
                    }
                    exif.saveAttributes()
                    cache.renameTo(outFile)
                }
            }
        }
        return BatchItem(file, rel, outFile, true, "ok")
    }

    private var doneCounter = 0

    private fun buildOutputName(base: String, ext: String, prefix: String, seq: Int): String {
        val p = if (prefix.isBlank()) "" else "${prefix}_"
        return "${p}${String.format("%03d", seq)}_$base.$ext"
    }

    private fun resolveOutputFile(input: File, rel: String, name: String, outputDir: String?): File {
        val parentDir = if (outputDir != null) {
            val relDir = File(rel)
            val dir = File(outputDir, relDir.path)
            dir.mkdirs()
            dir
        } else {
            input.parentFile ?: File(".")
        }
        return File(parentDir, name)
    }

    fun copyFileToAppCache(context: Context, uri: Uri, suffix: String): File? {
        return try {
            val cache = File(context.cacheDir, "batch_${System.currentTimeMillis()}$suffix")
            context.contentResolver.openInputStream(uri)?.use { input ->
                cache.outputStream().use { out -> input.copyTo(out) }
            }
            cache
        } catch (e: IOException) {
            null
        }
    }
}
