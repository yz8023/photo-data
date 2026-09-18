package com.omni.image.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omni.image.engine.ConvertEngine
import com.omni.image.engine.FilterEngine
import com.omni.image.ui.theme.GlassOnBackground
import com.omni.image.util.FileSaver
import com.omni.image.util.PresetStorage
import com.omni.image.util.RecentFiles
import com.omni.image.util.UndoRedoStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            return@LaunchedEffect
        }
        previewComputing = true
        delay(180)
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
        if (current != null) undoStack.push(current)
        bitmap = newBitmap
        canUndo = undoStack.canUndo()
        canRedo = undoStack.canRedo()
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
        Row(Modifier.padding(top = 8.dp)) {
            IconButton(onClick = {
                bitmap?.let { bmp -> commit(undoStack.undo(bmp)); canUndo = undoStack.canUndo(); canRedo = undoStack.canRedo() }
            }, enabled = canUndo) {
                Icon(Icons.Filled.Undo, contentDescription = "撤销", tint = if (canUndo) Color.White else Color.Gray)
            }
            IconButton(onClick = {
                bitmap?.let { bmp -> commit(undoStack.redo(bmp)); canUndo = undoStack.canUndo(); canRedo = undoStack.canRedo() }
            }, enabled = canRedo) {
                Icon(Icons.Filled.Redo, contentDescription = "重做", tint = if (canRedo) Color.White else Color.Gray)
            }
            Spacer(Modifier.width(8.dp))
            GlassChip("重置参数", brightness != 0f || contrast != 1f, onClick = {
                brightness = 0f; contrast = 1f; saturation = 1f; colorTemp = 0f
                exposure = 0f; shadows = 0f; highlights = 0f
            })
        }

        ImagePreview(
            bitmap = livePreview ?: bitmap,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(top = 8.dp)
        )
        if (previewComputing) {
            Text("实时预览计算中…", fontSize = 11.sp, color = GlassOnBackground, modifier = Modifier.padding(top = 2.dp))
        }

        if (bitmap != null) {
            SectionTitle("旋转 / 翻转")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                val bmp = bitmap!!
                GlassChip("左转90°", false) {
                    loading = true
                    scope.launch(Dispatchers.IO) {
                        val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, android.graphics.Matrix().apply { postRotate(-90f) }, true)
                        withContext(Dispatchers.Main) { commit(r); loading = false }
                    }
                }
                Spacer(Modifier.width(8.dp))
                GlassChip("右转90°", false) {
                    loading = true
                    scope.launch(Dispatchers.IO) {
                        val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, android.graphics.Matrix().apply { postRotate(90f) }, true)
                        withContext(Dispatchers.Main) { commit(r); loading = false }
                    }
                }
                Spacer(Modifier.width(8.dp))
                GlassChip("旋转180°", false) {
                    loading = true
                    scope.launch(Dispatchers.IO) {
                        val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, android.graphics.Matrix().apply { postRotate(180f) }, true)
                        withContext(Dispatchers.Main) { commit(r); loading = false }
                    }
                }
                Spacer(Modifier.width(8.dp))
                GlassChip("水平翻转", false) {
                    loading = true
                    scope.launch(Dispatchers.IO) {
                        val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, android.graphics.Matrix().apply { postScale(-1f, 1f) }, true)
                        withContext(Dispatchers.Main) { commit(r); loading = false }
                    }
                }
                Spacer(Modifier.width(8.dp))
                GlassChip("垂直翻转", false) {
                    loading = true
                    scope.launch(Dispatchers.IO) {
                        val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, android.graphics.Matrix().apply { postScale(1f, -1f) }, true)
                        withContext(Dispatchers.Main) { commit(r); loading = false }
                    }
                }
            }

            SectionTitle("裁剪比例")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                listOf("", "1:1", "4:3", "16:9", "3:2", "A4").forEach { r ->
                    GlassChip(if (r.isEmpty()) "自由" else r, cropRatio == r, onClick = {
                        cropRatio = r
                        if (r.isNotEmpty()) {
                            val parts = r.split(":")
                            val ratio = parts[0].toFloat() / parts[1].toFloat()
                            val cur = bitmap ?: return@GlassChip
                            val newW = cur.width
                            val newH = (newW / ratio).toInt()
                            if (newH <= cur.height) {
                                val y = (cur.height - newH) / 2
                                commit(Bitmap.createBitmap(cur, 0, y, newW, newH))
                            } else {
                                val nh = cur.height
                                val nw = (nh * ratio).toInt()
                                val x = (cur.width - nw) / 2
                                commit(Bitmap.createBitmap(cur, x, 0, nw, nh))
                            }
                        }
                    })
                    Spacer(Modifier.width(8.dp))
                }
            }
            if (cropRatio.isEmpty()) {
                Row(Modifier.padding(top = 10.dp)) {
                    GlassButton("按自由选区裁剪（居中50%）", onClick = {
                        val cur = bitmap ?: return@GlassButton
                        val nw = (cur.width * 0.5f).toInt()
                        val nh = (cur.height * 0.5f).toInt()
                        val x = (cur.width - nw) / 2
                        val y = (cur.height - nh) / 2
                        commit(Bitmap.createBitmap(cur, x, y, nw, nh))
                    }, modifier = Modifier.weight(1f))
                }
            }

            SectionTitle("滤镜")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                FilterEngine.FilterType.entries.forEach { f ->
                    GlassChip(f.label, false, onClick = {
                        val cur = bitmap ?: return@GlassChip
                        loading = true
                        scope.launch(Dispatchers.IO) {
                            val r = FilterEngine.applyFilter(cur, f)
                            withContext(Dispatchers.Main) { commit(r); loading = false }
                        }
                    })
                    Spacer(Modifier.width(8.dp))
                }
            }

            SectionTitle("手动调节")
            DampedSlider(brightness, { brightness = it }, -1f..1f, label = "亮度", display = "${(brightness * 100).toInt()}")
            DampedSlider(contrast, { contrast = it }, 0.2f..2f, label = "对比度", display = "${(contrast * 100).toInt()}%")
            DampedSlider(saturation, { saturation = it }, 0f..2f, label = "饱和度", display = "${(saturation * 100).toInt()}%")
            DampedSlider(colorTemp, { colorTemp = it }, -1f..1f, label = "色温", display = "${(colorTemp * 100).toInt()}")
            DampedSlider(exposure, { exposure = it }, -1f..1f, label = "曝光", display = "${(exposure * 100).toInt()}")
            DampedSlider(shadows, { shadows = it }, 0f..1f, label = "阴影", display = "${(shadows * 100).toInt()}%")
            DampedSlider(highlights, { highlights = it }, 0f..1f, label = "高光", display = "${(highlights * 100).toInt()}%")
            GlassButton("应用调节参数", enabled = bitmap != null && loading.not()) {
                val base = bitmap ?: return@GlassButton
                val preview = livePreview ?: base
                commit(preview)
                brightness = 0f; contrast = 1f; saturation = 1f; colorTemp = 0f
                exposure = 0f; shadows = 0f; highlights = 0f
            }

            SectionTitle("曲线 / 色阶")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                listOf("提亮S曲线" to "s", "压暗反S" to "inv_s", "高对比" to "hi").forEach { (label, key) ->
                    GlassChip(label, false, onClick = {
                        val bmp = bitmap ?: return@GlassChip
                        loading = true
                        scope.launch(Dispatchers.IO) {
                            val r = when (key) {
                                "s" -> FilterEngine.applyCurve(bmp, listOf(0f, 0.35f, 0.5f, 0.65f, 1f), listOf(0f, 0.35f, 0.5f, 0.65f, 1f), listOf(0f, 0.35f, 0.5f, 0.65f, 1f))
                                "inv_s" -> FilterEngine.applyCurve(bmp, listOf(0f, 0.15f, 0.5f, 0.85f, 1f), listOf(0f, 0.15f, 0.5f, 0.85f, 1f), listOf(0f, 0.15f, 0.5f, 0.85f, 1f))
                                else -> FilterEngine.applyLevels(bmp, 30, 225, 1f, 0, 255)
                            }
                            withContext(Dispatchers.Main) { commit(r); loading = false }
                        }
                    })
                    Spacer(Modifier.width(8.dp))
                }
            }

            var presetName by remember { mutableStateOf("") }
            SectionTitle("滤镜预设")
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

            Spacer(Modifier.height(12.dp))
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
