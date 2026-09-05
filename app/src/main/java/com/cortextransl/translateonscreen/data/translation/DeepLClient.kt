package com.cortextransl.translateonscreen.data.translation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeepLClient @Inject constructor() {

    suspend fun translate(
        texts: List<String>,
        source: String?,
        target: String,
        apiKey: String
    ): List<String> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyList()
        val targetCode = toDeepLCode(target)
            ?: throw DeepLException("Target language is not supported by DeepL")
        val sourceCode = source?.takeUnless { it == "auto" }?.let { toDeepLCode(it) }
        val key = apiKey.trim()
        if (key.isEmpty()) throw DeepLException("Missing DeepL API key")

        val endpoints = endpointsFor(key)
        var lastError: Exception? = null
        for (endpoint in endpoints) {
            try {
                return@withContext request(endpoint, key, texts, sourceCode, targetCode)
            } catch (error: DeepLException) {
                if (error.httpCode == 403 || error.httpCode == 404) {
                    lastError = error
                    continue
                }
                throw error
            }
        }
        throw lastError ?: DeepLException("DeepL request failed")
    }

    private fun request(
        endpoint: String,
        apiKey: String,
        texts: List<String>,
        source: String?,
        target: String
    ): List<String> {
        val body = buildString {
            append("target_lang=").append(enc(target))
            if (!source.isNullOrBlank()) {
                append("&source_lang=").append(enc(source))
            }
            texts.forEach { text ->
                append("&text=").append(enc(text))
            }
        }.toByteArray(StandardCharsets.UTF_8)

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Authorization", "DeepL-Auth-Key $apiKey")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Accept", "application/json")
            setFixedLengthStreamingMode(body.size)
        }
        try {
            connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw DeepLException(messageFor(code, response), code)
            }
            val translations = JSONObject(response).getJSONArray("translations")
            return List(translations.length()) { index ->
                translations.getJSONObject(index).getString("text")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun endpointsFor(apiKey: String): List<String> {
        val free = "https://api-free.deepl.com/v2/translate"
        val pro = "https://api.deepl.com/v2/translate"
        return if (apiKey.endsWith(":fx", ignoreCase = true)) {
            listOf(free, pro)
        } else {
            listOf(pro, free)
        }
    }

    private fun messageFor(code: Int, body: String): String = when (code) {
        401, 403 -> "DeepL API key is invalid"
        429 -> "DeepL quota exceeded. Try again later"
        456 -> "DeepL character quota exceeded"
        else -> body.ifBlank { "DeepL HTTP $code" }.take(180)
    }

    private fun enc(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    companion object {
        private val supported = setOf(
            "AR", "BG", "CS", "DA", "DE", "EL", "EN", "ES", "ET", "FI", "FR",
            "HU", "ID", "IT", "JA", "KO", "LT", "LV", "NB", "NL", "PL", "PT",
            "RO", "RU", "SK", "SL", "SV", "TR", "UK", "ZH"
        )

        fun toDeepLCode(mlKitCode: String): String? {
            val mapped = when (mlKitCode.lowercase(Locale.US)) {
                "zh", "zh-hans", "zh-hant" -> "ZH"
                "pt", "pt-pt", "pt-br" -> "PT"
                "en", "en-us", "en-gb" -> "EN"
                "no", "nb" -> "NB"
                else -> mlKitCode.uppercase(Locale.US).substringBefore("-").take(2)
            }
            return mapped.takeIf { it in supported }
        }
    }
}

class DeepLException(message: String, val httpCode: Int = 0) : Exception(message)
