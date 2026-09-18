package com.omni.image.model

data class WatermarkPreset(
    val name: String,
    val text: String = "",
    val color: Long = 0xFFFFFFFF,
    val sizePx: Float = 48f,
    val rotation: Float = 0f,
    val opacity: Float = 0.5f,
    val shadow: Boolean = true,
    val position: String = "center",
    val tileMode: Boolean = false,
    val tileGap: Int = 200,
    val imageUri: String = ""
)
