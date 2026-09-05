package com.cortextransl.translateonscreen.data.translation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibreTranslateClient @Inject constructor() {

    suspend fun translate(
        texts: List<String>,
        source: String?,
        target: String,
        apiKey: String,
        baseUrl: String
    ): List<String> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyList()
        val targetCode = iso(target)
        val sourceCode = source?.takeUnless { it == "auto" }?.let { iso(it) } ?: "auto"
        val endpoints = endpointsFor(baseUrl)
        var lastError: Exception? = null
        for (endpoint in endpoints) {
            try {
                return@withContext translateAt(endpoint, texts, sourceCode, targetCode, apiKey)
            } catch (error: LibreTranslateException) {
                lastError = error
                if (error.httpCode in 400..404 || error.httpCode == 429 || error.httpCode == 502) {
                    continue
                }
                throw error
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw lastError ?: LibreTranslateException("LibreTranslate request failed")
    }

    private fun translateAt(
        endpoint: String,
        texts: List<String>,
        source: String,
        target: String,
        apiKey: String
    ): List<String> {
        return try {
            request(endpoint, texts, source, target, apiKey)
        } catch (error: LibreTranslateException) {
            if (texts.size > 1 && (error.httpCode == 400 || error.httpCode == 422)) {
                texts.map { text -> request(endpoint, listOf(text), source, target, apiKey).first() }
            } else {
                throw error
            }
        }
    }

    private fun request(
        endpoint: String,
        texts: List<String>,
        source: String,
        target: String,
        apiKey: String
    ): List<String> {
        val payload = JSONObject().apply {
            if (texts.size == 1) put("q", texts.first()) else put("q", JSONArray(texts))
            put("source", source)
            put("target", target)
            put("format", "text")
            if (apiKey.isNotBlank()) put("api_key", apiKey)
        }
        val body = payload.toString().toByteArray(StandardCharsets.UTF_8)
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 25_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "CortexTranslate/1.6.7")
            setFixedLengthStreamingMode(body.size)
        }
        try {
            connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw LibreTranslateException("LibreTranslate HTTP $code", code)
            }
            return parse(response, texts.size)
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(response: String, expected: Int): List<String> {
        val json = JSONObject(response)
        val value = json.opt("translatedText")
        val result = when (value) {
            is JSONArray -> List(value.length()) { value.getString(it) }
            is String -> listOf(value)
            else -> emptyList()
        }
        if (result.size != expected) {
            throw LibreTranslateException("LibreTranslate returned ${result.size} texts")
        }
        return result
    }

    private fun endpointsFor(baseUrl: String): List<String> {
        val primary = normalize(baseUrl.ifBlank { DEFAULT_BASE })
        val defaultPrimary = normalize(DEFAULT_BASE)
        if (primary != defaultPrimary) {
            return listOf(primary)
        }
        return (listOf(primary) + FALLBACKS.map { normalize(it) }).distinct()
    }

    companion object {
        const val DEFAULT_BASE = "https://libretranslate.com"

        val FALLBACKS = listOf(
            "https://libretranslate.com",
            "https://translate.fortytwo-it.com",
            "https://lt.vern.cc"
        )

        fun normalize(baseUrl: String): String {
            val clean = baseUrl.trim().trimEnd('/')
            return if (clean.endsWith("/translate")) clean else "$clean/translate"
        }

        private fun iso(code: String): String =
            code.lowercase(Locale.US).substringBefore("-").take(2)
    }
}

class LibreTranslateException(message: String, val httpCode: Int = 0) : Exception(message)
