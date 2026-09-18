package com.omni.image.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omni.image.ui.theme.GlassOnBackground
import com.omni.image.ui.theme.GlassPrimary
import com.omni.image.ui.theme.GlassSecondary
import com.omni.image.ui.theme.GlassSurfaceStrong
import com.omni.image.util.RecentFiles

sealed class Screen {
    object Main : Screen()
    object Convert : Screen()
    object Edit : Screen()
    object Exif : Screen()
    object Vector : Screen()
    object Watermark : Screen()
    object Canvas : Screen()
    object Batch : Screen()
    object Album : Screen()
    data class AlbumViewer(val photos: List<AlbumPhoto>, val startIndex: Int) : Screen()
}

/** 相册 → 功能页的图片传递通道（属性名与 [ConvertEngine] 无冲突）。 */
object ScreenChannels {
    @JvmStatic var editUri: android.net.Uri? = null
    @JvmStatic var convertUri: android.net.Uri? = null
    @JvmStatic var exifUri: android.net.Uri? = null
}

private data class Feature(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val screen: Screen
)

private val FEATURES = listOf(
    Feature("相册系统", "像手机相册一样选取 / 查看图片", Icons.Filled.PhotoLibrary, Screen.Album),
    Feature("格式转换", "PNG/JPEG/WEBP/BMP/ICO · SVG/XML", Icons.Filled.SwapHoriz, Screen.Convert),
    Feature("裁剪与调节", "裁剪/旋转/滤镜/曲线/色阶", Icons.Filled.AutoFixHigh, Screen.Edit),
    Feature("EXIF 编辑", "查看/编辑/批量 · GPS 清除", Icons.Filled.Info, Screen.Exif),
    Feature("矢量与描摹", "SVG 预览 · 位图矢量化", Icons.Filled.IosShare, Screen.Vector),
    Feature("水印系统", "文字/图片 · 平铺 · 盲水印", Icons.Filled.WaterDrop, Screen.Watermark),
    Feature("画布绘制", "对称画笔 · 图层 · 色盘", Icons.Filled.Brush, Screen.Canvas),
    Feature("批量处理", "批量转换/改名/水印/EXIF", Icons.Filled.Inventory2, Screen.Batch),
    Feature("关于", "项目信息与开源说明", Icons.Filled.Info, Screen.Main)
)

@Composable
fun MainScreen() {
    var current by remember { mutableStateOf<Screen>(Screen.Main) }
    val context = LocalContext.current
    val recent by remember {
        mutableStateOf(RecentFiles.list(context))
    }

    when (val screen = current) {
        Screen.Main -> Home(
            recentFiles = recent,
            onNavigate = { current = it }
        )
        Screen.Convert -> AppScaffold("格式转换", onBack = { current = Screen.Main }) {
            AppScreenBox { ConvertScreen() }
        }
        Screen.Edit -> AppScaffold("裁剪与色彩调节", onBack = { current = Screen.Main }) {
            AppScreenBox { EditScreen() }
        }
        Screen.Exif -> AppScaffold("EXIF 编辑", onBack = { current = Screen.Main }) {
            AppScreenBox { ExifScreen() }
        }
        Screen.Vector -> AppScaffold("矢量化与 SVG", onBack = { current = Screen.Main }) {
            AppScreenBox { VectorScreen() }
        }
        Screen.Watermark -> AppScaffold("水印系统", onBack = { current = Screen.Main }) {
            AppScreenBox { WatermarkScreen() }
        }
        Screen.Canvas -> AppScaffold("画布绘制", onBack = { current = Screen.Main }) {
            AppScreenBox { CanvasScreen() }
        }
        Screen.Batch -> AppScaffold("批量处理", onBack = { current = Screen.Main }) {
            AppScreenBox { BatchScreen() }
        }
        Screen.Album -> AppScaffold("相册系统", onBack = { current = Screen.Main }) {
            AppScreenBox {
                AlbumScreen(
                    onPick = { uri ->
                        val photos = AlbumReader.loadPhotos(context)
                        val idx = photos.indexOfFirst { it.uri == uri }
                        if (idx >= 0) {
                            current = Screen.AlbumViewer(photos, idx)
                        }
                    }
                )
            }
        }
        is Screen.AlbumViewer -> AppScaffold("图片查看", onBack = { current = Screen.Album }) {
            AppScreenBox {
                AlbumViewer(
                    photos = screen.photos,
                    startIndex = screen.startIndex,
                    onBack = { current = Screen.Album },
                    onEdit = { uri ->
                        ScreenChannels.editUri = uri
                        current = Screen.Edit
                    },
                    onConvert = { uri ->
                        ScreenChannels.convertUri = uri
                        current = Screen.Convert
                    },
                    onExif = { uri ->
                        ScreenChannels.exifUri = uri
                        current = Screen.Exif
                    }
                )
            }
        }
    }
}

@Composable
private fun Home(
    recentFiles: List<Pair<String, String>>,
    onNavigate: (Screen) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(0xFF1A1B26))
    ) {
        com.omni.image.ui.theme.AuroraBackground(Modifier.fillMaxSize()) { }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 48.dp, bottom = 112.dp)
        ) {
        item {
            Text(
                "OmniImage Studio",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFE6E6FF)
            )
            Text(
                "纯离线全能图像工具 · 格式转换 / EXIF / 画布 / 矢量化 / 水印",
                color = GlassOnBackground,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
        }
        item {
            Column(Modifier.fillMaxWidth()) {
                FEATURES.forEachIndexed { index, feature ->
                    FeatureCard(feature) { onNavigate(feature.screen) }
                    if (index % 2 == 1) Spacer(Modifier.height(12.dp)) else Spacer(Modifier.height(12.dp))
                }
            }
        }
        item {
            Spacer(Modifier.height(20.dp))
            Text(
                "最近打开",
                style = MaterialTheme.typography.titleSmall,
                color = GlassSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        if (recentFiles.isEmpty()) {
            item {
                GlassCard {
                    Text("暂无最近文件", color = GlassOnBackground, fontSize = 13.sp)
                }
            }
        } else {
            items(recentFiles, key = { it.first }) { (_, name) ->
                GlassCard(Modifier.padding(bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = GlassPrimary)
                        Spacer(Modifier.width(10.dp))
                        Text(name, color = GlassOnBackground, fontSize = 14.sp, maxLines = 1)
                    }
                }
            }
        }
    }
        LiquidGlassShortcutBar(
            onNavigate = onNavigate,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        )
    }
}

@Composable
private fun LiquidGlassShortcutBar(
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val shortcuts = listOf(
        Triple(Icons.Filled.PhotoLibrary, "相册", Screen.Album),
        Triple(Icons.Filled.SwapHoriz, "转换", Screen.Convert),
        Triple(Icons.Filled.AutoFixHigh, "编辑", Screen.Edit),
        Triple(Icons.Filled.Inventory2, "批量", Screen.Batch)
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(androidx.compose.ui.graphics.Color(0x59FFFFFF))
            .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(28.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        shortcuts.forEach { (icon, label, screen) ->
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onNavigate(screen) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(icon, contentDescription = label, tint = Color(0xFFE6E6FF), modifier = Modifier.size(22.dp))
                Text(label, color = GlassOnBackground, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun FeatureCard(feature: Feature, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(androidx.compose.ui.graphics.Color(0x40FFFFFF))
            .border(1.dp, Color(0x39FFFFFF), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x33FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            Icon(feature.icon, contentDescription = null, tint = GlassPrimary, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(feature.title, color = Color(0xFFE6E6FF), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(feature.subtitle, color = GlassOnBackground, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
