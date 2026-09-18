package com.omni.image.model

import android.graphics.Bitmap
import android.graphics.BlendMode

data class Layer(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val visible: Boolean = true,
    val opacity: Float = 1f,
    val blendMode: BlendMode = BlendMode.SRC_OVER,
    val bitmap: Bitmap? = null
) {
    companion object {
        val SUPPORTED_MODES: List<BlendMode> = listOf(
            BlendMode.SRC_OVER,
            BlendMode.MULTIPLY,
            BlendMode.SCREEN,
            BlendMode.OVERLAY,
            BlendMode.DARKEN,
            BlendMode.LIGHTEN,
            BlendMode.DIFFERENCE,
            BlendMode.PLUS
        )
    }
}
