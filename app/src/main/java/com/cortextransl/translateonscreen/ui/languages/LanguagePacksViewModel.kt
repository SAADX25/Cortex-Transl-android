package com.cortextransl.translateonscreen.ui.languages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cortextransl.translateonscreen.data.translation.LanguageCatalog
import com.cortextransl.translateonscreen.data.translation.TranslationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LanguagePacksViewModel @Inject constructor(
    private val translationRepository: TranslationRepository
) : ViewModel() {

    val languages = LanguageCatalog.all()
    val downloadedLanguages: StateFlow<Set<String>> = translationRepository.downloadedLanguages

    private val _downloadingCode = MutableStateFlow<String?>(null)
    val downloadingCode: StateFlow<String?> = _downloadingCode.asStateFlow()

    private val _error = MutableStateFlow(false)
    val error: StateFlow<Boolean> = _error.asStateFlow()

    init {
        viewModelScope.launch { translationRepository.refreshDownloaded() }
    }

    fun download(code: String) {
        viewModelScope.launch {
            _downloadingCode.value = code
            _error.value = false
            try {
                translationRepository.downloadLanguage(code)
            } catch (_: Exception) {
                _error.value = true
            } finally {
                _downloadingCode.value = null
            }
        }
    }

    fun delete(code: String) {
        viewModelScope.launch {
            try {
                translationRepository.deleteLanguage(code)
            } catch (_: Exception) {
                _error.value = true
            }
        }
    }

    fun clearError() {
        _error.value = false
    }
}
