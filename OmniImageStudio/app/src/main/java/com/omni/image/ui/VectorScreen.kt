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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omni.image.engine.ConvertEngine
import com.omni.image.engine.VectorizeEngine
import com.omni.image.ui.theme.GlassOnBackground
import com.omni.image.util.FileSaver
import com.omni.image.util.RecentFiles
import com.omni.image.vector.SvgParser
import com.omni.image.vector.SvgRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun VectorScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var sourceUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var svgText by remember { mutableStateOf("") }
    var xmlText by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var threshold by remember { mutableStateOf(128f) }
    var colorLayers by remember { mutableStateOf(1f) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("描摹") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            sourceUri = uri
            bitmap = ConvertEngine.decodeUri(context, uri)
            RecentFiles.add(context, uri.toString(), uri.lastPathSegment ?: "image")
            svgText = ""
            xmlText = ""
            preview = null
        }
    }

    val svgPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (text != null) {
                    val shapes = SvgParser.parse(text)
                    val vb = SvgParser.viewBox(text)
                    val bmp = SvgRenderer.renderShapes(shapes, vb?.first ?: 512, vb?.second ?: 512)
                    withContext(Dispatchers.Main) {
                        svgText = text
                        xmlText = VectorizeEngine.svgToVectorDrawableXml(text) ?: "解析失败：仅支持 path/rect/circle/ellipse/line/polygon"
                        preview = bmp
                        message = "SVG 已解析，含 ${shapes.size} 个图元"
                        tab = "预览"
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(Modifier.fillMaxWidth()) {
            GlassButton(if (bitmap == null) "选择位图描摹" else "重新选择位图", onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            GlassButton("打开 SVG", onClick = {
                svgPicker.launch("*/*")
            }, modifier = Modifier.weight(1f))
        }

        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            listOf("描摹", "预览", "SVG 源码", "XML 导出").forEach { t ->
                GlassChip(t, tab == t, onClick = { tab = t })
                Spacer(Modifier.width(8.dp))
            }
        }

        when (tab) {
            "描摹" -> {
                ImagePreview(bitmap, Modifier.fillMaxWidth().height(180.dp).padding(top = 8.dp))
                if (bitmap != null) {
                    SectionTitle("描摹参数")
                    DampedSlider(threshold, { threshold = it }, 0f..255f, label = "二值化阈值", display = "${threshold.toInt()}")
                    DampedSlider(colorLayers, { colorLayers = it }, 1f..6f, label = "颜色分层", display = "${colorLayers.toInt()} 层")
                    GlassButton("开始矢量化", loading = loading, enabled = bitmap != null) {
                        val bmp = bitmap ?: return@GlassButton
                        loading = true
                        scope.launch(Dispatchers.IO) {
                            val opts = VectorizeEngine.TraceOptions(
                                threshold = threshold.toInt(),
                                colorLayers = colorLayers.toInt(),
                                simplifyTolerance = 0.5f
                            )
                            val svg = VectorizeEngine.vectorizeToSvg(bmp, opts)
                            val xml = VectorizeEngine.vectorizeToVectorDrawable(bmp, opts)
                            val vb = SvgParser.viewBox(svg)
                            val pv = SvgRenderer.renderShapes(SvgParser.parse(svg), vb?.first ?: bmp.width, vb?.second ?: bmp.height)
                            withContext(Dispatchers.Main) {
                                svgText = svg
                                xmlText = xml
                                preview = pv
                                loading = false
                                message = "描摹完成"
                                tab = "预览"
                            }
                        }
                    }
                }
            }
            "预览" -> {
                if (preview != null) {
                    ImagePreview(preview, Modifier.fillMaxWidth().height(240.dp).padding(top = 8.dp))
                    Row(Modifier.padding(top = 10.dp)) {
                        GlassButton("导出 PNG", onClick = {
                            val p = preview ?: return@GlassButton
                            scope.launch(Dispatchers.IO) {
                                val f = FileSaver.saveBitmapToSource(context, sourceUri, p, "PNG", 100, "svg_render")
                                withContext(Dispatchers.Main) { FileSaver.shareUri(context, f) }
                            }
                        }, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        GlassButton("导出 PDF", onClick = {
                            val p = preview ?: return@GlassButton
                            scope.launch(Dispatchers.IO) {
                                val f = PdfHelper.exportPdf(context, listOf(p), "vector_page")
                                withContext(Dispatchers.Main) { FileSaver.shareFile(context, f) }
                            }
                        }, modifier = Modifier.weight(1f))
                    }
                } else {
                    Text("暂无预览", color = GlassOnBackground, modifier = Modifier.padding(top = 16.dp))
                }
            }
            "SVG 源码" -> {
                if (svgText.isNotEmpty()) {
                    SelectionContainer {
                        Text(svgText, fontSize = 11.sp, color = GlassOnBackground, modifier = Modifier.padding(top = 8.dp))
                    }
                    Row(Modifier.padding(top = 10.dp)) {
                        GlassButton("复制", onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(svgText)) }, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        GlassButton("保存 .svg", onClick = {
                            scope.launch(Dispatchers.IO) {
                                val f = FileSaver.saveTextToSource(context, sourceUri, svgText, "svg", "vectorized")
                                withContext(Dispatchers.Main) { FileSaver.shareUri(context, f) }
                            }
                        }, modifier = Modifier.weight(1f))
                    }
                }
            }
            "XML 导出" -> {
                if (xmlText.isNotEmpty()) {
                    SelectionContainer {
                        Text(xmlText, fontSize = 11.sp, color = GlassOnBackground, modifier = Modifier.padding(top = 8.dp))
                    }
                    Row(Modifier.padding(top = 10.dp)) {
                        GlassButton("复制", onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(xmlText)) }, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        GlassButton("保存 .xml", onClick = {
                            scope.launch(Dispatchers.IO) {
                                val f = FileSaver.saveTextToSource(context, sourceUri, xmlText, "xml", "vector_drawable")
                                withContext(Dispatchers.Main) { FileSaver.shareUri(context, f) }
                            }
                        }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        if (message.isNotEmpty()) {
            Text(message, fontSize = 12.sp, color = Color(0xFF9ECE6A), modifier = Modifier.padding(top = 10.dp))
        }
    }
}

object PdfHelper {
    fun exportPdf(context: android.content.Context, bitmaps: List<Bitmap>, prefix: String): java.io.File {
        val pdf = android.graphics.pdf.PdfDocument()
        for (bmp in bitmaps) {
            val page = pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(bmp.width, bmp.height, pdf.pages.size + 1).create())
            page.canvas.drawBitmap(bmp, 0f, 0f, null)
            pdf.finishPage(page)
        }
        val out = FileSaver.outputDir(context).resolve(FileSaver.uniqueName(prefix, "pdf"))
        java.io.FileOutputStream(out).use { pdf.writeTo(it) }
        pdf.close()
        return out
    }
}
