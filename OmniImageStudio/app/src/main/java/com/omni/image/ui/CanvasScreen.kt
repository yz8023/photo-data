package com.omni.image.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omni.image.engine.LayerEngine
import com.omni.image.engine.SymmetryBrush
import com.omni.image.model.Layer
import com.omni.image.ui.theme.GlassOnBackground
import com.omni.image.ui.theme.GlassOutline
import com.omni.image.ui.theme.GlassPrimary
import com.omni.image.ui.theme.GlassSecondary
import com.omni.image.ui.theme.GlassSuccess
import com.omni.image.util.FileSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CanvasScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var canvasW by remember { mutableStateOf("1024") }
    var canvasH by remember { mutableStateOf("1024") }
    var initialized by remember { mutableStateOf(false) }
    var background by remember { mutableStateOf(0xFF000000.toInt()) }

    var layers by remember { mutableStateOf<List<Layer>>(emptyList()) }
    var activeIdx by remember { mutableStateOf(0) }

    var brushColor by remember { mutableStateOf(Color.White) }
    var brushSize by remember { mutableStateOf(8f) }
    var softness by remember { mutableStateOf(0f) }
    var mode by remember { mutableStateOf(SymmetryBrush.Mode.VERTICAL) }
    var lines by remember { mutableStateOf(6f) }
    var angleOffset by remember { mutableStateOf(0f) }

    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var drawing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    fun currentBitmap(): Bitmap? = layers.getOrNull(activeIdx)?.bitmap

    fun initCanvas() {        val w = canvasW.toIntOrNull()?.coerceIn(16, 8192) ?: 1024
        val h = canvasH.toIntOrNull()?.coerceIn(16, 8192) ?: 1024
        canvasW = w.toString()
        canvasH = h.toString()
        val base = LayerEngine.newCanvas(w, h, background)
        layers = listOf(Layer(name = "图层 1", bitmap = base))
        activeIdx = 0
        initialized = true
    }

    fun commitStroke(points: List<Offset>) {
        if (points.isEmpty()) return
        val bmp = currentBitmap() ?: return
        val w = bmp.width.toFloat()
        val h = bmp.height.toFloat()
        val stroke = SymmetryBrush.Stroke(
            points = points.map { android.graphics.PointF(it.x, it.y) },
            paint = SymmetryBrush.buildPaint(brushSize, brushColor.toArgb().toLong()),
            width = brushSize,
            color = brushColor.toArgb().toLong(),
            softness = softness
        )
        val canvas = android.graphics.Canvas(bmp)
        SymmetryBrush.drawSymmetry(canvas, stroke, mode, lines.toInt(), w / 2, h / 2, angleOffset)
        val newLayer = layers[activeIdx].copy()
        layers = layers.toMutableList().also { it[activeIdx] = newLayer }
    }

    if (!initialized) {
        Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            GlassCard {
                Text("新建画布", color = Color(0xFFE6E6FF), fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedTextField(canvasW, { canvasW = it }, label = { Text("宽 (px)") }, singleLine = true, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(canvasH, { canvasH = it }, label = { Text("高 (px)") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Text("比例预设", color = GlassSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                Row(Modifier.fillMaxWidth()) {
                    listOf("1:1" to Pair("1024", "1024"), "9:16" to Pair("1080", "1920"), "16:9" to Pair("1920", "1080"), "A4" to Pair("2480", "3508"), "3:2" to Pair("1500", "1000"), "4:3" to Pair("1600", "1200")).forEach { (label, size) ->
                        GlassChip(label, false, onClick = { canvasW = size.first; canvasH = size.second })
                        Spacer(Modifier.width(6.dp))
                    }
                }
                Text("背景色", color = GlassSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                Row(Modifier.fillMaxWidth()) {
                    listOf(0xFF000000.toInt() to "透明", 0xFFFFFFFF.toInt() to "白色", 0xFF1A1B26.toInt() to "深色").forEach { (c, label) ->
                        GlassChip(label, background == c, onClick = { background = c })
                        Spacer(Modifier.width(8.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
                GlassButton("创建画布", onClick = { initCanvas() })
            }
        }
        return
    }

    val bmp = currentBitmap() ?: return
    val ratio = bmp.width.toFloat() / bmp.height.toFloat()
    val brushArgb = brushColor.toArgb()

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        GlassCard(Modifier.fillMaxWidth()) {
            Text("画笔设置", color = GlassSecondary, fontSize = 13.sp)
            ColorPickerPanel(brushColor, { brushColor = it }, Modifier.padding(top = 4.dp))
            DampedSlider(brushSize, { brushSize = it }, 1f..80f, label = "笔刷大小", display = "${brushSize.toInt()} px")
            DampedSlider(softness, { softness = it }, 0f..1f, label = "柔边", display = "${(softness * 100).toInt()}%")
            Text("对称模式", color = GlassSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            Row(Modifier.fillMaxWidth()) {
                SymmetryBrush.Mode.entries.forEach { m ->
                    GlassChip(m.label, mode == m, onClick = { mode = m })
                    Spacer(Modifier.width(6.dp))
                }
            }
            if (mode == SymmetryBrush.Mode.RADIAL || mode == SymmetryBrush.Mode.KALEIDOSCOPE) {
                DampedSlider(lines, { lines = it }, 2f..24f, label = "对称线数", display = "${lines.toInt()}")
                DampedSlider(angleOffset, { angleOffset = it }, 0f..360f, label = "起始角度", display = "${angleOffset.toInt()}°")
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .padding(vertical = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(background))
                .border(1.dp, GlassOutline, RoundedCornerShape(12.dp))
                .pointerInput(activeIdx, bmp.width, bmp.height) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            drawing = true
                            currentPoints = listOf(scaleToBitmap(offset, size.width.toFloat(), size.height.toFloat(), bmp.width, bmp.height))
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            if (drawing) {
                                currentPoints = currentPoints + scaleToBitmap(change.position, size.width.toFloat(), size.height.toFloat(), bmp.width, bmp.height)
                            }
                        },
                        onDragEnd = {
                            drawing = false
                            commitStroke(currentPoints)
                            currentPoints = emptyList()
                        },
                        onDragCancel = {
                            drawing = false
                            currentPoints = emptyList()
                        }
                    )
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                layers.forEachIndexed { idx, layer ->
                    if (layer.visible && layer.bitmap != null) {
                        drawImage(
                            layer.bitmap.asImageBitmap(),
                            dstSize = androidx.compose.ui.unit.IntSize(this.size.width.toInt(), this.size.height.toInt())
                        )
                    }
                    if (idx == activeIdx) {
                        drawRect(GlassPrimary.copy(alpha = 0.25f), style = Stroke(3f))
                    }
                }
                if (currentPoints.isNotEmpty()) {
                    drawSymmetryPreview(currentPoints, brushArgb, brushSize, mode, lines.toInt())
                }
            }
        }

        Text("图层", color = GlassSecondary, fontSize = 13.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            layers.forEachIndexed { idx, layer ->
                val isActive = idx == activeIdx
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isActive) GlassPrimary else androidx.compose.ui.graphics.Color(0x33FFFFFF))
                        .border(1.dp, if (isActive) androidx.compose.ui.graphics.Color.Transparent else GlassOutline, RoundedCornerShape(12.dp))
                        .pointerInput(idx) { detectTapGestures { activeIdx = idx } }
                        .padding(10.dp)
                ) {
                    Text(
                        "${layer.name}${if (!layer.visible) " (隐藏)" else ""}",
                        color = if (isActive) androidx.compose.ui.graphics.Color(0xFF1A1B26) else GlassOnBackground,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            GlassChip("新建图层", false, onClick = {
                val bmp = currentBitmap() ?: return@GlassChip
                val blank = LayerEngine.newCanvas(bmp.width, bmp.height, android.graphics.Color.TRANSPARENT)
                layers = layers + Layer(name = "图层 ${layers.size + 1}", bitmap = blank)
                activeIdx = layers.lastIndex
            })
            Spacer(Modifier.width(6.dp))
            GlassChip("隐藏/显示", false, onClick = {
                if (layers.isEmpty()) return@GlassChip
                val cur = layers[activeIdx]
                layers = layers.toMutableList().also { it[activeIdx] = cur.copy(visible = !cur.visible) }
            })
            Spacer(Modifier.width(6.dp))
            GlassChip("合并到下层", false, onClick = {
                if (activeIdx == 0 || layers.isEmpty()) return@GlassChip
                layers = LayerEngine.mergeLayers(layers, activeIdx)
                activeIdx = (activeIdx - 1).coerceAtLeast(0)
            })
            Spacer(Modifier.width(6.dp))
            GlassChip("删除图层", false, onClick = {
                if (layers.size <= 1) {
                    message = "至少保留一个图层"
                    return@GlassChip
                }
                val cur = layers[activeIdx]
                cur.bitmap?.recycle()
                layers = layers.toMutableList().also { it.removeAt(activeIdx) }
                activeIdx = activeIdx.coerceAtMost(layers.lastIndex)
            })
        }

        Spacer(Modifier.height(12.dp))
        GlassButton("保存为 PNG", onClick = {
            scope.launch(Dispatchers.IO) {
                val flat = LayerEngine.flatten(layers, bmp.width, bmp.height, background)
                if (flat != null) {
                    val f = FileSaver.saveBitmap(context, flat, "PNG", 100, "canvas")
                    withContext(Dispatchers.Main) {
                        message = "已保存: ${f.name}"
                        FileSaver.shareFile(context, f)
                    }
                }
            }
        })
        if (message.isNotEmpty()) {
            Text(message, fontSize = 12.sp, color = GlassSuccess, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

private fun scaleToBitmap(offset: Offset, canvasW: Float, canvasH: Float, bmpW: Int, bmpH: Int): Offset {
    val x = (offset.x / canvasW * bmpW).coerceIn(0f, bmpW - 1f)
    val y = (offset.y / canvasH * bmpH).coerceIn(0f, bmpH - 1f)
    return Offset(x, y)
}

private fun DrawScope.drawSymmetryPreview(
    points: List<Offset>,
    color: Int,
    width: Float,
    mode: SymmetryBrush.Mode,
    lines: Int
) {
    val path = Path()
    path.moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) {
        path.lineTo(points[i].x, points[i].y)
    }
    val cx = this.size.width / 2
    val cy = this.size.height / 2
    val style = Stroke(width)

    fun draw() {
        drawPath(path, color = Color(color), style = style)
    }

    draw()
    when (mode) {
        SymmetryBrush.Mode.VERTICAL -> {
            withTransform({ scale(scaleX = -1f, scaleY = 1f, pivot = Offset(cx, cy)) }) { draw() }
        }
        SymmetryBrush.Mode.HORIZONTAL -> {
            withTransform({ scale(scaleX = 1f, scaleY = -1f, pivot = Offset(cx, cy)) }) { draw() }
        }
        SymmetryBrush.Mode.QUADRANT -> {
            withTransform({ scale(scaleX = -1f, scaleY = -1f, pivot = Offset(cx, cy)) }) { draw() }
        }
        SymmetryBrush.Mode.RADIAL -> {
            val step = 360f / lines.coerceAtLeast(2)
            for (i in 1 until lines.coerceAtLeast(2)) {
                withTransform({ rotate(i * step, pivot = Offset(cx, cy)) }) { draw() }
            }
        }
        SymmetryBrush.Mode.KALEIDOSCOPE -> {
            val step = 360f / lines.coerceAtLeast(2)
            for (i in 1 until lines.coerceAtLeast(2)) {
                withTransform({ rotate(i * step, pivot = Offset(cx, cy)) }) { draw() }
            }
            for (i in 0 until lines.coerceAtLeast(2)) {
                withTransform({
                    rotate(i * step, pivot = Offset(cx, cy))
                    scale(scaleX = -1f, scaleY = 1f, pivot = Offset(cx, cy))
                }) { draw() }
            }
        }
    }
}
