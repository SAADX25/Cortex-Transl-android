package com.cortextransl.translateonscreen.data.ocr

import android.graphics.Rect
import android.util.Log
import com.cortextransl.translateonscreen.data.model.OcrBlock
import com.cortextransl.translateonscreen.data.preferences.UserPreferences
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

private enum class OcrScript { Latin, Chinese, Japanese, Korean, Devanagari }

@Singleton
class OcrRepository @Inject constructor() {

    private val recognizers = mutableMapOf<OcrScript, TextRecognizer>()

    suspend fun recognize(bitmap: android.graphics.Bitmap, sourceLanguage: String): List<OcrBlock> {
        val script = scriptFor(sourceLanguage)
        val image = InputImage.fromBitmap(bitmap, 0)
        val text = try {
            recognizer(script).process(image).await()
        } catch (error: Exception) {
            Log.w(TAG, "OCR failed for $script, falling back to Latin", error)
            if (script == OcrScript.Latin) throw error
            recognizer(OcrScript.Latin).process(image).await()
        }
        val imageHeight = bitmap.height
        val extracted = extract(text, imageHeight)
        return dedupe(extracted).take(MAX_BLOCKS)
    }

    private fun extract(text: Text, imageHeight: Int): List<OcrBlock> {
        val statusCut = (imageHeight * 0.045f).toInt()
        return text.textBlocks.flatMap { block ->
            val lines = block.lines
            val pieces = if (shouldSplit(lines)) {
                groupLines(lines).map { group ->
                    val box = group.mapNotNull { it.boundingBox }
                        .reduceOrNull { acc, r -> Rect(acc).apply { union(r) } }
                    Triple(group.joinToString("\n") { it.text.trim() }, box, group.size)
                }
            } else {
                listOf(
                    Triple(
                        block.text.trim(),
                        block.boundingBox,
                        lines.size.coerceAtLeast(1)
                    )
                )
            }
            pieces.mapNotNull { (content, box, lineCount) ->
                if (box == null || content.isBlank()) return@mapNotNull null
                if (box.bottom < statusCut) return@mapNotNull null
                if (box.width() < MIN_BLOCK_SIZE ||
                    box.height() < MIN_BLOCK_HEIGHT ||
                    isHairline(box) ||
                    isNoise(content)
                ) {
                    return@mapNotNull null
                }
                OcrBlock(
                    text = content,
                    boundingBox = Rect(box),
                    lineCount = lineCount
                )
            }
        }
    }

    private fun shouldSplit(lines: List<Text.Line>): Boolean {
        if (lines.size <= 1) return false
        val boxes = lines.mapNotNull { it.boundingBox }
        if (boxes.size < 2) return false
        val avgH = boxes.map { it.height().coerceAtLeast(1) }.average()
        val maxGap = boxes.zipWithNext { a, b -> b.top - a.bottom }.maxOrNull() ?: 0
        // Keep paragraph snippets together; only split clearly separate UI rows.
        if (maxGap > avgH * 2.2) return true
        // Title + description rows: a clearly bigger first line is its own label.
        val heights = boxes.map { it.height().coerceAtLeast(1) }
        if (heights.max().toFloat() / heights.min() > 1.3f) return true
        val xSpread = (boxes.maxOf { it.centerX() } - boxes.minOf { it.centerX() }).toFloat()
        if (xSpread > avgH * 5) return true
        return lines.size >= 8
    }

    /**
     * Splits a block into runs of lines that visually belong together: same text
     * height, normal line spacing and roughly the same horizontal position.
     * Keeps multi-line descriptions as one paragraph while separating titles,
     * badges and unrelated UI rows.
     */
    private fun groupLines(lines: List<Text.Line>): List<List<Text.Line>> {
        val groups = ArrayList<MutableList<Text.Line>>()
        var current = mutableListOf<Text.Line>()
        var prev: Rect? = null
        for (line in lines) {
            val box = line.boundingBox
            if (box == null) {
                if (current.isNotEmpty()) current.add(line)
                continue
            }
            val p = prev
            val startNew = p != null && run {
                val h = min(p.height(), box.height()).coerceAtLeast(1).toFloat()
                val ratio = max(p.height(), box.height()) / h
                val gap = box.top - p.bottom
                val leftShift = kotlin.math.abs(box.left - p.left)
                val centerShift = kotlin.math.abs(box.centerX() - p.centerX())
                ratio > 1.3f || gap > h * 1.1f || gap < -h * 0.5f ||
                    (leftShift > h * 2.5f && centerShift > h * 2.5f)
            }
            if (startNew && current.isNotEmpty()) {
                groups += current
                current = mutableListOf()
            }
            current.add(line)
            prev = box
        }
        if (current.isNotEmpty()) groups += current
        return groups
    }

    private fun recognizer(script: OcrScript): TextRecognizer {
        return recognizers.getOrPut(script) {
            when (script) {
                OcrScript.Latin -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                OcrScript.Chinese -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                OcrScript.Japanese -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
                OcrScript.Korean -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
                OcrScript.Devanagari -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
            }
        }
    }

    private fun scriptFor(languageCode: String): OcrScript {
        return when (languageCode) {
            "zh", "zh-Hans", "zh-Hant" -> OcrScript.Chinese
            "ja" -> OcrScript.Japanese
            "ko" -> OcrScript.Korean
            "hi", "mr", "ne", "sa" -> OcrScript.Devanagari
            else -> OcrScript.Latin
        }
    }

