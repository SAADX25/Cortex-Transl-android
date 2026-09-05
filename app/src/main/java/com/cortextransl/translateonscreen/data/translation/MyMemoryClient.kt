package com.cortextransl.translateonscreen.data.translation

import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
class MyMemoryClient @Inject constructor() {

    private val gate = Semaphore(4)

    suspend fun translate(
        texts: List<String>,
        source: String,
        target: String
    ): List<String> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyList()
        val pair = "${iso(source)}|${iso(target)}"
        coroutineScope {
            texts.map { text ->
                async {
                    gate.withPermit { translateOne(text, pair) }
                }
            }.awaitAll()
        }
    }

    private fun translateOne(text: String, langPair: String): String {
        val encoded = URLEncoder.encode(text.take(500), StandardCharsets.UTF_8.name())
        val url = URL("https://api.mymemory.translated.net/get?q=$encoded&langpair=$langPair")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "CortexTranslate/1.6.7")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw MyMemoryException("MyMemory HTTP $code")
            }
            val raw = JSONObject(response)
                .optJSONObject("responseData")
                ?.optString("translatedText")
                .orEmpty()
                .trim()
            if (raw.isBlank()) throw MyMemoryException("Empty MyMemory response")
            return Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString().trim()
        } finally {
            connection.disconnect()
        }
    }

    private fun iso(code: String): String =
        code.lowercase(Locale.US).substringBefore("-").take(2)
}

class MyMemoryException(message: String) : Exception(message)
