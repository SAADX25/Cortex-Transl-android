package com.cortextransl.translateonscreen.data.model

import android.graphics.Rect

data class OverlayBlock(
    val originalText: String,
    val translatedText: String,
    val rect: Rect,
    val lineCount: Int
)
