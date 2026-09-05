package com.cortextransl.translateonscreen.data.model

import com.cortextransl.translateonscreen.R
import com.cortextransl.translateonscreen.data.preferences.UserPreferences

object TranslationModes {
    const val FULLSCREEN = UserPreferences.MODE_FULLSCREEN
    const val REGION = UserPreferences.MODE_REGION
    const val FIXED_REGION = UserPreferences.MODE_FIXED_REGION
    const val AUTO_REGION = UserPreferences.MODE_AUTO_REGION
    const val AUTO_FULLSCREEN = UserPreferences.MODE_AUTO_FULLSCREEN

    val all = listOf(FULLSCREEN, REGION, FIXED_REGION, AUTO_REGION, AUTO_FULLSCREEN)

    fun normalize(mode: String): String = when (mode) {
        UserPreferences.MODE_PARTIAL -> REGION
        else -> mode
    }

    fun labelRes(mode: String): Int = when (normalize(mode)) {
        REGION -> R.string.mode_region
        FIXED_REGION -> R.string.mode_fixed_region
        AUTO_REGION -> R.string.mode_auto_region
        AUTO_FULLSCREEN -> R.string.mode_auto_fullscreen
        else -> R.string.mode_fullscreen
    }

    fun isAuto(mode: String): Boolean {
        val normalized = normalize(mode)
        return normalized == AUTO_REGION || normalized == AUTO_FULLSCREEN
    }

    fun usesRegion(mode: String): Boolean {
        val normalized = normalize(mode)
        return normalized == REGION || normalized == FIXED_REGION || normalized == AUTO_REGION
    }
}

object BubbleActions {
    const val OPEN_MENU = "open_menu"
    const val TRANSLATE = "translate"
    const val NONE = "none"

    val all = listOf(OPEN_MENU, TRANSLATE, NONE)

    fun labelRes(action: String): Int = when (action) {
        TRANSLATE -> R.string.bubble_action_translate
        NONE -> R.string.bubble_action_none
        else -> R.string.bubble_action_open_menu
    }
}
