package com.omni.image.ui

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omni.image.engine.ConvertEngine
import com.omni.image.engine.ResizeEngine
import com.omni.image.model.ImageTask
import com.omni.image.ui.theme.GlassOnBackground
import com.omni.image.util.FileSaver
import com.omni.image.util.RecentFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ConvertScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var sourceUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var sourceName by remember { mutableStateOf("") }
    var format by remember { mutableStateOf("PNG") }
    var quality by remember { mutableStateOf(90f) }
    var width by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var keepRatio by remember { mutableStateOf(true) }
    var targetKb by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(ImageTask.MODE_STANDARD) }
    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val uri = ScreenChannels.convertUri
        if (uri != null) {
            ScreenChannels.convertUri = null
            sourceUri = uri
            bitmap = ConvertEngine.decodeUri(context, uri)
            sourceName = uri.lastPathSegment ?: "image"
            result = ""
            RecentFiles.add(context, uri.toString(), sourceName)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            sourceUri = uri
            bitmap = ConvertEngine.decodeUri(context, uri)
            sourceName = uri.lastPathSegment ?: "image"
            result = ""
            RecentFiles.add(context, uri.toString(), sourceName)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        GlassButton(if (bitmap == null) "选择图片" else "重新选择图片") {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        ImagePreview(
            bitmap = bitmap,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(top = 12.dp)
        )

        SectionTitle("输出格式")
        Row(Modifier.fillMaxWidth()) {
            ConvertEngine.OUTPUT_FORMATS.forEach { f ->
                GlassChip(f, selected = format == f, onClick = { format = f })
                Spacer(Modifier.width(8.dp))
            }
        }
        Text(
            "GIF/TIFF/HEIC/AVIF/TGA/EPS 已预留接口（见 HANDOVER），SVG 输出请使用「矢量化」页",
            fontSize = 11.sp,
            color = GlassOnBackground,
            modifier = Modifier.padding(top = 6.dp)
        )

        SectionTitle("质量")
        DampedSlider(
            value = quality,
            onValueChange = { quality = it },
            valueRange = 1f..100f,
            label = "质量",
            display = "${quality.toInt()}%"
        )

        SectionTitle("尺寸与目标大小")
        Row {
            OutlinedTextField(
                value = width,
                onValueChange = { width = it },
                label = { Text("宽(px)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = height,
                onValueChange = { height = it },
                label = { Text("高(px)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassToggle("锁定宽高比", keepRatio) { keepRatio = it }
            Spacer(Modifier.width(12.dp))
            GlassChip("标准", mode == ImageTask.MODE_STANDARD, onClick = { mode = ImageTask.MODE_STANDARD })
            Spacer(Modifier.width(8.dp))
            GlassChip("限大小", mode == ImageTask.MODE_SIZE_LIMITED, onClick = { mode = ImageTask.MODE_SIZE_LIMITED })
            Spacer(Modifier.width(8.dp))
            GlassChip("组合", mode == ImageTask.MODE_COMBO, onClick = { mode = ImageTask.MODE_COMBO })
        }
        if (mode != ImageTask.MODE_STANDARD) {
            OutlinedTextField(
                value = targetKb,
                onValueChange = { targetKb = it },
                label = { Text("目标文件大小 (KB)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
        }

        Spacer(Modifier.height(16.dp))
        GlassButton(
            text = "开始转换",
            enabled = bitmap != null,
            loading = loading
        ) {
            val bmp = bitmap ?: return@GlassButton
            val w = width.toIntOrNull() ?: 0
            val h = height.toIntOrNull() ?: 0
            val kb = targetKb.toIntOrNull() ?: 0
            loading = true
            scope.launch(Dispatchers.IO) {
                var current = bmp
                if (w > 0 || h > 0) {
                    current = ResizeEngine.resize(current, w, h, keepRatio)
                }
                if (mode == ImageTask.MODE_SIZE_LIMITED || mode == ImageTask.MODE_COMBO) {
                    val (resized, q) = ResizeEngine.fitToFileSize(current, format, kb)
                    current = resized
                    quality = q.toFloat()
                }
                val outFormat = if (format == "XML_VECTOR") "PNG" else format
                val file = FileSaver.saveBitmapToSource(context, sourceUri, current, outFormat, quality.toInt(), "convert")
                val success = file.contentUri != null || (file.file != null && file.file!!.exists() && file.file!!.length() > 0)
                withContext(Dispatchers.Main) {
                    loading = false
                    result = if (success) file.description else "转换失败，请重试"
                }
            }
        }
        if (result.isNotEmpty()) {
            Text(result, fontSize = 12.sp, color = Color(0xFF9ECE6A), modifier = Modifier.padding(top = 10.dp))
        }
    }
}
