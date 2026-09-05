package com.cortextransl.translateonscreen.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cortextransl.translateonscreen.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences
) : ViewModel() {

    val darkMode: StateFlow<Boolean?> = userPreferences.darkMode.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null
    )

    val singleAppCapture: StateFlow<Boolean> = userPreferences.singleAppCapture.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false
    )

    val quickAccessEnabled: StateFlow<Boolean> = userPreferences.quickAccessEnabled.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false
    )

    val deeplApiKey: StateFlow<String> = userPreferences.deeplApiKey.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UserPreferences.DEFAULT_DEEPL_API_KEY
    )

    val libreBaseUrl: StateFlow<String> = userPreferences.libreBaseUrl.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UserPreferences.DEFAULT_LIBRE_URL
    )

    val libreApiKey: StateFlow<String> = userPreferences.libreApiKey.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ""
    )

    val bubbleSizeDp: StateFlow<Int> = userPreferences.bubbleSizeDp.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UserPreferences.DEFAULT_BUBBLE_SIZE_DP
    )

    val bubbleColor: StateFlow<Int> = userPreferences.bubbleColor.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UserPreferences.DEFAULT_BUBBLE_COLOR
    )

    val bubbleOpacity: StateFlow<Int> = userPreferences.bubbleOpacity.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UserPreferences.DEFAULT_BUBBLE_OPACITY
    )

    fun setThemeFollowSystem() {
        viewModelScope.launch { userPreferences.setDarkModeFollowSystem() }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setDarkMode(enabled) }
    }

    fun setSingleAppCapture(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setSingleAppCapture(enabled) }
    }

    fun setQuickAccessEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setQuickAccessEnabled(enabled) }
    }

    fun setDeeplApiKey(key: String) {
        viewModelScope.launch { userPreferences.setDeeplApiKey(key) }
    }

    fun restoreDefaultDeeplApiKey() {
        viewModelScope.launch { userPreferences.setDeeplApiKey("") }
    }

    fun setLibreBaseUrl(url: String) {
        viewModelScope.launch { userPreferences.setLibreBaseUrl(url) }
    }

    fun setLibreApiKey(key: String) {
        viewModelScope.launch { userPreferences.setLibreApiKey(key) }
    }

    fun setBubbleSizeDp(sizeDp: Int) {
        viewModelScope.launch { userPreferences.setBubbleSizeDp(sizeDp) }
    }

    fun setBubbleColor(color: Int) {
        viewModelScope.launch { userPreferences.setBubbleColor(color) }
    }

    fun setBubbleOpacity(opacity: Int) {
        viewModelScope.launch { userPreferences.setBubbleOpacity(opacity) }
    }
}
