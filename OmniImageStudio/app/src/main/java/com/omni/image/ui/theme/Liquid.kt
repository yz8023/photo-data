package com.omni.image.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Aurora 装饰层：液态玻璃折射的对象。
 * 独立轨道周期的径向渐变光斑，全部 Canvas 绘制，无第三方依赖。
 */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val dark = isSystemInDarkTheme()
    val transition = rememberInfiniteTransition(label = "aurora")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(28_000, easing = LinearEasing)),
        label = "auroraPhase"
    )
    val palette = if (dark) AuroraDark else AuroraLight
    val base = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier.background(Brush.verticalGradient(listOf(base, base, palette.first())))
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawAurora(phase, palette)
        }
        content()
    }
}

private data class Blob(
    val cx: Float,
    val cy: Float,
    val r: Float,
    val orbit: Float,
    val speed: Float,
    val color: Color
)

private fun DrawScope.drawAurora(phase: Float, palette: List<Color>) {
    val blobs = listOf(
        Blob(cx = 0.20f, cy = 0.16f, r = 0.62f, orbit = 0.10f, speed = 1.0f, color = palette[1]),
        Blob(cx = 0.86f, cy = 0.28f, r = 0.52f, orbit = 0.12f, speed = -1.35f, color = palette[2]),
        Blob(cx = 0.34f, cy = 0.82f, r = 0.66f, orbit = 0.09f, speed = 0.78f, color = palette[3]),
        Blob(cx = 0.92f, cy = 0.88f, r = 0.48f, orbit = 0.08f, speed = -0.62f, color = palette[1])
    )
    blobs.forEach { blob ->
        val x = (blob.cx + cos(phase * blob.speed) * blob.orbit) * size.width
        val y = (blob.cy + sin(phase * blob.speed * 0.8f) * blob.orbit) * size.height
        val radius = blob.r * size.minDimension
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    blob.color.copy(alpha = 0.42f),
                    blob.color.copy(alpha = 0.16f),
                    Color.Transparent
                ),
                center = Offset(x, y),
                radius = radius
            ),
            radius = radius,
            center = Offset(x, y)
        )
    }
}

/**
 * 液态玻璃表面近似（纯 Compose）。
 * 自下而上：软投影[无] → 半透明 tint → 顶部 specular 高光描边。
 * 不采样真实 backdrop（无 backdrop-android 依赖），观感接近参考项目的玻璃卡片。
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape = MaterialTheme.shapes.large,
    tint: Color? = null,
    borderAlpha: Float = 0.42f
): Modifier {
    val baseTint = tint ?: MaterialTheme.colorScheme.surfaceContainer
    val resolved = baseTint.copy(alpha = 0.55f)
    val highlight = Color.White.copy(alpha = 0.18f * borderAlpha / 0.42f)
    return this
        .clip(shape)
        .background(resolved)
        .drawWithContent {
            drawContent()
            drawSpecular(shape, highlight)
        }
}

private fun DrawScope.drawSpecular(shape: Shape, highlight: Color) {
    // 顶部 1dp 高光描边，模拟 specular Highlight。
    val topStroke = 1.dp.toPx()
    val sweep = Brush.linearGradient(
        colors = listOf(Color.Transparent, highlight, Color.Transparent),
        start = Offset(0f, 0f),
        end = Offset(size.width, 0f)
    )
    drawRect(
        brush = sweep,
        topLeft = Offset(0f, 0f),
        size = androidx.compose.ui.geometry.Size(size.width, topStroke)
    )
}