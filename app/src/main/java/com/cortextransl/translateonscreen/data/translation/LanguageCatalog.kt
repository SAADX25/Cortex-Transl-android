package com.cortextransl.translateonscreen.data.translation

import com.cortextransl.translateonscreen.data.preferences.UserPreferences
import com.google.mlkit.nl.translate.TranslateLanguage
import java.util.Locale

data class AppLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String
)

object LanguageCatalog {

    fun all(): List<AppLanguage> {
        return TranslateLanguage.getAllLanguages()
            .map { code -> fromCode(code) }
            .sortedBy { it.displayName.lowercase(Locale.getDefault()) }
    }

    fun fromCode(code: String): AppLanguage {
        if (code == UserPreferences.AUTO_LANGUAGE) {
            return AppLanguage(code, "Auto", "Auto")
        }
        val locale = localeFor(code)
        val display = locale.getDisplayLanguage(Locale.getDefault()).ifBlank { code }
        val native = locale.getDisplayLanguage(locale).ifBlank { display }
        return AppLanguage(
            code = code,
            displayName = display.replaceFirstChar { it.titlecase(Locale.getDefault()) },
            nativeName = native.replaceFirstChar { it.titlecase(locale) }
        )
    }

    fun displayName(code: String): String = fromCode(code).displayName

    fun shortCode(code: String): String {
        if (code == UserPreferences.AUTO_LANGUAGE) return "AUTO"
        return code.uppercase(Locale.US)
    }

    private fun localeFor(code: String): Locale {
        return when (code) {
            TranslateLanguage.CHINESE -> Locale.CHINESE
            TranslateLanguage.ENGLISH -> Locale.ENGLISH
            TranslateLanguage.FRENCH -> Locale.FRENCH
            TranslateLanguage.GERMAN -> Locale.GERMAN
            TranslateLanguage.JAPANESE -> Locale.JAPANESE
            TranslateLanguage.KOREAN -> Locale.KOREAN
            TranslateLanguage.ITALIAN -> Locale.ITALIAN
            else -> Locale.forLanguageTag(code)
        }
    }
}
