package com.omni.image.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omni.image.ui.theme.GlassOnBackground
import com.omni.image.ui.theme.GlassPrimary
import com.omni.image.util.RecentFiles

data class AlbumPhoto(
    val id: Long,
    val uri: Uri,
    val name: String,
    val dateTaken: Long,
    val size: Long
)

object AlbumReader {

    /** 读取 MediaStore 全部图片，按时间倒序。 */
    fun loadPhotos(context: Context): List<AlbumPhoto> {
        val out = mutableListOf<AlbumPhoto>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE
        )
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, sort
            )?.use { c ->
                val idCol = c.getColumnIndex(MediaStore.Images.Media._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateCol = c.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                val sizeCol = c.getColumnIndex(MediaStore.Images.Media.SIZE)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI.buildUpon()
                        .appendPath(id.toString()).build()
                    val name = c.getString(nameCol)?.toString() ?: "IMG_$id"
                    val date = if (dateCol >= 0) c.getLong(dateCol) * 1000 else 0L
                    val size = if (sizeCol >= 0) c.getLong(sizeCol) else 0L
                    out.add(AlbumPhoto(id, uri, name, date, size))
                }
            }
        }
        return out
    }
}

@Composable
fun AlbumScreen(onPick: (Uri) -> Unit) {
    val context = LocalContext.current
    val photos by produceState<List<AlbumPhoto>>(initialValue = emptyList()) {
        value = AlbumReader.loadPhotos(context)
    }

    if (photos.isEmpty()) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = GlassOnBackground, modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(12.dp))
            Text("相册为空，或未授予照片权限", color = GlassOnBackground, fontSize = 14.sp)
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(photos, key = { it.id }) { photo ->
            AlbumThumb(photo) { onPick(photo.uri) }
        }
    }
}

@Composable
private fun AlbumThumb(photo: AlbumPhoto, onClick: () -> Unit) {
    val context = LocalContext.current
    val bmp by produceState<Bitmap?>(initialValue = null, key1 = photo.id) {
        value = decodeThumb(context, photo.uri, 480)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x22FFFFFF))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp!!.asImageBitmap(),
                contentDescription = photo.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(photo.name.substringAfterLast('.', "").uppercase(), color = GlassOnBackground, fontSize = 11.sp)
            }
        }
    }
}

fun decodeThumb(context: Context, uri: Uri, maxDim: Int): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= 29) {
            context.contentResolver.loadThumbnail(uri, android.util.Size(maxDim, maxDim), null)
        } else {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it, null, bounds)
            }
            val sample = bounds.outWidth.coerceAtLeast(bounds.outHeight)
            val opts = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = if (sample > maxDim) sample / maxDim + 1 else 1
            }
            context.contentResolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it, null, opts)
            }
        }
    } catch (e: Exception) {
        null
    }
}

@Composable
fun AlbumViewer(
    photos: List<AlbumPhoto>,
    startIndex: Int,
    onBack: () -> Unit,
    onEdit: (Uri) -> Unit,
    onConvert: (Uri) -> Unit,
    onExif: (Uri) -> Unit
) {
    var index by remember { mutableStateOf(startIndex.coerceIn(0, photos.size - 1)) }
    val context = LocalContext.current
    val current = photos.getOrNull(index)
    val bmp by produceState<Bitmap?>(initialValue = null, key1 = current?.id) {
        value = current?.let { decodeThumb(context, it.uri, 2048) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${index + 1} / ${photos.size}",
                color = GlassOnBackground,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                current?.name ?: "",
                color = GlassOnBackground,
                fontSize = 13.sp,
                maxLines = 1,
                modifier = Modifier.weight(2f)
            )
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = GlassPrimary,
                modifier = Modifier.weight(1f)
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (bmp != null) {
                Image(
                    bitmap = bmp!!.asImageBitmap(),
                    contentDescription = current?.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text("加载中…", color = GlassOnBackground)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ViewerAction("上一张") {
                if (index > 0) index--
            }
            ViewerAction("下一张") {
                if (index < photos.size - 1) index++
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ViewerAction("编辑") {
                current?.let { onEdit(it.uri) }
            }
            ViewerAction("转换") {
                current?.let { onConvert(it.uri) }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ViewerAction("EXIF") {
                current?.let { onExif(it.uri) }
            }
            ViewerAction("返回") {
                onBack()
            }
        }
    }
}

@Composable
private fun RowScope.ViewerAction(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(androidx.compose.ui.graphics.Color(0x33FFFFFF))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = GlassOnBackground, fontSize = 14.sp)
    }
}