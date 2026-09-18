package com.omni.image.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omni.image.engine.ConvertEngine
import com.omni.image.engine.ExifEngine
import com.omni.image.engine.ResizeEngine
import com.omni.image.engine.WatermarkEngine
import com.omni.image.ui.theme.GlassOnBackground
import com.omni.image.ui.theme.GlassSuccess
import com.omni.image.util.FileSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.documentfile.provider.DocumentFile
import java.io.File

@Composable
fun BatchScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selected by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var folderMode by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    var format by remember { mutableStateOf("PNG") }
    var quality by remember { mutableStateOf(90f) }
    var resizeW by remember { mutableStateOf("") }
    var resizeH by remember { mutableStateOf("") }
    var keepRatio by remember { mutableStateOf(true) }
    var renamePrefix by remember { mutableStateOf("") }
    var wmText by remember { mutableStateOf("") }
    var wmOpacity by remember { mutableStateOf(0.4f) }
    var wmPosition by remember { mutableStateOf("bottomRight") }
    var wmTile by remember { mutableStateOf(false) }
    var clearExif by remember { mutableStateOf(false) }

    val multiPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        selected = uris
        folderMode = false
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            selected = listOf(uri)
            folderMode = true
        }
    }

    fun collectSaf(root: DocumentFile, rel: String, out: MutableList<Pair<Uri, String>>) {
        root.listFiles().forEach { f ->
            if (f.isDirectory) {
                collectSaf(f, if (rel.isEmpty()) f.name ?: "" else "$rel/${f.name}", out)
            } else {
                val ext = (f.name ?: "").substringAfterLast('.', "").lowercase()
                if (ext in setOf("png", "jpg", "jpeg", "webp", "bmp", "gif", "tif", "tiff", "ico")) {
                    out.add(f.uri to rel)
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(Modifier.fillMaxWidth()) {
            GlassButton(if (selected.isEmpty()) "选择多张图片" else "重新选择图片", onClick = {
                multiPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            GlassButton(if (selected.isEmpty()) "选择文件夹" else "重选文件夹", onClick = {
                folderPicker.launch(null)
            }, modifier = Modifier.weight(1f))
        }
        if (selected.isNotEmpty()) {
            Text(
                "${if (folderMode) "文件夹模式（保留目录结构）" else "多选模式"} · 已选 ${selected.size} 项",
                fontSize = 13.sp,
                color = GlassSuccess,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        SectionTitle("统一转换格式")
        Row(Modifier.fillMaxWidth()) {
            ConvertEngine.OUTPUT_FORMATS.forEach { f ->
                GlassChip(f, format == f, onClick = { format = f })
                Spacer(Modifier.width(6.dp))
            }
        }
        DampedSlider(quality, { quality = it }, 1f..100f, label = "质量", display = "${quality.toInt()}%")

        SectionTitle("统一尺寸")
        Row {
            OutlinedTextField(resizeW, { resizeW = it }, label = { Text("宽(px, 0=不变)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(resizeH, { resizeH = it }, label = { Text("高(px, 0=不变)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            GlassToggle("锁定宽高比", keepRatio) { keepRatio = it }
            Spacer(Modifier.width(12.dp))
            GlassToggle("清除 EXIF", clearExif) { clearExif = it }
        }

        SectionTitle("统一文字水印（留空则不加水印）")
        OutlinedTextField(wmText, { wmText = it }, label = { Text("水印文字") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        DampedSlider(wmOpacity, { wmOpacity = it }, 0.05f..0.9f, label = "水印不透明度", display = "${(wmOpacity * 100).toInt()}%")
        Row(Modifier.fillMaxWidth()) {
            GlassToggle("平铺水印", wmTile) { wmTile = it }
        }

        SectionTitle("批量重命名前缀")
        OutlinedTextField(renamePrefix, { renamePrefix = it }, label = { Text("前缀（如 batch_）") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(14.dp))
        GlassButton(
            "开始批量处理",
            enabled = selected.isNotEmpty() && loading.not(),
            loading = loading
        ) {
            val uris = selected
            loading = true
            scope.launch(Dispatchers.IO) {
                val items = mutableListOf<Pair<Uri, String>>()
                if (folderMode) {
                    val rootDoc = DocumentFile.fromTreeUri(context, uris[0])
                    if (rootDoc != null) collectSaf(rootDoc, "", items)
                } else {
                    items.addAll(uris.map { it to "" })
                }
                val outDir = FileSaver.outputDir(context).resolve("batch").apply { mkdirs() }
                val style = if (wmText.isNotBlank()) {
                    WatermarkEngine.WatermarkStyle(
                        text = wmText,
                        color = 0xFFFFFFFF,
                        sizePx = 48f,
                        rotation = 0f,
                        opacity = wmOpacity,
                        shadow = true
                    )
                } else null

                var success = 0
                var failed = 0
                items.forEachIndexed { index, (uri, rel) ->
                    runCatching {
                        val cache = ExifEngine.copyToCache(context, uri) ?: return@runCatching
                        var bmp = ConvertEngine.decodeFile(cache.absolutePath) ?: return@runCatching
                        if (resizeW.toIntOrNull()?.let { it > 0 } == true || (resizeH.toIntOrNull() ?: 0) > 0) {
                            bmp = ResizeEngine.resize(bmp, resizeW.toIntOrNull() ?: 0, resizeH.toIntOrNull() ?: 0, keepRatio)
                        }
                        if (style != null) {
                            bmp = if (wmTile) WatermarkEngine.applyTileText(bmp, style, 200) else WatermarkEngine.applyText(bmp, style, wmPosition)
                        }
                        val name = cache.name.substringBeforeLast('.').let { base ->
                            if (renamePrefix.isNotBlank()) "${renamePrefix}_$base" else base
                        }
                        val ext = ConvertEngine.outputExtension(format)
                        val relDir = if (rel.isEmpty()) "" else "$rel/"
                        val dir = File(outDir, relDir).apply { mkdirs() }
                        val outFile = File(dir, "$name.$ext")
                        if (ConvertEngine.encode(bmp, format, outFile, quality.toInt())) {
                            if (clearExif) {
                                runCatching {
                                    val ef = ExifEngine.copyToCache(context, Uri.fromFile(outFile))
                                    if (ef != null) {
                                        val exif = androidx.exifinterface.media.ExifInterface(ef.absolutePath)
                                        for (t in ExifEngine.READ_TAGS.map { it.first }) runCatching { exif.setAttribute(t, "") }
                                        exif.saveAttributes()
                                        ef.renameTo(outFile)
                                    }
                                }
                            }
                            success++
                        } else {
                            failed++
                        }
                        cache.delete()
                    }.onFailure { failed++ }
                    withContext(Dispatchers.Main) {
                        progress = "${index + 1}/${items.size} 已完成"
                    }
                }
                withContext(Dispatchers.Main) {
                    loading = false
                    progress = ""
                    message = "批量处理完成：成功 $success，失败 $failed\n输出目录: ${outDir.absolutePath}"
                }
            }
        }
        if (progress.isNotEmpty()) {
            Text(progress, fontSize = 13.sp, color = Color(0xFF7AA2F7), modifier = Modifier.padding(top = 8.dp))
        }
        if (message.isNotEmpty()) {
            Text(message, fontSize = 12.sp, color = GlassSuccess, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
