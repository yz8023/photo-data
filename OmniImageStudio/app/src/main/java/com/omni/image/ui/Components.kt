package com.omni.image.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.unit.Dp
import com.omni.image.ui.theme.GlassBackground
import com.omni.image.ui.theme.GlassOnBackground
import com.omni.image.ui.theme.GlassOnSurface
import com.omni.image.ui.theme.GlassOutline
import com.omni.image.ui.theme.GlassPrimary
import com.omni.image.ui.theme.GlassSecondary
import com.omni.image.ui.theme.GlassSuccess
import com.omni.image.ui.theme.GlassSurface
import com.omni.image.ui.theme.GlassSurfaceStrong
import com.omni.image.ui.theme.liquidGlass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

object Glass {
    fun gradient(): Brush = Brush.verticalGradient(
        listOf(Color(0xFF1A1B2E), Color(0xFF24283B), Color(0xFF1A1B26))
    )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.liquidGlass(shape = RoundedCornerShape(cornerRadius)),
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        border = BorderStroke(1.dp, GlassOutline),
        content = { Column(Modifier.padding(16.dp), content = content) }
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = GlassSecondary,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
    )
}

@Composable
fun GlassButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = GlassPrimary,
            contentColor = Color(0xFF1A1B26),
            disabledContainerColor = GlassSurface,
            disabledContentColor = GlassOnBackground
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = GlassOnSurface)
        } else {
            Text(text, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun GlassChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (selected) GlassPrimary else GlassSurface
    val fg = if (selected) Color(0xFF1A1B26) else GlassOnSurface
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, if (selected) Color.Transparent else GlassOutline, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(label, color = fg, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun GlassToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val bg = if (checked) GlassSuccess.copy(alpha = 0.5f) else GlassSurface
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, GlassOutline, RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (checked) GlassSuccess else GlassSurfaceStrong)
        ) {
            if (checked) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .align(Alignment.Center)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(label, color = GlassOnSurface, fontSize = 13.sp)
    }
}

@Composable
fun DampedSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    label: String = "",
    display: String = ""
) {
    val animated by animateFloatAsState(
        targetValue = value,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 260f),
        label = "damped"
    )
    Column(modifier) {
        if (label.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(label, fontSize = 13.sp, color = GlassOnBackground)
                Text(display.ifEmpty { "${(animated * 100).roundToInt()}" }, fontSize = 13.sp, color = GlassPrimary)
            }
        }
        Slider(
            value = animated,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = GlassPrimary,
                activeTrackColor = GlassPrimary,
                inactiveTrackColor = GlassSurfaceStrong
            )
        )
    }
}

@Composable
fun LoadingBox(visible: Boolean, modifier: Modifier = Modifier) {
    if (visible) {
        Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = GlassPrimary)
        }
    }
}

@Composable
fun ImagePreview(bitmap: Bitmap?, modifier: Modifier = Modifier) {
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "preview",
            modifier = modifier
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, GlassOutline, RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun ColorPickerPanel(
    color: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    var h by remember { mutableStateOf(color.toHsvFloatArray()[0]) }
    var s by remember { mutableStateOf(color.toHsvFloatArray()[1]) }
    var v by remember { mutableStateOf(color.toHsvFloatArray()[2]) }
    var a by remember { mutableStateOf(color.alpha) }
    var hex by remember { mutableStateOf(color.toArgbHex()) }

    fun applyHsv() {
        val c = androidx.compose.ui.graphics.Color.hsv(h, s, v, a)
        hex = c.toArgbHex()
        onColorChange(c)
    }

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            var textFieldHex by remember { mutableStateOf(hex) }
            OutlinedTextField(
                value = textFieldHex,
                onValueChange = {
                    textFieldHex = it
                    val parsed = parseHex(it)
                    if (parsed != null) {
                        val c = androidx.compose.ui.graphics.Color(parsed)
                        h = c.toHsvFloatArray()[0]
                        s = c.toHsvFloatArray()[1]
                        v = c.toHsvFloatArray()[2]
                        a = c.alpha
                        hex = c.toArgbHex()
                        onColorChange(c)
                    }
                },
                label = { Text("HEX") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color)
                    .border(1.dp, GlassOutline, RoundedCornerShape(10.dp))
            )
        }
        DampedSlider(h, { h = it; applyHsv() }, 0f..360f, label = "H 色相", display = "${h.toInt()}°")
        DampedSlider(s, { s = it; applyHsv() }, 0f..1f, label = "S 饱和度", display = "${(s * 100).toInt()}%")
        DampedSlider(v, { v = it; applyHsv() }, 0f..1f, label = "B 明度", display = "${(v * 100).toInt()}%")
        DampedSlider(a, { a = it; applyHsv() }, 0f..1f, label = "A 透明度", display = "${(a * 100).toInt()}%")
        Row(Modifier.fillMaxWidth()) {
            listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFF7768E.toInt(), 0xFF9ECE6A.toInt(), 0xFF7AA2F7.toInt(), 0xFFBB9AF7.toInt(), 0xFFE0AF68.toInt(), 0xFF2AC3DE.toInt())
                .forEach { preset ->
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(preset))
                            .clickable {
                                val c = Color(preset)
                                h = c.toHsvFloatArray()[0]
                                s = c.toHsvFloatArray()[1]
                                v = c.toHsvFloatArray()[2]
                                a = 1f
                                hex = c.toArgbHex()
                                onColorChange(c)
                            }
                    )
                }
        }
    }
}

private fun Color.toHsvFloatArray(): FloatArray {
    val out = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), out)
    return out
}

private fun Color.toArgbHex(): String {
    return String.format("#%06X", 0xFFFFFF and toArgb())
}

private fun parseHex(s: String): Int? {
    val clean = s.removePrefix("#")
    if (clean.length != 6) return null
    val v = clean.toLongOrNull(16) ?: return null
    return 0xFF000000.toInt() or v.toInt()
}