    companion object {
        private const val TAG = "OcrRepository"
        private const val MIN_BLOCK_SIZE = 16
        private const val MIN_BLOCK_HEIGHT = 12
        private const val MAX_BLOCKS = 48
        const val AUTO = UserPreferences.AUTO_LANGUAGE

        private val junkExact = setOf(
            "ok", "cancel", "start", "share", "close", "open", "home", "more",
            "en", "ar", "offline", "deepl", "ilove pdf", "ilovepdf"
        )

        private val junkContains = listOf(
            "share your screen", "share the screen", "cast your screen",
            "can see", "passwords", "screen sharing",
            "translation engine", "full-screen", "fullscreen",
            "floating translator", "deepl api", "on-device",
            "مشاركة الشاشة", "عندما تشارك", "كلمات المرور"
        )

        private fun isHairline(box: Rect): Boolean {
            if (box.height() <= 0) return true
            return box.height() < 12 && box.width().toFloat() / box.height() > 18f
        }

        private fun isNoise(text: String): Boolean {
            val t = text.trim()
            val letters = t.filter { it.isLetter() }
            if (letters.length < 3) return true
            if (t.startsWith("http", ignoreCase = true) || t.startsWith("www.", ignoreCase = true)) {
                return true
            }
            if (t.contains("→") || t.contains("->")) return true
            val lower = t.lowercase()
            if (lower in junkExact) return true
            if (junkContains.any { lower.contains(it) }) return true
            if (t.all { !it.isLetter() }) return true

            // Filter garbled Latin OCR artifacts from logos/watermarks (e.g. "(Släljl L) IALLL SLAC")
            if (isGibberish(t)) return true

            return false
        }

        private fun isGibberish(text: String): Boolean {
            // Triple identical characters in sequence (e.g. "lll", "xxx")
            if (Regex("(.)\\1{2,}").containsMatchIn(text)) return true

            val words = text.split(Regex("[\\s\\p{Punct}]+")).filter { it.isNotBlank() }
            if (words.isEmpty()) return true

            val latinWords = words.filter { w -> w.all { it in 'a'..'z' || it in 'A'..'Z' } }
            if (latinWords.isNotEmpty()) {
                // If Latin words with length >= 3 have zero vowels (a, e, i, o, u, y), likely logo noise
                val noVowelCount = latinWords.count { w ->
                    w.length >= 3 && !w.any { it.lowercaseChar() in "aeiouy" }
                }
                if (noVowelCount > 0 && noVowelCount == latinWords.size) {
                    return true
                }
            }

            // Strange symbol density
            val nonAlphanumeric = text.count { !it.isLetterOrDigit() && !it.isWhitespace() }
            if (nonAlphanumeric > 3 && nonAlphanumeric.toFloat() / text.length > 0.35f) {
                return true
            }

            return false
        }

        private fun dedupe(blocks: List<OcrBlock>): List<OcrBlock> {
            val sorted = blocks.sortedByDescending { area(it.boundingBox) }
            val kept = ArrayList<OcrBlock>(sorted.size)
            for (block in sorted) {
                val norm = normalize(block.text)
                val duplicate = kept.any { other ->
                    val otherNorm = normalize(other.text)
                    val overlap = iou(block.boundingBox, other.boundingBox)
                    overlap >= 0.22f ||
                        containedIn(block.boundingBox, other.boundingBox) ||
                        (similar(norm, otherNorm) && nearby(block.boundingBox, other.boundingBox)) ||
                        (otherNorm.contains(norm) && norm.length + 2 < otherNorm.length &&
                            nearby(block.boundingBox, other.boundingBox))
                }
                if (!duplicate) kept.add(block)
            }
            return kept.sortedWith(compareBy({ it.boundingBox.top }, { it.boundingBox.left }))
        }

        private fun normalize(text: String): String {
            return text.lowercase().replace(Regex("[\\s\\p{Punct}]+"), "")
        }

        private fun similar(a: String, b: String): Boolean {
            if (a == b) return true
            if (a.isEmpty() || b.isEmpty()) return false
            val shorter = if (a.length <= b.length) a else b
            val longer = if (a.length <= b.length) b else a
            return longer.contains(shorter) && shorter.length * 10 >= longer.length * 7
        }

        private fun area(box: Rect): Int = box.width().coerceAtLeast(0) * box.height().coerceAtLeast(0)

        private fun iou(a: Rect, b: Rect): Float {
            val left = max(a.left, b.left)
            val top = max(a.top, b.top)
            val right = min(a.right, b.right)
            val bottom = min(a.bottom, b.bottom)
            val inter = max(0, right - left) * max(0, bottom - top)
            if (inter <= 0) return 0f
            val union = area(a) + area(b) - inter
            return if (union <= 0) 0f else inter.toFloat() / union
        }

        private fun containedIn(inner: Rect, outer: Rect): Boolean {
            val left = max(inner.left, outer.left)
            val top = max(inner.top, outer.top)
            val right = min(inner.right, outer.right)
            val bottom = min(inner.bottom, outer.bottom)
            val interPx = max(0, right - left) * max(0, bottom - top)
            return interPx >= area(inner).coerceAtLeast(1) * 0.65f
        }

        private fun nearby(a: Rect, b: Rect): Boolean {
            val gapX = max(0, max(a.left, b.left) - min(a.right, b.right))
            val gapY = max(0, max(a.top, b.top) - min(a.bottom, b.bottom))
            val scale = max(a.height(), b.height()).coerceAtLeast(12)
            return gapX <= scale * 2 && gapY <= scale
        }
    }
}
