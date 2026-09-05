package com.cortextransl.translateonscreen.data.translation

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslationRepository @Inject constructor() {

    private val modelManager = RemoteModelManager.getInstance()
    private val languageIdentifier = LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder()
            .setConfidenceThreshold(0.4f)
            .build()
    )

    private val translatorMutex = Mutex()
    private var cachedKey: Pair<String, String>? = null
    private var cachedTranslator: Translator? = null

    private val _downloadedLanguages = MutableStateFlow<Set<String>>(emptySet())
    val downloadedLanguages: StateFlow<Set<String>> = _downloadedLanguages.asStateFlow()

    suspend fun refreshDownloaded(): Set<String> {
        val models = modelManager.getDownloadedModels(TranslateRemoteModel::class.java).await()
        val languages = models.map { it.language }.toSet()
        _downloadedLanguages.value = languages
        return languages
    }

    fun isDownloaded(code: String): Boolean = code in _downloadedLanguages.value

    suspend fun downloadLanguage(code: String) {
        val model = TranslateRemoteModel.Builder(code).build()
        modelManager.download(model, DownloadConditions.Builder().build()).await()
        refreshDownloaded()
    }

    suspend fun deleteLanguage(code: String) {
        val model = TranslateRemoteModel.Builder(code).build()
        modelManager.deleteDownloadedModel(model).await()
        translatorMutex.withLock {
            val key = cachedKey
            if (key != null && (key.first == code || key.second == code)) {
                cachedTranslator?.close()
                cachedTranslator = null
                cachedKey = null
            }
        }
        refreshDownloaded()
    }

    suspend fun detectLanguage(text: String): String? {
        val sample = text.take(400)
        if (sample.isBlank()) return null
        val identified = languageIdentifier.identifyLanguage(sample).await()
        if (identified.isNullOrBlank() || identified == UNDETERMINED) return null
        return TranslateLanguage.fromLanguageTag(identified)
            ?: TranslateLanguage.fromLanguageTag(identified.take(2))
    }

    suspend fun ensureModels(source: String, target: String) {
        translator(source, target).downloadModelIfNeeded().await()
        refreshDownloaded()
    }

    suspend fun translateAll(texts: List<String>, source: String, target: String): List<String> {
        if (texts.isEmpty()) return emptyList()
        val client = translator(source, target)
        client.downloadModelIfNeeded().await()
        val cache = LinkedHashMap<String, String>()
        return texts.map { original ->
            cache.getOrPut(original) {
                client.translate(original).await()
            }
        }
    }

    private suspend fun translator(source: String, target: String): Translator {
        return translatorMutex.withLock {
            val key = source to target
            val existing = cachedTranslator
            if (cachedKey == key && existing != null) {
                existing
            } else {
                existing?.close()
                val created = Translation.getClient(
                    TranslatorOptions.Builder()
                        .setSourceLanguage(source)
                        .setTargetLanguage(target)
                        .build()
                )
                cachedKey = key
                cachedTranslator = created
                created
            }
        }
    }

    companion object {
        private const val UNDETERMINED = "und"
    }
}
