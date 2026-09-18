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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omni.image.engine.ConvertEngine
import com.omni.image.engine.FilterEngine
import com.omni.image.ui.theme.GlassOnBackground
import com.omni.image.ui.theme.GlassOnSurface
import com.omni.image.ui.theme.GlassPrimary
import com.omni.image.util.FileSaver
import com.omni.image.util.PresetStorage
import com.omni.image.util.RecentFiles
import com.omni.image.util.UndoRedoStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val EDIT_TOOLS = listOf("旋转", "裁剪", "滤镜", "调节", "曲线", "预设")

@Composable
fun EditScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var sourceUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var loading by remember { mutableStateOf(false) }
    var cropRatio by remember { mutableStateOf("") }
    val undoStack = remember { UndoRedoStack() }
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }
    var tool by remember { mutableStateOf<String?>(null) }

    var brightness by remember { mutableStateOf(0f) }
    var contrast by remember { mutableStateOf(1f) }
    var saturation by remember { mutableStateOf(1f) }
    var colorTemp by remember { mutableStateOf(0f) }
    var exposure by remember { mutableStateOf(0f) }
    var shadows by remember { mutableStateOf(0f) }
    var highlights by remember { mutableStateOf(0f) }

    // 实时预览：滑块参数变化时防抖重算预览图
    var livePreview by remember { mutableStateOf<Bitmap?>(null) }
    var previewComputing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val uri = ScreenChannels.editUri
        if (uri != null) {
            ScreenChannels.editUri = null
            sourceUri = uri
            bitmap = ConvertEngine.decodeUri(context, uri)
            livePreview = bitmap
            RecentFiles.add(context, uri.toString(), uri.lastPathSegment ?: "image")
        }
    }
    LaunchedEffect(brightness, contrast, saturation, colorTemp, exposure, shadows, highlights, bitmap) {
        val base = bitmap
        if (base == null) {
            livePreview = null
            return@LaunchedEffect
        }
        val b = brightness; val c = contrast; val s = saturation
        val t = colorTemp; val e = exposure; val sh = shadows; val hi = highlights
        if (b == 0f && c == 1f && s == 1f && t == 0f && e == 0f && sh == 0f && hi == 0f) {
            livePreview = base
            previewComputing = false
            return@LaunchedEffect
        }
        previewComputing = true
        delay(120)
        val result = withContext(Dispatchers.Default) {
            FilterEngine.adjust(base, FilterEngine.AdjustParams().apply {
                brightness = b; contrast = c; saturation = s
                colorTemp = t; exposure = e; shadows = sh; highlights = hi
            })
        }
        livePreview = result
        previewComputing = false
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            sourceUri = uri
            bitmap = ConvertEngine.decodeUri(context, uri)
            RecentFiles.add(context, uri.toString(), uri.lastPathSegment ?: "image")
        }
    }

    fun commit(newBitmap: Bitmap) {
        val current = bitmap
        if (current != null && current !== newBitmap) undoStack.push(current)
        bitmap = newBitmap
        livePreview = newBitmap
        canUndo = undoStack.canUndo()
        canRedo = undoStack.canRedo()
    }

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassButton(if (bitmap == null) "选择图片" else "重选图片", onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = {
                bitmap?.let { bmp -> commit(undoStack.undo(bmp)); canUndo = undoStack.canUndo(); canRedo = undoStack.canRedo() }
            }, enabled = canUndo) {
                Icon(Icons.Filled.Undo, contentDescription = "撤销", tint = if (canUndo) GlassOnSurface else Color.Gray)
            }
            IconButton(onClick = {
                bitmap?.let { bmp -> commit(undoStack.redo(bmp)); canUndo = undoStack.canUndo(); canRedo = undoStack.canRedo() }
            }, enabled = canRedo) {
                Icon(Icons.Filled.Redo, contentDescription = "重做", tint = if (canRedo) GlassOnSurface else Color.Gray)
            }
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
                Text("点击上方按钮选择图片", color = GlassOnBackground, fontSize = 14.sp, textAlign = TextAlign.Center)
            } else {
                ImagePreview(
                    bitmap = livePreview ?: bitmap,
                    modifier = Modifier.fillMaxSize()
                )
                if (previewComputing) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .heightIn(min = 24.dp)
                            .background(androidx.compose.ui.graphics.Color(0x99000000), androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("实时预览计算中…", fontSize = 11.sp, color = Color.White)
                    }
                }
            }
        }

        // ===== 底部：功能按钮，点击展开 =====
        if (bitmap != null) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EDIT_TOOLS.forEach { t ->
                    GlassChip(t, selected = tool == t, onClick = { tool = if (tool == t) null else t })
                    Spacer(Modifier.width(8.dp))
                }
            }

            AnimatedVisibility(
                visible = tool != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .padding(top = 8.dp)
                        .background(androidx.compose.ui.graphics.Color(0x40FFFFFF), androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                ) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)
                    ) {
                        when (tool) {
                            "旋转" -> RotationControls(bitmap!!, { new -> commit(new) }, { v -> loading = v }, scope)
                            "裁剪" -> CropControls(bitmap!!, cropRatio, { cropRatio = it }, { new -> commit(new) })
                            "滤镜" -> FilterControls(bitmap!!, { new -> commit(new) }, { v -> loading = v }, scope)
                            "调节" -> AdjustControls(
                                brightness, contrast, saturation, colorTemp, exposure, shadows, highlights,
                                { brightness = it }, { contrast = it }, { saturation = it },
                                { colorTemp = it }, { exposure = it }, { shadows = it }, { highlights = it },
                                onApply = {
                                    val base = bitmap ?: return@AdjustControls
                                    val p = livePreview ?: base
                                    commit(p)
                                    brightness = 0f; contrast = 1f; saturation = 1f; colorTemp = 0f
                                    exposure = 0f; shadows = 0f; highlights = 0f; tool = null
                                }
                            )
                            "曲线" -> CurveControls(bitmap!!, { new -> commit(new) }, { v -> loading = v }, scope)
                            "预设" -> PresetControls(context, brightness, contrast, saturation, colorTemp, exposure, shadows, highlights)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            GlassButton("保存结果", enabled = bitmap != null, loading = loading) {
                val bmp = bitmap ?: return@GlassButton
                loading = true
                scope.launch(Dispatchers.IO) {
                    val result = FileSaver.saveBitmapToSource(context, sourceUri, bmp, "PNG", 100, "edit")
                    withContext(Dispatchers.Main) {
                        loading = false
                        FileSaver.shareUri(context, result)
                    }
                }
            }
        }
    }
}

