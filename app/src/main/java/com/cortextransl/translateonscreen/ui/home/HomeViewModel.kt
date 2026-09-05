package com.cortextransl.translateonscreen.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cortextransl.translateonscreen.data.preferences.UserPreferences
import com.cortextransl.translateonscreen.data.translation.TranslationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val translationRepository: TranslationRepository
) : ViewModel() {

    val sourceLanguage: StateFlow<String> = userPreferences.sourceLanguage.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UserPreferences.DEFAULT_SOURCE_LANGUAGE
    )

    val targetLanguage: StateFlow<String> = userPreferences.targetLanguage.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UserPreferences.DEFAULT_TARGET_LANGUAGE
    )

    val translationMode: StateFlow<String> = userPreferences.translationMode.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UserPreferences.DEFAULT_MODE
    )

    val translationEngine: StateFlow<String> = userPreferences.translationEngine.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UserPreferences.DEFAULT_ENGINE
    )

    val deeplApiKey: StateFlow<String> = userPreferences.deeplApiKey.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UserPreferences.DEFAULT_DEEPL_API_KEY
    )

    val singleAppCapture: StateFlow<Boolean> = userPreferences.singleAppCapture.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false
    )

    val downloadedLanguages: StateFlow<Set<String>> = translationRepository.downloadedLanguages

    private val _downloading = MutableStateFlow(false)
    val downloading: StateFlow<Boolean> = _downloading.asStateFlow()

    private val _downloadError = MutableStateFlow(false)
    val downloadError: StateFlow<Boolean> = _downloadError.asStateFlow()

    init {
        viewModelScope.launch { translationRepository.refreshDownloaded() }
    }

    fun setSourceLanguage(code: String) {
        viewModelScope.launch { userPreferences.setSourceLanguage(code) }
    }

    fun setTargetLanguage(code: String) {
        viewModelScope.launch { userPreferences.setTargetLanguage(code) }
    }

    fun swapLanguages() {
        viewModelScope.launch {
            val source = userPreferences.sourceLanguage.first()
            val target = userPreferences.targetLanguage.first()
            if (source == UserPreferences.AUTO_LANGUAGE) return@launch
            userPreferences.setSourceLanguage(target)
            userPreferences.setTargetLanguage(source)
        }
    }

    fun setTranslationMode(mode: String) {
        viewModelScope.launch { userPreferences.setTranslationMode(mode) }
    }

    fun setTranslationEngine(engine: String) {
        viewModelScope.launch { userPreferences.setTranslationEngine(engine) }
    }

    fun downloadRequiredLanguages() {
        viewModelScope.launch {
            _downloading.value = true
            _downloadError.value = false
            try {
                val source = userPreferences.sourceLanguage.first()
                val target = userPreferences.targetLanguage.first()
                if (source != UserPreferences.AUTO_LANGUAGE) {
                    translationRepository.downloadLanguage(source)
                }
                translationRepository.downloadLanguage(target)
            } catch (_: Exception) {
                _downloadError.value = true
            } finally {
                _downloading.value = false
            }
        }
    }

    fun clearDownloadError() {
        _downloadError.value = false
    }
}
