package com.cortextransl.translateonscreen.overlay

import android.graphics.Typeface

/**
 * User-configurable appearance of the translated text cards.
 *
 * @param textScale   percentage applied to the automatic text size (70..170).
 * @param fontKey     one of [FONT_KEYS].
 * @param bold        render text in bold.
 * @param textColor   ARGB text colour.
 * @param backgroundColor ARGB (alpha ignored) card colour.
 * @param backgroundOpacity 30..100 percent.
 * @param outline     draw a thin stroke behind text (readability on busy images).
 */
data class OverlayStyle(
    val textScale: Int = DEFAULT_TEXT_SCALE,
    val fontKey: String = DEFAULT_FONT,
    val bold: Boolean = false,
    val textColor: Int = DEFAULT_TEXT_COLOR,
    val backgroundColor: Int = DEFAULT_BACKGROUND,
    val backgroundOpacity: Int = DEFAULT_BG_OPACITY,
    val outline: Boolean = false
) {
    val scale: Float get() = textScale.coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE) / 100f

    fun typeface(rtl: Boolean): Typeface {
        val family = when (fontKey) {
            FONT_SYSTEM -> if (rtl) "sans-serif-medium" else "sans-serif"
            FONT_LIGHT -> "sans-serif-light"
            FONT_CONDENSED -> "sans-serif-condensed"
            FONT_SERIF -> "serif"
            FONT_MONO -> "monospace"
            FONT_ROUNDED -> "casual"
            else -> "sans-serif"
        }
        return Typeface.create(family, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    fun backgroundArgb(): Int {
        val alpha = (backgroundOpacity.coerceIn(MIN_BG_OPACITY, MAX_BG_OPACITY) * 255 / 100)
        return (alpha shl 24) or (backgroundColor and 0x00FFFFFF)
    }

    companion object {
        const val FONT_SYSTEM = "system"
        const val FONT_LIGHT = "light"
        const val FONT_CONDENSED = "condensed"
        const val FONT_SERIF = "serif"
        const val FONT_MONO = "mono"
        const val FONT_ROUNDED = "rounded"
        val FONT_KEYS = listOf(FONT_SYSTEM, FONT_LIGHT, FONT_CONDENSED, FONT_SERIF, FONT_MONO, FONT_ROUNDED)

        const val MIN_TEXT_SCALE = 70
        const val MAX_TEXT_SCALE = 170
        const val DEFAULT_TEXT_SCALE = 100
        const val DEFAULT_FONT = FONT_SYSTEM
        const val MIN_BG_OPACITY = 30
        const val MAX_BG_OPACITY = 100
        const val DEFAULT_BG_OPACITY = 90
        const val DEFAULT_TEXT_COLOR = 0xFFFFFFFF.toInt()
        const val DEFAULT_BACKGROUND = 0xFF3A3B40.toInt()

        val textColorPresets = listOf(
            0xFFFFFFFF.toInt(), 0xFFFFE082.toInt(), 0xFF80D8FF.toInt(),
            0xFFB9F6CA.toInt(), 0xFF111111.toInt()
        )
        val backgroundPresets = listOf(
            0xFF3A3B40.toInt(), 0xFF000000.toInt(), 0xFF1A73E8.toInt(),
            0xFF2E7D32.toInt(), 0xFF7B1FA2.toInt(), 0xFFFFFFFF.toInt()
        )
    }
}