@Composable
private fun RotationControls(
    bmp: Bitmap,
    commit: (Bitmap) -> Unit,
    setLoading: (Boolean) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        listOf("左转90°" to -90f, "右转90°" to 90f, "旋转180°" to 180f).forEach { (label, deg) ->
            GlassChip(label, false, onClick = {
                setLoading(true)
                scope.launch(Dispatchers.IO) {
                    val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, android.graphics.Matrix().apply { postRotate(deg) }, true)
                    withContext(Dispatchers.Main) { commit(r); setLoading(false) }
                }
            })
            Spacer(Modifier.width(8.dp))
        }
        GlassChip("水平翻转", false, onClick = {
            setLoading(true)
            scope.launch(Dispatchers.IO) {
                val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, android.graphics.Matrix().apply { postScale(-1f, 1f) }, true)
                withContext(Dispatchers.Main) { commit(r); setLoading(false) }
            }
        })
        Spacer(Modifier.width(8.dp))
        GlassChip("垂直翻转", false, onClick = {
            setLoading(true)
            scope.launch(Dispatchers.IO) {
                val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, android.graphics.Matrix().apply { postScale(1f, -1f) }, true)
                withContext(Dispatchers.Main) { commit(r); setLoading(false) }
            }
        })
    }
}

@Composable
private fun CropControls(
    cur: Bitmap,
    cropRatio: String,
    setCropRatio: (String) -> Unit,
    commit: (Bitmap) -> Unit
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        listOf("", "1:1", "4:3", "16:9", "3:2", "A4").forEach { r ->
            GlassChip(if (r.isEmpty()) "自由" else r, cropRatio == r, onClick = {
                setCropRatio(r)
                if (r.isNotEmpty()) {
                    val parts = r.split(":")
                    val ratio = parts[0].toFloat() / parts[1].toFloat()
                    val nw = cur.width
                    val nh = (nw / ratio).toInt()
                    if (nh <= cur.height) {
                        val y = (cur.height - nh) / 2
                        commit(Bitmap.createBitmap(cur, 0, y, nw, nh))
                    } else {
                        val nh2 = cur.height
                        val nw2 = (nh2 * ratio).toInt()
                        val x = (cur.width - nw2) / 2
                        commit(Bitmap.createBitmap(cur, x, 0, nw2, nh2))
                    }
                }
            })
            Spacer(Modifier.width(8.dp))
        }
    }
    if (cropRatio.isEmpty()) {
        GlassButton("按自由选区裁剪（居中50%）", modifier = Modifier.padding(top = 10.dp), onClick = {
            val nw = (cur.width * 0.5f).toInt()
            val nh = (cur.height * 0.5f).toInt()
            val x = (cur.width - nw) / 2
            val y = (cur.height - nh) / 2
            commit(Bitmap.createBitmap(cur, x, y, nw, nh))
        })
    }
}

