package com.omni.image.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val WATERMARK_TABS = listOf("文字", "图片", "平铺", "盲水印")

@Composable
fun WatermarkScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var sourceUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var watermarkBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var result by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(WATERMARK_TABS[0]) }

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

    var stegoMessage by remember { mutableStateOf("隐藏内容") }
    var stegoStrength by remember { mutableStateOf(8f) }
    var stegoDct by remember { mutableStateOf(false) }

    // 实时预览：参数变化时防抖重算水印结果（浮于全图之上）
    var livePreview by remember { mutableStateOf<Bitmap?>(null) }
    var previewComputing by remember { mutableStateOf(false) }
    var previewJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    DisposableEffect(Unit) {
        onDispose { previewJob?.cancel() }
    }
    fun schedulingPreview() {
        val base = bitmap ?: return
        previewJob?.cancel()
        if (base != null) {
            val tabS = tab; val txt = text; val col = color; val sp = sizePx; val rot = rotation
            val op = opacity; val sh = shadow; val pos = position; val tm = tileMode
            val gap = tileGap.toIntOrNull() ?: 200; val wmo = wmOpacity; val wmBmp = watermarkBitmap
            val stm = stegoMessage; val sts = stegoStrength.toInt(); val sd = stegoDct
            previewComputing = true
            previewJob = scope.launch {
                delay(160)
                val out = withContext(Dispatchers.Default) {
                    val previewBase = scaleBitmap(base, 900)
                    when (tabS) {
                        "文字" -> WatermarkEngine.applyText(previewBase, WatermarkEngine.WatermarkStyle(txt, col.toArgb().toLong(), sp, rot, op, sh), pos)
                        "图片" -> wmBmp?.let { WatermarkEngine.applyImage(previewBase, scaleBitmap(it, (450 * (sp / 48f)).toInt().coerceAtLeast(64)), wmo, pos, rot) }
                        "平铺" -> wmBmp?.let {
                            WatermarkEngine.applyTileImage(previewBase, scaleBitmap(it, (450 * (sp / 48f)).toInt().coerceAtLeast(64)), wmo, gap)
                        } ?: WatermarkEngine.applyTileText(previewBase, WatermarkEngine.WatermarkStyle(txt, col.toArgb().toLong(), sp, rot, op, sh), gap)
                        "盲水印" -> if (sd) SteganoEngine.embedDct(previewBase, stm, sts) else SteganoEngine.embedLsb(previewBase, stm)
                        else -> null
                    }?.also { m -> if (m !== previewBase) previewBase.recycle() }
                }
                livePreview = out
                previewComputing = false
            }
        }
    }

    val basePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            sourceUri = uri
            bitmap = ConvertEngine.decodeUri(context, uri)
            result = null
            livePreview = null
            RecentFiles.add(context, uri.toString(), uri.lastPathSegment ?: "image")
        }
    }
    val wmPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            watermarkBitmap = ConvertEngine.decodeUri(context, uri)
            schedulingPreview()
        }
    }
    val presets = remember { PresetStorage.listWatermarkPresets(context) }

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GlassButton(if (bitmap == null) "选择底图" else "重选底图", onClick = {
                basePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            GlassButton(if (watermarkBitmap == null) "水印图" else "重选水印图", onClick = {
                wmPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }, modifier = Modifier.weight(1f))
        }

        // ===== 主预览区：全图悬浮呈现 =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap == null) {
                Text("点击上方按钮选择底图", color = GlassOnBackground, fontSize = 14.sp)
            } else {
                ImagePreview(
                    bitmap = livePreview ?: result ?: bitmap,
                    modifier = Modifier.fillMaxSize()
                )
                if (previewComputing) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .heightIn(min = 24.dp)
                            .background(Color(0x99000000), androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("实时预览生成中…", fontSize = 11.sp, color = Color.White)
                    }
                }
                if (message.isNotEmpty() && !previewComputing) {
                    Text(
                        message,
                        fontSize = 11.sp,
                        color = Color(0xFF9ECE6A),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                    )
                }
            }
        }

        if (bitmap != null) {
            // ===== 底部：功能按钮（展开对应功能面板） =====
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                WATERMARK_TABS.forEach { t ->
                    GlassChip(t, selected = tab == t, onClick = { tab = t; schedulingPreview() })
                    Spacer(Modifier.width(8.dp))
                }
                if (tab == "盲水印") {
                    Spacer(Modifier.width(4.dp))
                    GlassChip("提取", selected = false, onClick = {
                        val bmp = bitmap ?: return@GlassChip
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
            }

            AnimatedVisibility(
                visible = tab.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .padding(top = 8.dp)
                        .background(Color(0x40FFFFFF), androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                ) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)
                    ) {
                        when (tab) {
                            "文字" -> {
                                OutlinedTextField(text, { text = it; schedulingPreview() }, label = { Text("水印文字") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                ColorPickerPanel(color, { color = it; schedulingPreview() }, Modifier.padding(top = 4.dp))
                                DampedSlider(sizePx, { sizePx = it; schedulingPreview() }, 10f..120f, label = "字号", display = "${sizePx.toInt()} px")
                                DampedSlider(rotation, { rotation = it; schedulingPreview() }, -45f..45f, label = "旋转角度", display = "${rotation.toInt()}°")
                                DampedSlider(opacity, { opacity = it; schedulingPreview() }, 0f..1f, label = "不透明度", display = "${(opacity * 100).toInt()}%")
                                GlassToggle("阴影", shadow) { shadow = it; schedulingPreview() }
                                SectionTitle("位置")
                                WatermarkEngine.POSITIONS.forEach { (key, label) ->
                                    GlassChip(label, position == key, onClick = { position = key; schedulingPreview() }, modifier = Modifier.padding(vertical = 2.dp))
                                    Spacer(Modifier.width(8.dp))
                                }
                            }
                            "图片" -> {
                                DampedSlider(sizePx, { sizePx = it; schedulingPreview() }, 24f..240f, label = "水印尺寸", display = "${sizePx.toInt()} px")
                                DampedSlider(wmOpacity, { wmOpacity = it; schedulingPreview() }, 0f..1f, label = "不透明度", display = "${(wmOpacity * 100).toInt()}%")
                                DampedSlider(rotation, { rotation = it; schedulingPreview() }, -45f..45f, label = "旋转角度", display = "${rotation.toInt()}°")
                                SectionTitle("位置")
                                WatermarkEngine.POSITIONS.forEach { (key, label) ->
                                    GlassChip(label, position == key, onClick = { position = key; schedulingPreview() }, modifier = Modifier.padding(vertical = 2.dp))
                                    Spacer(Modifier.width(8.dp))
                                }
                            }
                            "平铺" -> {
                                DampedSlider(sizePx, { sizePx = it; schedulingPreview() }, 10f..120f, label = "字号/尺寸", display = "${sizePx.toInt()} px")
                                OutlinedTextField(tileGap, { tileGap = it; schedulingPreview() }, label = { Text("平铺间距 (px)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                                DampedSlider(opacity, { opacity = it; schedulingPreview() }, 0f..1f, label = "不透明度", display = "${(opacity * 100).toInt()}%")
                            }
                            "盲水印" -> {
                                OutlinedTextField(stegoMessage, { stegoMessage = it; schedulingPreview() }, label = { Text("要嵌入的隐藏内容") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                Row(Modifier.padding(top = 8.dp)) {
                                    GlassToggle("使用 DCT 算法", stegoDct) { stegoDct = it; schedulingPreview() }
                                }
                                if (stegoDct) {
                                    DampedSlider(stegoStrength, { stegoStrength = it; schedulingPreview() }, 1f..40f, label = "嵌入强度", display = "${stegoStrength.toInt()}")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                GlassButton("生成水印并保存", enabled = bitmap != null && loading.not(), loading = loading, onClick = {
                    val bmp = bitmap ?: return@GlassButton
                    loading = true
                    scope.launch(Dispatchers.IO) {
                        val out = when (tab) {
                            "文字" -> WatermarkEngine.applyText(bmp, WatermarkEngine.WatermarkStyle(text, color.toArgb().toLong(), sizePx, rotation, opacity, shadow), position)
                            "图片" -> {
                                val wm = watermarkBitmap
                                if (wm == null) null else WatermarkEngine.applyImage(bmp, wm, wmOpacity, position, rotation)
                            }
                            "平铺" -> {
                                val wm = watermarkBitmap
                                if (wm == null) WatermarkEngine.applyTileText(bmp, WatermarkEngine.WatermarkStyle(text, color.toArgb().toLong(), sizePx, rotation, opacity, shadow), tileGap.toIntOrNull() ?: 200)
                                else WatermarkEngine.applyTileImage(bmp, wm, wmOpacity, tileGap.toIntOrNull() ?: 200)
                            }
                            "盲水印" -> {
                                if (stegoDct) SteganoEngine.embedDct(bmp, stegoMessage, stegoStrength.toInt())
                                else SteganoEngine.embedLsb(bmp, stegoMessage)
                            }
                            else -> null
                        }
                        result = out
                        withContext(Dispatchers.Main) {
                            loading = false
                            if (out == null) {
                                message = "生成失败（请检查水印图是否已选择）"
                            } else {
                                message = ""
                                val f = FileSaver.saveBitmapToSource(context, sourceUri, out, "PNG", 100, "watermarked")
                                FileSaver.shareUri(context, f)
                            }
                        }
                    }
                }, modifier = Modifier.weight(1f))
                GlassChip("存预设", false, onClick = {
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
                })
            }
            if (presets.isNotEmpty()) {
                Row(Modifier.padding(top = 8.dp)) {
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
                            schedulingPreview()
                        })
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }
        }
    }
}

private fun scaleBitmap(src: Bitmap, maxDim: Int): Bitmap {
    val w = src.width
    val h = src.height
    val max = w.coerceAtLeast(h)
    if (max <= maxDim) return src
    val s = maxDim.toFloat() / max
    return Bitmap.createScaledBitmap(src, (w * s).toInt().coerceAtLeast(1), (h * s).toInt().coerceAtLeast(1), true)
}