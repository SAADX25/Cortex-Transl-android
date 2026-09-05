package com.cortextransl.translateonscreen.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cortextransl.translateonscreen.data.model.BubbleActions
import com.cortextransl.translateonscreen.overlay.BubbleStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import android.graphics.Rect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "translate_on_screen_preferences"
)

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val SOURCE_LANGUAGE = stringPreferencesKey("source_language")
        val TARGET_LANGUAGE = stringPreferencesKey("target_language")
        val TRANSLATION_ENGINE = stringPreferencesKey("translation_engine")
        val TRANSLATION_MODE = stringPreferencesKey("translation_mode")
        val DEEPL_API_KEY = stringPreferencesKey("deepl_api_key")
        val LIBRE_BASE_URL = stringPreferencesKey("libre_base_url")
        val LIBRE_API_KEY = stringPreferencesKey("libre_api_key")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val QUICK_ACCESS_ENABLED = booleanPreferencesKey("quick_access_enabled")
        val SINGLE_APP_CAPTURE = booleanPreferencesKey("single_app_capture")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val LONG_PRESS_ACTION = stringPreferencesKey("long_press_action")
        val DOUBLE_TAP_ACTION = stringPreferencesKey("double_tap_action")
        val LAST_REGION_LEFT = intPreferencesKey("last_region_left")
        val LAST_REGION_TOP = intPreferencesKey("last_region_top")
        val LAST_REGION_RIGHT = intPreferencesKey("last_region_right")
        val LAST_REGION_BOTTOM = intPreferencesKey("last_region_bottom")
        val BUBBLE_SIZE_DP = intPreferencesKey("bubble_size_dp")
        val BUBBLE_COLOR = intPreferencesKey("bubble_color")
        val BUBBLE_OPACITY = intPreferencesKey("bubble_opacity")
        val QUICK_ACCESS_OFFERED = booleanPreferencesKey("quick_access_offered")

        const val AUTO_LANGUAGE = "auto"
        const val DEFAULT_SOURCE_LANGUAGE = "en"
        const val DEFAULT_TARGET_LANGUAGE = "ar"
        const val ENGINE_MLKIT = "mlkit"
        const val ENGINE_DEEPL = "deepl"
        const val ENGINE_LIBRE = "libre"
        const val ENGINE_MYMEMORY = "mymemory"
        const val DEFAULT_ENGINE = ENGINE_DEEPL
        const val DEFAULT_DEEPL_API_KEY = ""
        const val DEFAULT_LIBRE_URL = "https://libretranslate.com"
        const val MODE_FULLSCREEN = "fullscreen"
        const val MODE_PARTIAL = "partial"
        const val MODE_REGION = "region"
        const val MODE_FIXED_REGION = "fixed_region"
        const val MODE_AUTO_REGION = "auto_region"
        const val MODE_AUTO_FULLSCREEN = "auto_fullscreen"
        const val DEFAULT_MODE = MODE_FULLSCREEN
        const val DEFAULT_LONG_PRESS_ACTION = BubbleActions.TRANSLATE
        const val DEFAULT_DOUBLE_TAP_ACTION = BubbleActions.NONE
        const val DEFAULT_BUBBLE_SIZE_DP = BubbleStyle.DEFAULT_DP
        const val DEFAULT_BUBBLE_COLOR = BubbleStyle.DEFAULT_COLOR
        const val DEFAULT_BUBBLE_OPACITY = BubbleStyle.DEFAULT_OPACITY
    }

    val sourceLanguage: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SOURCE_LANGUAGE] ?: DEFAULT_SOURCE_LANGUAGE
    }

    val targetLanguage: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[TARGET_LANGUAGE] ?: DEFAULT_TARGET_LANGUAGE
    }

    suspend fun setSourceLanguage(languageCode: String) {
        context.dataStore.edit { prefs -> prefs[SOURCE_LANGUAGE] = languageCode }
    }

    suspend fun setTargetLanguage(languageCode: String) {
        context.dataStore.edit { prefs -> prefs[TARGET_LANGUAGE] = languageCode }
    }

    val translationEngine: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[TRANSLATION_ENGINE] ?: DEFAULT_ENGINE
    }

    suspend fun setTranslationEngine(engine: String) {
        context.dataStore.edit { prefs -> prefs[TRANSLATION_ENGINE] = engine }
    }

    val deeplApiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[DEEPL_API_KEY]?.trim().orEmpty().ifBlank { DEFAULT_DEEPL_API_KEY }
    }

    suspend fun setDeeplApiKey(key: String) {
        val trimmed = key.trim()
        context.dataStore.edit { prefs ->
            if (trimmed.isBlank() || trimmed == DEFAULT_DEEPL_API_KEY) {
                prefs.remove(DEEPL_API_KEY)
            } else {
                prefs[DEEPL_API_KEY] = trimmed
            }
        }
    }

    val libreBaseUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[LIBRE_BASE_URL]?.trim().orEmpty().ifBlank { DEFAULT_LIBRE_URL }
    }

    suspend fun setLibreBaseUrl(url: String) {
        val trimmed = url.trim().trimEnd('/')
        context.dataStore.edit { prefs ->
            if (trimmed.isBlank() || trimmed == DEFAULT_LIBRE_URL) {
                prefs.remove(LIBRE_BASE_URL)
            } else {
                prefs[LIBRE_BASE_URL] = trimmed
            }
        }
    }

    val libreApiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[LIBRE_API_KEY].orEmpty()
    }

    suspend fun setLibreApiKey(key: String) {
        context.dataStore.edit { prefs ->
            val trimmed = key.trim()
            if (trimmed.isBlank()) prefs.remove(LIBRE_API_KEY) else prefs[LIBRE_API_KEY] = trimmed
        }
    }

    val translationMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[TRANSLATION_MODE] ?: DEFAULT_MODE
    }

    suspend fun setTranslationMode(mode: String) {
        context.dataStore.edit { prefs -> prefs[TRANSLATION_MODE] = mode }
    }

    val darkMode: Flow<Boolean?> = context.dataStore.data.map { prefs ->
        if (prefs.contains(DARK_MODE)) prefs[DARK_MODE] else null
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[DARK_MODE] = enabled }
    }

    suspend fun setDarkModeFollowSystem() {
        context.dataStore.edit { prefs -> prefs.remove(DARK_MODE) }
    }

    val quickAccessEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[QUICK_ACCESS_ENABLED] ?: false
    }

    suspend fun setQuickAccessEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[QUICK_ACCESS_ENABLED] = enabled }
    }

    val singleAppCapture: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SINGLE_APP_CAPTURE] ?: false
    }

    suspend fun setSingleAppCapture(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[SINGLE_APP_CAPTURE] = enabled }
    }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[ONBOARDING_COMPLETED] ?: false
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { prefs -> prefs[ONBOARDING_COMPLETED] = completed }
    }

    val longPressAction: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[LONG_PRESS_ACTION] ?: DEFAULT_LONG_PRESS_ACTION
    }

    suspend fun setLongPressAction(action: String) {
        context.dataStore.edit { prefs -> prefs[LONG_PRESS_ACTION] = action }
    }

    val doubleTapAction: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[DOUBLE_TAP_ACTION] ?: DEFAULT_DOUBLE_TAP_ACTION
    }

    suspend fun setDoubleTapAction(action: String) {
        context.dataStore.edit { prefs -> prefs[DOUBLE_TAP_ACTION] = action }
    }

    val bubbleSizeDp: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[BUBBLE_SIZE_DP] ?: DEFAULT_BUBBLE_SIZE_DP)
            .coerceIn(BubbleStyle.MIN_DP, BubbleStyle.MAX_DP)
    }

    suspend fun setBubbleSizeDp(sizeDp: Int) {
        context.dataStore.edit { prefs ->
            prefs[BUBBLE_SIZE_DP] = sizeDp.coerceIn(BubbleStyle.MIN_DP, BubbleStyle.MAX_DP)
        }
    }

    val bubbleColor: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[BUBBLE_COLOR] ?: DEFAULT_BUBBLE_COLOR
    }

    suspend fun setBubbleColor(color: Int) {
        context.dataStore.edit { prefs -> prefs[BUBBLE_COLOR] = color }
    }

    val bubbleOpacity: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[BUBBLE_OPACITY] ?: DEFAULT_BUBBLE_OPACITY)
            .coerceIn(BubbleStyle.MIN_OPACITY, BubbleStyle.MAX_OPACITY)
    }

    suspend fun setBubbleOpacity(opacity: Int) {
        context.dataStore.edit { prefs ->
            prefs[BUBBLE_OPACITY] = opacity.coerceIn(BubbleStyle.MIN_OPACITY, BubbleStyle.MAX_OPACITY)
        }
    }

    val quickAccessOffered: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[QUICK_ACCESS_OFFERED] ?: false
    }

    suspend fun setQuickAccessOffered(offered: Boolean) {
        context.dataStore.edit { prefs -> prefs[QUICK_ACCESS_OFFERED] = offered }
    }

    suspend fun getLastRegion(): Rect? {
        val prefs = context.dataStore.data.first()
        val left = prefs[LAST_REGION_LEFT] ?: return null
        val top = prefs[LAST_REGION_TOP] ?: return null
        val right = prefs[LAST_REGION_RIGHT] ?: return null
        val bottom = prefs[LAST_REGION_BOTTOM] ?: return null
        if (right <= left || bottom <= top) return null
        return Rect(left, top, right, bottom)
    }

    suspend fun setLastRegion(rect: Rect) {
        context.dataStore.edit { prefs ->
            prefs[LAST_REGION_LEFT] = rect.left
            prefs[LAST_REGION_TOP] = rect.top
            prefs[LAST_REGION_RIGHT] = rect.right
            prefs[LAST_REGION_BOTTOM] = rect.bottom
        }
    }
}