@Composable
private fun FilterControls(
    cur: Bitmap,
    commit: (Bitmap) -> Unit,
    setLoading: (Boolean) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        FilterEngine.FilterType.entries.forEach { f ->
            GlassChip(f.label, false, onClick = {
                setLoading(true)
                scope.launch(Dispatchers.IO) {
                    val r = FilterEngine.applyFilter(cur, f)
                    withContext(Dispatchers.Main) { commit(r); setLoading(false) }
                }
            })
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
private fun AdjustControls(
    brightness: Float, contrast: Float, saturation: Float,
    colorTemp: Float, exposure: Float, shadows: Float, highlights: Float,
    setBrightness: (Float) -> Unit, setContrast: (Float) -> Unit, setSaturation: (Float) -> Unit,
    setColorTemp: (Float) -> Unit, setExposure: (Float) -> Unit, setShadows: (Float) -> Unit,
    setHighlights: (Float) -> Unit,
    onApply: () -> Unit
) {
    DampedSlider(brightness, setBrightness, -1f..1f, label = "亮度", display = "${(brightness * 100).toInt()}")
    DampedSlider(contrast, setContrast, 0.2f..2f, label = "对比度", display = "${(contrast * 100).toInt()}%")
    DampedSlider(saturation, setSaturation, 0f..2f, label = "饱和度", display = "${(saturation * 100).toInt()}%")
    DampedSlider(colorTemp, setColorTemp, -1f..1f, label = "色温", display = "${(colorTemp * 100).toInt()}")
    DampedSlider(exposure, setExposure, -1f..1f, label = "曝光", display = "${(exposure * 100).toInt()}")
    DampedSlider(shadows, setShadows, 0f..1f, label = "阴影", display = "${(shadows * 100).toInt()}%")
    DampedSlider(highlights, setHighlights, 0f..1f, label = "高光", display = "${(highlights * 100).toInt()}%")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        GlassButton("应用调节", onClick = onApply, modifier = Modifier.weight(1f))
        GlassChip("重置", false, onClick = {
            setBrightness(0f); setContrast(1f); setSaturation(1f); setColorTemp(0f)
            setExposure(0f); setShadows(0f); setHighlights(0f)
        })
    }
}

@Composable
private fun CurveControls(
    cur: Bitmap,
    commit: (Bitmap) -> Unit,
    setLoading: (Boolean) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        listOf("提亮S曲线" to "s", "压暗反S" to "inv_s", "高对比" to "hi").forEach { (label, key) ->
            GlassChip(label, false, onClick = {
                setLoading(true)
                scope.launch(Dispatchers.IO) {
                    val r = when (key) {
                        "s" -> FilterEngine.applyCurve(cur, listOf(0f, 0.35f, 0.5f, 0.65f, 1f), listOf(0f, 0.35f, 0.5f, 0.65f, 1f), listOf(0f, 0.35f, 0.5f, 0.65f, 1f))
                        "inv_s" -> FilterEngine.applyCurve(cur, listOf(0f, 0.15f, 0.5f, 0.85f, 1f), listOf(0f, 0.15f, 0.5f, 0.85f, 1f), listOf(0f, 0.15f, 0.5f, 0.85f, 1f))
                        else -> FilterEngine.applyLevels(cur, 30, 225, 1f, 0, 255)
                    }
                    withContext(Dispatchers.Main) { commit(r); setLoading(false) }
                }
            })
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
private fun PresetControls(
    context: android.content.Context,
    brightness: Float, contrast: Float, saturation: Float,
    colorTemp: Float, exposure: Float, shadows: Float, highlights: Float
) {
    var presetName by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = presetName,
            onValueChange = { presetName = it },
            label = { Text("预设名") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        GlassChip("保存", false, onClick = {
            if (presetName.isNotBlank()) {
                PresetStorage.saveFilterPreset(context, presetName, FilterEngine.AdjustParams().let {
                    mapOf("brightness" to brightness, "contrast" to contrast, "saturation" to saturation, "colorTemp" to colorTemp, "exposure" to exposure, "shadows" to shadows, "highlights" to highlights)
                })
            }
        })
    }
}