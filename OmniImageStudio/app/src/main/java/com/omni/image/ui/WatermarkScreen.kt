package com.omni.image.ui

import android.graphics.Bitmap
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omni.image.engine.ConvertEngine
import com.omni.image.engine.SteganoEngine
import com.omni.image.engine.WatermarkEngine
import com.omni.image.model.WatermarkPreset
import com.omni.image.ui.theme.GlassOnBackground
import com.omni.image.ui.theme.GlassSecondary
import com.omni.image.util.FileSaver
import com.omni.image.util.PresetStorage
import com.omni.image.util.RecentFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WatermarkScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var watermarkBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var result by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    var text by remember { mutableStateOf("© OmniImage") }
    var color by remember { mutableStateOf(Color.White) }
    var sizePx by remember { mutableStateOf(48f) }
    var rotation by remember { mutableStateOf(0f) }
    var opacity by remember { mutableStateOf(0.5f) }
    var shadow by remember { mutableStateOf(true) }
    var position by remember { mutableStateOf("bottomRight") }
    var tileMode by remember { mutableStateOf(false) }
    var tileGap by remember { mutableStateOf("200") }
    var wmOpacity by remember { mutableStateOf(0.6f) }
    var tab by remember { mutableStateOf("文字水印") }

    var stegoMessage by remember { mutableStateOf("隐藏内容") }
    var stegoStrength by remember { mutableStateOf(8f) }
    var stegoDct by remember { mutableStateOf(false) }

    val basePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            bitmap = ConvertEngine.decodeUri(context, uri)
            result = null
            RecentFiles.add(context, uri.toString(), uri.lastPathSegment ?: "image")
        }
    }
    val wmPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) watermarkBitmap = ConvertEngine.decodeUri(context, uri)
    }
    val presets = remember { PresetStorage.listWatermarkPresets(context) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(Modifier.fillMaxWidth()) {
            GlassButton(if (bitmap == null) "选择底图" else "重选底图", onClick = {
                basePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            GlassButton(if (watermarkBitmap == null) "选水印图" else "重选水印图", onClick = {
                wmPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }, modifier = Modifier.weight(1f))
        }

        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            listOf("文字水印", "图片水印", "平铺", "盲水印").forEach { t ->
                GlassChip(t, tab == t, onClick = { tab = t })
                Spacer(Modifier.width(8.dp))
            }
        }

        when (tab) {
            "文字水印" -> {
                OutlinedTextField(text, { text = it }, label = { Text("水印文字") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                ColorPickerPanel(color, { color = it }, Modifier.padding(top = 4.dp))
                DampedSlider(sizePx, { sizePx = it }, 10f..200f, label = "字号", display = "${sizePx.toInt()} px")
                DampedSlider(rotation, { rotation = it }, -45f..45f, label = "旋转角度", display = "${rotation.toInt()}°")
                DampedSlider(opacity, { opacity = it }, 0f..1f, label = "不透明度", display = "${(opacity * 100).toInt()}%")
                GlassToggle("阴影", shadow) { shadow = it }
                SectionTitle("位置")
                Row(Modifier.fillMaxWidth()) {
                    WatermarkEngine.POSITIONS.chunked(3).forEach { row ->
                        Column(Modifier.weight(1f)) {
                            row.forEach { (key, label) ->
                                GlassChip(label, position == key, onClick = { position = key }, modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }
                }
            }
            "图片水印" -> {
                DampedSlider(wmOpacity, { wmOpacity = it }, 0f..1f, label = "不透明度", display = "${(wmOpacity * 100).toInt()}%")
                DampedSlider(rotation, { rotation = it }, -45f..45f, label = "旋转角度", display = "${rotation.toInt()}°")
                SectionTitle("位置")
                Row(Modifier.fillMaxWidth()) {
                    WatermarkEngine.POSITIONS.chunked(3).forEach { row ->
                        Column(Modifier.weight(1f)) {
                            row.forEach { (key, label) ->
                                GlassChip(label, position == key, onClick = { position = key }, modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }
                }
            }
            "平铺" -> {
                DampedSlider(sizePx, { sizePx = it }, 10f..120f, label = "字号/尺寸", display = "${sizePx.toInt()} px")
                OutlinedTextField(tileGap, { tileGap = it }, label = { Text("平铺间距 (px)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                DampedSlider(opacity, { opacity = it }, 0f..1f, label = "不透明度", display = "${(opacity * 100).toInt()}%")
            }
            "盲水印" -> {
                OutlinedTextField(stegoMessage, { stegoMessage = it }, label = { Text("要嵌入的隐藏内容") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Row(Modifier.padding(top = 8.dp)) {
                    GlassToggle("使用 DCT 算法", stegoDct) { stegoDct = it }
                }
                if (stegoDct) {
                    DampedSlider(stegoStrength, { stegoStrength = it }, 1f..40f, label = "嵌入强度", display = "${stegoStrength.toInt()}")
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        GlassButton(
            "生成水印",
            enabled = bitmap != null && loading.not(),
            loading = loading
        ) {
            val bmp = bitmap ?: return@GlassButton
            loading = true
            scope.launch(Dispatchers.IO) {
                val out = when (tab) {
                    "文字水印" -> WatermarkEngine.applyText(
                        bmp,
                        WatermarkEngine.WatermarkStyle(text, color.toArgb().toLong(), sizePx, rotation, opacity, shadow),
                        position
                    )
                    "图片水印" -> {
                        val wm = watermarkBitmap
                        if (wm == null) null else WatermarkEngine.applyImage(bmp, wm, wmOpacity, position, rotation)
                    }
                    "平铺" -> {
                        val wm = watermarkBitmap
                        if (wm == null) {
                            WatermarkEngine.applyTileText(bmp, WatermarkEngine.WatermarkStyle(text, color.toArgb().toLong(), sizePx, rotation, opacity, shadow), tileGap.toIntOrNull() ?: 200)
                        } else {
                            WatermarkEngine.applyTileImage(bmp, wm, wmOpacity, tileGap.toIntOrNull() ?: 200)
                        }
                    }
                    "盲水印" -> {
                        if (stegoDct) SteganoEngine.embedDct(bmp, stegoMessage, stegoStrength.toInt())
                        else SteganoEngine.embedLsb(bmp, stegoMessage)
                    }
                    else -> null
                }
                withContext(Dispatchers.Main) {
                    loading = false
                    result = out
                    message = if (out != null) "水印已生成" else "生成失败（请检查水印图是否已选择）"
                }
            }
        }

        if (result != null) {
            ImagePreview(result, Modifier.fillMaxWidth().height(180.dp).padding(top = 10.dp))
        }
        if (tab == "盲水印" && bitmap != null) {
            GlassButton("提取盲水印", enabled = loading.not(), onClick = {
                val bmp = bitmap ?: return@GlassButton
                loading = true
                scope.launch(Dispatchers.IO) {
                    val extracted = if (stegoDct) SteganoEngine.extractDct(bmp) else SteganoEngine.extractLsb(bmp)
                    withContext(Dispatchers.Main) {
                        loading = false
                        message = if (extracted.isNullOrEmpty()) "未检测到水印" else "提取到: $extracted"
                    }
                }
            })
        }

        if (result != null) {
            Row(Modifier.padding(top = 10.dp)) {
                GlassButton("保存结果", onClick = {
                    val r = result ?: return@GlassButton
                    scope.launch(Dispatchers.IO) {
                        val f = FileSaver.saveBitmap(context, r, "PNG", 100, "watermarked")
                        withContext(Dispatchers.Main) { FileSaver.shareFile(context, f) }
                    }
                }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                GlassButton("存为预设", onClick = {
                    PresetStorage.saveWatermarkPreset(
                        context,
                        WatermarkPreset(
                            name = "预设${System.currentTimeMillis() % 10000}",
                            text = text,
                            color = color.toArgb().toLong(),
                            sizePx = sizePx,
                            rotation = rotation,
                            opacity = opacity,
                            shadow = shadow,
                            position = position,
                            tileMode = tileMode,
                            tileGap = tileGap.toIntOrNull() ?: 200,
                            imageUri = ""
                        )
                    )
                    message = "水印预设已保存"
                }, modifier = Modifier.weight(1f))
            }
        }

        if (presets.isNotEmpty()) {
            SectionTitle("已有水印预设")
            Row(Modifier.fillMaxWidth()) {
                presets.forEach { p ->
                    GlassChip(p.name, false, onClick = {
                        text = p.text
                        color = Color(p.color.toInt())
                        sizePx = p.sizePx
                        rotation = p.rotation
                        opacity = p.opacity
                        shadow = p.shadow
                        position = p.position
                        tileMode = p.tileMode
                        tileGap = p.tileGap.toString()
                        message = "已载入预设「${p.name}」"
                    })
                    Spacer(Modifier.width(8.dp))
                }
            }
        }

        if (message.isNotEmpty()) {
            Text(message, fontSize = 12.sp, color = Color(0xFF9ECE6A), modifier = Modifier.padding(top = 10.dp))
        }
    }
}
