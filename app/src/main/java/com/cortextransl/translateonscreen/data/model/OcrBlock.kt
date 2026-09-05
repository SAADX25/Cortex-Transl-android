package com.cortextransl.translateonscreen.data.model

import android.graphics.Rect

data class OcrBlock(
    val text: String,
    val boundingBox: Rect,
    val lineCount: Int
)
