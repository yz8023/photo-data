package com.omni.image.model

data class ImageTask(
    val id: Long = System.currentTimeMillis(),
    val sourceUri: String = "",
    val sourceName: String = "",
    val outputFormat: String = "PNG",
    val quality: Int = 90,
    val width: Int = 0,
    val height: Int = 0,
    val keepRatio: Boolean = true,
    val targetSizeKb: Int = 0,
    val mode: String = MODE_STANDARD,
    val outputPath: String? = null,
    val status: String = STATUS_PENDING
) {
    companion object {
        const val MODE_STANDARD = "standard"
        const val MODE_SIZE_LIMITED = "size_limited"
        const val MODE_COMBO = "combo"

        const val STATUS_PENDING = "pending"
        const val STATUS_RUNNING = "running"
        const val STATUS_DONE = "done"
        const val STATUS_ERROR = "error"
    }
}
