package com.cortextransl.translateonscreen.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import androidx.core.graphics.ColorUtils

object BubbleStyle {
    const val MIN_DP = 46
    const val MAX_DP = 70
    const val DEFAULT_DP = 56
    const val DEFAULT_COLOR = 0xFF0F766E.toInt()
    const val MIN_OPACITY = 45
    const val MAX_OPACITY = 100
    const val DEFAULT_OPACITY = 82

    val presets = intArrayOf(
        0xFF0F766E.toInt(),
        0xFF0E7490.toInt(),
        0xFF0284C7.toInt(),
        0xFF1D4ED8.toInt(),
        0xFF5B21B6.toInt(),
        0xFFBE185D.toInt(),
        0xFFC2410C.toInt(),
        0xFF334155.toInt(),
        0xFFF4F4F5.toInt()
    )

    fun glyphColor(fill: Int): Int {
        return if (ColorUtils.calculateLuminance(fill) > 0.62) {
            0xFF111827.toInt()
        } else {
            Color.WHITE
        }
    }

    fun background(context: Context, color: Int): Drawable {
        val density = context.resources.displayMetrics.density
        val light = ColorUtils.blendARGB(color, Color.WHITE, 0.20f)
        val dark = ColorUtils.blendARGB(color, Color.BLACK, 0.28f)
        val fill = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(light, color, dark)
        ).apply {
            shape = GradientDrawable.OVAL
        }
        val lightFill = ColorUtils.calculateLuminance(color) > 0.62
        val veryDark = ColorUtils.calculateLuminance(color) < 0.14
        val ring = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke(
                (1.8f * density).toInt().coerceAtLeast(2),
                when {
                    lightFill -> 0x3D000000
                    veryDark -> 0xCCFFFFFF.toInt()
                    else -> 0x73FFFFFF
                }
            )
        }
        val gloss = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0x4DFFFFFF, 0x00FFFFFF)
        ).apply {
            shape = GradientDrawable.OVAL
        }
        val ringInset = (2.2f * density).toInt()
        val glossSide = (3.5f * density).toInt()
        return LayerDrawable(arrayOf(fill, ring, gloss)).apply {
            setLayerInset(1, ringInset, ringInset, ringInset, ringInset)
            setLayerInset(2, glossSide, glossSide, glossSide, (density * 22f).toInt())
        }
    }
}
