package com.omni.image.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omni.image.engine.ExifEngine
import com.omni.image.model.ExifPreset
import com.omni.image.ui.theme.GlassOnBackground
import com.omni.image.util.FileSaver
import com.omni.image.util.PresetStorage
import com.omni.image.util.RecentFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val EDITABLE_TAGS = listOf(
    "Make", "Model", "Software", "Artist", "Copyright", "ImageDescription", "DateTime"
)

@Composable
fun ExifScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uri by remember { mutableStateOf<android.net.Uri?>(null) }
    var values by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var presetName by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val presets = remember { PresetStorage.listExifPresets(context) }

    LaunchedEffect(Unit) {
        val ch = ScreenChannels.exifUri
        if (ch != null) {
            ScreenChannels.exifUri = null
            uri = ch
            loaded = false
            scope.launch(Dispatchers.IO) {
                val raw = ExifEngine.getRawTags(context, ch)
                val labels = ExifEngine.READ_TAGS.associate { it.first to it.second }
                val editable = EDITABLE_TAGS.associateWith { raw[it] ?: "" }
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    values = editable
                    message = "共 ${raw.size} 个 EXIF 字段"
                    loaded = true
                    RecentFiles.add(context, ch.toString(), ch.lastPathSegment ?: "image")
                }
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) {
            uri = u
            loaded = false
            scope.launch(Dispatchers.IO) {
                val raw = ExifEngine.getRawTags(context, u)
                val labels = ExifEngine.READ_TAGS.associate { it.first to it.second }
                val labeled = raw.mapKeys { labels[it.key] ?: it.key }
                val editable = EDITABLE_TAGS.associateWith { raw[it] ?: "" }
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    values = editable
                    message = "共 ${raw.size} 个 EXIF 字段"
                    loaded = true
                    RecentFiles.add(context, u.toString(), u.lastPathSegment ?: "image")
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        GlassButton(if (uri == null) "选择图片" else "重新选择图片") {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        if (message.isNotEmpty()) {
            Text(message, fontSize = 12.sp, color = Color(0xFF9ECE6A), modifier = Modifier.padding(top = 8.dp))
        }

        if (loaded && uri != null) {
            Text("可编辑字段", color = com.omni.image.ui.theme.GlassSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(EDITABLE_TAGS) { tag ->
                    val label = ExifEngine.READ_TAGS.firstOrNull { it.first == tag }?.second ?: tag
                    OutlinedTextField(
                        value = values[tag] ?: "",
                        onValueChange = { values = values + (tag to it) },
                        label = { Text(label) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                GlassButton("应用修改", onClick = {
                    val u = uri ?: return@GlassButton
                    scope.launch(Dispatchers.IO) {
                        val ok = ExifEngine.writeFields(context, u, values)
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            message = if (ok) "EXIF 已写入（JPEG 直接写入，其他格式写入缓存副本）" else "写入失败"
                        }
                    }
                }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                GlassButton("清除 GPS", onClick = {
                    val u = uri ?: return@GlassButton
                    scope.launch(Dispatchers.IO) {
                        val ok = ExifEngine.clearGps(context, u)
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            message = if (ok) "GPS 已清除" else "清除失败"
                        }
                    }
                }, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                GlassButton("清除全部", onClick = {
                    val u = uri ?: return@GlassButton
                    scope.launch(Dispatchers.IO) {
                        val ok = ExifEngine.clearAll(context, u)
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            message = if (ok) "全部 EXIF 已清除" else "清除失败"
                        }
                    }
                }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                GlassButton("保存为预设", enabled = presetName.isNotBlank(), onClick = {
                    PresetStorage.saveExifPreset(context, ExifPreset(presetName, values.filterValues { it.isNotBlank() }))
                    presetName = ""
                    message = "预设已保存"
                }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(
                value = presetName,
                onValueChange = { presetName = it },
                label = { Text("预设名（保存当前字段）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )

            if (presets.isNotEmpty()) {
                Text("已有预设", color = com.omni.image.ui.theme.GlassSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                LazyColumn(Modifier.weight(1f)) {
                    items(presets) { p ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            GlassChip(p.name, false, onClick = {
                                val u = uri ?: return@GlassChip
                                scope.launch(Dispatchers.IO) {
                                    val ok = ExifEngine.writeFields(context, u, p.fields)
                                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                                        message = if (ok) "预设「${p.name}」已应用" else "应用失败"
                                    }
                                }
                            })
                        }
                    }
                }
            }
        } else if (uri != null) {
            Spacer(Modifier.height(20.dp))
            GlassButton("读取 EXIF", loading = !loaded) {
                val u = uri ?: return@GlassButton
                loaded = false
                scope.launch(Dispatchers.IO) {
                    val raw = ExifEngine.getRawTags(context, u)
                    val labels = ExifEngine.READ_TAGS.associate { it.first to it.second }
                    val editable = EDITABLE_TAGS.associateWith { raw[it] ?: "" }
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        values = editable
                        message = "共 ${raw.size} 个 EXIF 字段"
                        loaded = true
                    }
                }
            }
        }
    }
}
