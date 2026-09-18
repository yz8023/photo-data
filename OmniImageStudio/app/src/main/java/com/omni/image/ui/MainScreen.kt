package com.omni.image.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omni.image.ui.theme.GlassOnBackground
import com.omni.image.ui.theme.GlassPrimary
import com.omni.image.ui.theme.GlassSecondary
import com.omni.image.ui.theme.GlassSurfaceStrong
import com.omni.image.ui.theme.GlassSurface
import com.omni.image.util.RecentFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    data class RecentViewer(val uri: Uri, val name: String) : Screen()
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
    val context = LocalContext.current
    val recent by remember {
        mutableStateOf(RecentFiles.list(context))
    }

    // 简单返回栈：Album → AlbumViewer → Edit/Convert/Exif 均入栈，返回回到上一步。
    val stack = remember { mutableStateOf(listOf<Screen>(Screen.Main)) }
    val current = stack.value.last()

    fun navigate(target: Screen) {
        stack.value = stack.value + target
    }
    fun goBack() {
        if (stack.value.size > 1) stack.value = stack.value.dropLast(1)
    }

    // 首次进入请求媒体读取权限，保证 MediaStore 能查到 RELATIVE_PATH（决定输出目录）。
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        val permission = if (Build.VERSION.SDK_INT >= 33)
            Manifest.permission.READ_MEDIA_IMAGES
        else
            Manifest.permission.READ_EXTERNAL_STORAGE
        val granted = checkSelfPermission(context, permission)
        if (!granted) permissionLauncher.launch(permission)
    }

    when (val screen = current) {
        Screen.Main -> Home(
            recentFiles = recent,
            onNavigate = { navigate(it) }
        )
        Screen.Convert -> AppScaffold("格式转换", onBack = { goBack() }) {
            AppScreenBox { ConvertScreen() }
        }
        Screen.Edit -> AppScaffold("裁剪与色彩调节", onBack = { goBack() }) {
            AppScreenBox { EditScreen() }
        }
        Screen.Exif -> AppScaffold("EXIF 编辑", onBack = { goBack() }) {
            AppScreenBox { ExifScreen() }
        }
        Screen.Vector -> AppScaffold("矢量化与 SVG", onBack = { goBack() }) {
            AppScreenBox { VectorScreen() }
        }
        Screen.Watermark -> AppScaffold("水印系统", onBack = { goBack() }) {
            AppScreenBox { WatermarkScreen() }
        }
        Screen.Canvas -> AppScaffold("画布绘制", onBack = { goBack() }) {
            AppScreenBox { CanvasScreen() }
        }
        Screen.Batch -> AppScaffold("批量处理", onBack = { goBack() }) {
            AppScreenBox { BatchScreen() }
        }
        Screen.Album -> AppScaffold("相册系统", onBack = { goBack() }) {
            AppScreenBox {
                AlbumScreen(
                    onPick = { uri ->
                        val photos = AlbumReader.loadPhotos(context)
                        val idx = photos.indexOfFirst { it.uri == uri }
                        if (idx >= 0) {
                            navigate(Screen.AlbumViewer(photos, idx))
                        }
                    }
                )
            }
        }
        is Screen.AlbumViewer -> AppScaffold("图片查看", onBack = { goBack() }) {
            AppScreenBox {
                AlbumViewer(
                    photos = screen.photos,
                    startIndex = screen.startIndex,
                    onBack = { goBack() },
                    onEdit = { uri ->
                        ScreenChannels.editUri = uri
                        navigate(Screen.Edit)
                    },
                    onConvert = { uri ->
                        ScreenChannels.convertUri = uri
                        navigate(Screen.Convert)
                    },
                    onExif = { uri ->
                        ScreenChannels.exifUri = uri
                        navigate(Screen.Exif)
                    }
                )
            }
        }
        is Screen.RecentViewer -> {
            val photo = remember(screen.uri) {
                AlbumPhoto(
                    id = screen.uri.toString().hashCode().toLong(),
                    uri = screen.uri,
                    name = screen.name,
                    dateTaken = 0L,
                    size = 0L
                )
            }
            AppScaffold("最近打开", onBack = { goBack() }) {
                AppScreenBox {
                    AlbumViewer(
                        photos = listOf(photo),
                        startIndex = 0,
                        onBack = { goBack() },
                        onEdit = { uri ->
                            ScreenChannels.editUri = uri
                            navigate(Screen.Edit)
                        },
                        onConvert = { uri ->
                            ScreenChannels.convertUri = uri
                            navigate(Screen.Convert)
                        },
                        onExif = { uri ->
                            ScreenChannels.exifUri = uri
                            navigate(Screen.Exif)
                        }
                    )
                }
            }
        }
    }
}

private fun checkSelfPermission(context: android.content.Context, permission: String): Boolean =
    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

@Composable
private fun Home(
    recentFiles: List<Pair<String, String>>,
    onNavigate: (Screen) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(0xFF14151F))
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
                color = Color(0xFFFFFFFF)
            )
            Text(
                "纯离线全能图像工具 · 格式转换 / EXIF / 画布 / 矢量化 / 水印",
                color = GlassOnBackground,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
        }
        item {
            Column(Modifier.fillMaxWidth()) {
                FEATURES.forEach { feature ->
                    FeatureCard(feature) { onNavigate(feature.screen) }
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
                    Text("暂无最近文件，完成一次转换/编辑后文件会出现在这里", color = GlassOnBackground, fontSize = 13.sp)
                }
            }
        } else {
            // 与相册一致：缩略图网格（二级）→ 点击进查看器（一级）
            recentFiles.chunked(3).forEach { rowItems ->
                item(key = "recent_${rowItems.first().first}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { (uri, name) ->
                            RecentThumb(
                                uri = Uri.parse(uri),
                                name = name,
                                modifier = Modifier.weight(1f)
                            ) {
                                onNavigate(Screen.RecentViewer(Uri.parse(uri), name))
                            }
                        }
                        repeat(3 - rowItems.size) {
                            Spacer(Modifier.weight(1f))
                        }
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
private fun RecentThumb(
    uri: Uri,
    name: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val bmp by produceState<Bitmap?>(initialValue = null, key1 = uri) {
        value = withContext(Dispatchers.IO) {
            runCatching { decodeThumb(context, uri, 300) }.getOrNull()
        }
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(GlassSurface)
            .border(1.dp, Color(0x59FFFFFF), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(androidx.compose.ui.graphics.Color(0x45FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp!!.asImageBitmap(),
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = GlassPrimary, modifier = Modifier.size(22.dp))
            }
        }
        Text(
            name,
            color = Color(0xFFFFFFFF),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
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
            .background(androidx.compose.ui.graphics.Color(0xA6FFFFFF))
            .border(1.dp, Color(0x59FFFFFF), RoundedCornerShape(28.dp))
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
                Icon(icon, contentDescription = label, tint = Color(0xFFFFFFFF), modifier = Modifier.size(22.dp))
                Text(label, color = GlassOnBackground, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
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
            .background(androidx.compose.ui.graphics.Color(0xA6FFFFFF))
            .border(1.dp, Color(0x59FFFFFF), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(androidx.compose.ui.graphics.Color(0x59FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            Icon(feature.icon, contentDescription = null, tint = GlassPrimary, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(feature.title, color = Color(0xFFFFFFFF), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(feature.subtitle, color = GlassOnBackground, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}