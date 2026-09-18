package com.omni.image.vector

import android.graphics.Path

data class SvgShape(
    val path: Path,
    val fill: Int?,
    val stroke: Int?,
    val strokeWidth: Float,
    val fillOpacity: Float,
    val fillColorHex: String
)
