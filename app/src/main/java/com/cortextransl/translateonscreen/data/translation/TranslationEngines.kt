package com.cortextransl.translateonscreen.data.translation

import com.cortextransl.translateonscreen.R
import com.cortextransl.translateonscreen.data.preferences.UserPreferences

object TranslationEngines {
    val all = listOf(
        UserPreferences.ENGINE_MLKIT,
        UserPreferences.ENGINE_DEEPL,
        UserPreferences.ENGINE_LIBRE,
        UserPreferences.ENGINE_MYMEMORY
    )

    fun labelRes(engine: String): Int = when (engine) {
        UserPreferences.ENGINE_DEEPL -> R.string.engine_deepl
        UserPreferences.ENGINE_LIBRE -> R.string.engine_libre
        UserPreferences.ENGINE_MYMEMORY -> R.string.engine_mymemory
        else -> R.string.engine_mlkit
    }

    fun shortRes(engine: String): Int = when (engine) {
        UserPreferences.ENGINE_DEEPL -> R.string.engine_deepl_short
        UserPreferences.ENGINE_LIBRE -> R.string.engine_libre_short
        UserPreferences.ENGINE_MYMEMORY -> R.string.engine_mymemory_short
        else -> R.string.engine_mlkit_short
    }

    fun overlaySubtitleRes(engine: String): Int = when (engine) {
        UserPreferences.ENGINE_DEEPL -> R.string.overlay_subtitle_cloud
        UserPreferences.ENGINE_LIBRE -> R.string.overlay_subtitle_libre
        UserPreferences.ENGINE_MYMEMORY -> R.string.overlay_subtitle_mymemory
        else -> R.string.overlay_subtitle
    }

    fun usesOnDevicePacks(engine: String): Boolean =
        engine == UserPreferences.ENGINE_MLKIT
}
