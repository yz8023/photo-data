package com.omni.image.model

data class ExifPreset(
    val name: String,
    val fields: Map<String, String> = emptyMap()
)
