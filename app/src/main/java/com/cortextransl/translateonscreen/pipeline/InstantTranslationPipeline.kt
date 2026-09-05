package com.cortextransl.translateonscreen.pipeline

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.cortextransl.translateonscreen.R
import com.cortextransl.translateonscreen.capture.CaptureFrame
import com.cortextransl.translateonscreen.capture.ScreenFrameCapturer
import com.cortextransl.translateonscreen.data.model.OverlayBlock
import com.cortextransl.translateonscreen.data.model.PipelineResult
import com.cortextransl.translateonscreen.data.ocr.OcrEnhancer
import com.cortextransl.translateonscreen.data.ocr.OcrRepository
import com.cortextransl.translateonscreen.data.preferences.UserPreferences
import com.cortextransl.translateonscreen.data.translation.DeepLClient
import com.cortextransl.translateonscreen.data.translation.DeepLException
import com.cortextransl.translateonscreen.data.translation.LibreTranslateClient
import com.cortextransl.translateonscreen.data.translation.LibreTranslateException
import com.cortextransl.translateonscreen.data.translation.MyMemoryClient
import com.cortextransl.translateonscreen.data.translation.MyMemoryException
import com.cortextransl.translateonscreen.data.translation.ProperNouns
import com.cortextransl.translateonscreen.data.translation.TranslationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InstantTranslationPipeline @Inject constructor(
    private val capturer: ScreenFrameCapturer,
    private val ocrRepository: OcrRepository,
    private val translationRepository: TranslationRepository,
    private val deepLClient: DeepLClient,
    private val libreTranslateClient: LibreTranslateClient,
    private val myMemoryClient: MyMemoryClient,
    private val userPreferences: UserPreferences
) {
    suspend fun translateScreen(
        region: Rect?
    ): PipelineResult = withContext(Dispatchers.Default) {
        var frame: CaptureFrame? = null
        var ocrBitmap: Bitmap? = null
        var recycledFrame = false
        try {
            frame = capturer.grab()
            val sourcePref = userPreferences.sourceLanguage.first()
            val target = userPreferences.targetLanguage.first()

            val crop = cropIfNeeded(frame, region)
            ocrBitmap = crop.bitmap
            recycledFrame = crop.recycledSource
            if (ocrBitmap.isRecycled) {
                return@withContext PipelineResult.Error(R.string.error_translation_failed)
            }

            val engine = userPreferences.translationEngine.first()

            // Fast path: identical picture as last time (auto modes, game dialogue
            // that has not advanced) -> reuse the previous result, skip OCR entirely.
            val signature = OcrEnhancer.signature(ocrBitmap)
            val contextKey = "$region|$sourcePref|$target|$engine"
            synchronized(cacheLock) {
                val last = lastResult
                if (last != null && last.contextKey == contextKey &&
                    OcrEnhancer.similar(last.signature, signature)
                ) {
                    return@withContext last.result
                }
            }

            // Region / game modes: upscale + contrast boost for stylised fonts.
            val prepared = OcrEnhancer.prepare(
                ocrBitmap,
                upscale = region != null,
                enhance = true
            )
            val rawBlocks = try {
                ocrRepository.recognize(prepared.bitmap, sourcePref).map { block ->
                    block.copy(boundingBox = prepared.mapBack(block.boundingBox))
                }
            } finally {
                prepared.release()
            }
            val ocrBlocks = rawBlocks.filter { block ->
                !alreadyInTargetScript(block.text, target)
            }
            if (ocrBlocks.isEmpty()) {
                return@withContext PipelineResult.NoTextFound
            }

            val detectedSource = if (sourcePref == UserPreferences.AUTO_LANGUAGE) {
                translationRepository.detectLanguage(ocrBlocks.joinToString(" ") { it.text })
                    ?: "en"
            } else {
                sourcePref
            }

            if (detectedSource == target) {
                val blocks = suppressOverlaps(
                    ocrBlocks.map { block ->
                        OverlayBlock(
                            originalText = block.text,
                            translatedText = block.text,
                            rect = mapToScreen(block.boundingBox, frame, crop.offsetX, crop.offsetY),
                            lineCount = block.lineCount
                        )
                    }
                )
                return@withContext PipelineResult.Success(blocks, detectedSource, target)
            }

            // OCR blocks contain hard line breaks; translation engines produce far
            // better sentences when they see one flowing paragraph.
            val originals = ocrBlocks.map { normalizeSource(it.text) }
            val translated = try {
                translateCached(
                    engine = engine,
                    texts = originals,
                    sourcePref = sourcePref,
                    detectedSource = detectedSource,
                    target = target
                )
            } catch (error: DeepLException) {
                return@withContext PipelineResult.Error(
                    messageRes = R.string.error_deepl_failed,
                    detail = error.message
                )
            } catch (error: LibreTranslateException) {
                return@withContext PipelineResult.Error(
                    messageRes = R.string.error_libre_failed,
                    detail = error.message
                )
            } catch (error: MyMemoryException) {
                return@withContext PipelineResult.Error(
                    messageRes = R.string.error_mymemory_failed,
                    detail = error.message
                )
            }

            val overlayBlocks = suppressOverlaps(
                ocrBlocks.mapIndexedNotNull { index, block ->
                    val translatedText = cleanTranslation(
                        original = block.text,
                        translated = translated.getOrElse(index) { block.text },
                        target = target
                    ) ?: return@mapIndexedNotNull null
                    OverlayBlock(
                        originalText = block.text,
                        translatedText = translatedText,
                        rect = mapToScreen(block.boundingBox, frame, crop.offsetX, crop.offsetY),
                        lineCount = block.lineCount
                    )
                }
            )
            val success = PipelineResult.Success(overlayBlocks, detectedSource, target)
            synchronized(cacheLock) {
                lastResult = LastResult(contextKey, signature, success)
            }
            success
        } catch (error: Exception) {
            Log.e(TAG, "Translation pipeline failed", error)
            PipelineResult.Error(
                messageRes = R.string.error_translation_failed,
                detail = error.localizedMessage
            )
        } finally {
            ocrBitmap?.takeIf { it !== frame?.bitmap && !it.isRecycled }?.recycle()
            if (!recycledFrame) {
                frame?.bitmap?.takeIf { !it.isRecycled }?.recycle()
            }
        }
    }

    private val cacheLock = Any()
    private var lastResult: LastResult? = null
    private val translationCache = object : LinkedHashMap<String, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > TRANSLATION_CACHE_SIZE
    }

    private class LastResult(val contextKey: String, val signature: ByteArray, val result: PipelineResult.Success)

    /** Invalidate the "unchanged screen" fast path (e.g. after settings change). */
    fun invalidate() {
        synchronized(cacheLock) { lastResult = null }
    }

    /**
     * Memoises translations per (engine, languages, text). Repeated UI strings
     * and game dialogue that stays on screen cost nothing after the first pass.
     */
    private suspend fun translateCached(
        engine: String,
        texts: List<String>,
        sourcePref: String,
        detectedSource: String,
        target: String
    ): List<String> {
        val prefix = "$engine|$sourcePref|$detectedSource|$target|"
        val results = arrayOfNulls<String>(texts.size)
        val missIdx = ArrayList<Int>()
        val miss = ArrayList<String>()
        synchronized(cacheLock) {
            texts.forEachIndexed { index, text ->
                val hit = translationCache[prefix + text]
                if (hit != null) results[index] = hit else {
                    missIdx += index
                    miss += text
                }
            }
        }
        if (miss.isNotEmpty()) {
            val fresh = translateWithEngine(engine, miss, sourcePref, detectedSource, target)
            synchronized(cacheLock) {
                missIdx.forEachIndexed { j, i ->
                    val value = fresh.getOrElse(j) { miss[j] }
                    results[i] = value
                    if (value.isNotBlank()) translationCache[prefix + miss[j]] = value
                }
            }
        }
        return results.map { it.orEmpty() }
    }

    private suspend fun translateWithEngine(
        engine: String,
        texts: List<String>,
        sourcePref: String,
        detectedSource: String,
        target: String
    ): List<String> {
        val results = arrayOfNulls<String>(texts.size)
        val pendingIdx = ArrayList<Int>()
        val pending = ArrayList<String>()
        texts.forEachIndexed { index, text ->
            val override = ProperNouns.override(text, target)
            if (override != null) {
                results[index] = override
            } else {
                pendingIdx += index
                pending += text
            }
        }
        if (pending.isNotEmpty()) {
            val cloudSource = if (sourcePref == UserPreferences.AUTO_LANGUAGE) null else detectedSource
            val translated = when (engine) {
                UserPreferences.ENGINE_DEEPL -> {
                    val apiKey = userPreferences.deeplApiKey.first()
                    if (apiKey.isBlank()) throw DeepLException("Missing DeepL API key")
                    deepLClient.translate(pending, cloudSource, target, apiKey)
                }
                UserPreferences.ENGINE_LIBRE -> {
                    libreTranslateClient.translate(
                        texts = pending,
                        source = cloudSource,
                        target = target,
                        apiKey = userPreferences.libreApiKey.first(),
                        baseUrl = userPreferences.libreBaseUrl.first()
                    )
                }
                UserPreferences.ENGINE_MYMEMORY -> {
                    myMemoryClient.translate(pending, detectedSource, target)
                }
                else -> {
                    translationRepository.ensureModels(detectedSource, target)
                    translateOfflineProtected(pending, detectedSource, target)
                }
            }
            pendingIdx.forEachIndexed { j, i ->
                results[i] = translated.getOrElse(j) { pending[j] }
            }
        }
        return results.map { it.orEmpty() }
    }

    /**
     * Offline ML Kit tends to transliterate or mangle brand names, domains and
     * product codes (Eneba -> إنيبا, eneba.com -> إنيبا.كوم). Such tokens are
     * swapped for placeholders, translated, then restored. If the engine drops a
     * placeholder the plain translation is used instead.
     */
    private suspend fun translateOfflineProtected(
        texts: List<String>,
        source: String,
        target: String
    ): List<String> {
        val protectedTexts = texts.map { protectTokens(it) }
        val translated = translationRepository.translateAll(
            protectedTexts.map { it.text },
            source,
            target
        )
        val fallbackIdx = ArrayList<Int>()
        val results = protectedTexts.mapIndexed { index, item ->
            var out = translated.getOrElse(index) { item.text }
            var ok = true
            item.tokens.forEachIndexed { i, token ->
                val key = placeholder(i)
                if (!out.contains(key)) {
                    ok = false
                } else {
                    out = out.replace(key, token)
                }
            }
            if (!ok && item.tokens.isNotEmpty()) fallbackIdx += index
            out
        }.toMutableList()
        if (fallbackIdx.isNotEmpty()) {
            val plain = translationRepository.translateAll(fallbackIdx.map { texts[it] }, source, target)
            fallbackIdx.forEachIndexed { j, i -> results[i] = plain.getOrElse(j) { texts[i] } }
        }
        return results
    }

    private fun cropIfNeeded(frame: CaptureFrame, region: Rect?): Crop {
        if (region == null) {
            return Crop(frame.bitmap, 0, 0, recycledSource = false)
        }

        val left = (region.left / frame.scaleX).toInt().coerceIn(0, frame.bitmap.width - 1)
        val top = (region.top / frame.scaleY).toInt().coerceIn(0, frame.bitmap.height - 1)
        val right = (region.right / frame.scaleX).toInt().coerceIn(left + 1, frame.bitmap.width)
        val bottom = (region.bottom / frame.scaleY).toInt().coerceIn(top + 1, frame.bitmap.height)
        val cropped = Bitmap.createBitmap(frame.bitmap, left, top, right - left, bottom - top)
        frame.bitmap.recycle()
        return Crop(cropped, left, top, recycledSource = true)
    }

    private fun mapToScreen(
        box: Rect,
        frame: CaptureFrame,
        offsetX: Int,
        offsetY: Int
    ): Rect {
        return Rect(
            ((offsetX + box.left) * frame.scaleX).toInt(),
            ((offsetY + box.top) * frame.scaleY).toInt(),
            ((offsetX + box.right) * frame.scaleX).toInt(),
            ((offsetY + box.bottom) * frame.scaleY).toInt()
        )
    }

    private data class Crop(
        val bitmap: Bitmap,
        val offsetX: Int,
        val offsetY: Int,
        val recycledSource: Boolean
    )

    companion object {
        private const val TAG = "TranslationPipeline"
        private const val TRANSLATION_CACHE_SIZE = 600
        private val WS = Regex("\\s+")
        private val WS_PUNCT = Regex("[\\s\\p{Punct}]+")
        private val WS_BOUNDARY = Regex("(?<=\\s)|(?=\\s)")
        private val HYPHEN_BREAK = Regex("(\\p{L})-\\s*\\n\\s*(\\p{L})")
        private val LINE_BREAK = Regex("\\s*\\n\\s*")
        private val MULTI_SPACE = Regex("[ \\t]{2,}")

        /** Joins OCR lines into one paragraph and repairs hyphenated line breaks. */
        fun normalizeSource(text: String): String {
            return text
                .replace(HYPHEN_BREAK, "$1$2")
                .replace(LINE_BREAK, " ")
                .replace(MULTI_SPACE, " ")
                .replace(Regex("\\s+([,.;:!?،؛؟])"), "$1")
                .trim()
        }

        private val domainRegex = Regex("^[\\p{L}\\p{N}-]+(\\.[\\p{L}\\p{N}-]+)+(/\\S*)?$")

        /** Brand-like tokens that must survive translation untouched. */
        fun isProtectedToken(raw: String, allowCapsCodes: Boolean = true): Boolean {
            val token = raw.trim().trimEnd('.', ',', ':', ';', '!', '?', ')', ']').trimStart('(', '[')
            if (token.length < 2) return false
            if (token.startsWith("http", ignoreCase = true) || token.startsWith("www.", ignoreCase = true)) return true
            if (domainRegex.matches(token) && token.contains('.')) return true
            if (token.startsWith("@") || token.startsWith("#")) return true
            val letters = token.filter { it.isLetter() }
            if (letters.isEmpty()) return false
            val latin = letters.all { it in 'a'..'z' || it in 'A'..'Z' }
            if (!latin) return false
            if (token.any { it.isDigit() }) return true
            // Mixed case inside the word (AllKeyShop, DeepL, iPhone, YouTube).
            if (letters.drop(1).any { it.isUpperCase() } && letters.any { it.isLowerCase() }) return true
            // Short ALL-CAPS codes (DNS, VPN, USB, PS5-like handled above).
            if (allowCapsCodes && letters.length in 2..5 && letters.all { it.isUpperCase() }) return true
            return ProperNouns.lookupArabic(token)?.let { it.any { c -> c in 'A'..'Z' || c in 'a'..'z' } } == true
        }

        private fun placeholder(index: Int): String = "XQ${index + 1}"

        private fun protectTokens(text: String): ProtectedText {
            val tokens = ArrayList<String>()
            val sb = StringBuilder()
            val parts = text.split(WS_BOUNDARY)
            // Headings written in ALL CAPS ("BUY NOW") must still be translated.
            val wordsWithLetters = parts.filter { p -> p.any { it.isLetter() } }
            val capsWords = wordsWithLetters.count { p -> p.filter { it.isLetter() }.all { it.isUpperCase() } }
            val allowCaps = wordsWithLetters.isNotEmpty() && capsWords * 2 < wordsWithLetters.size
            for (part in parts) {
                if (part.isNotBlank() && isProtectedToken(part, allowCaps)) {
                    // Keep trailing punctuation outside the placeholder.
                    val core = part.trimEnd('.', ',', ':', ';', '!', '?', ')', ']')
                    val tail = part.substring(core.length)
                    tokens += core
                    sb.append(placeholder(tokens.size - 1)).append(tail)
                } else {
                    sb.append(part)
                }
            }
            return ProtectedText(sb.toString(), tokens)
        }

        private class ProtectedText(val text: String, val tokens: List<String>)

        private fun alreadyInTargetScript(text: String, target: String): Boolean {
            if (target != "ar" && !target.startsWith("ar")) return false
            val letters = text.filter { it.isLetter() }
            if (letters.isEmpty()) return false
            val arabic = letters.count { ch ->
                val block = Character.UnicodeBlock.of(ch)
                block == Character.UnicodeBlock.ARABIC ||
                    block == Character.UnicodeBlock.ARABIC_SUPPLEMENT ||
                    block == Character.UnicodeBlock.ARABIC_EXTENDED_A
            }
            return arabic * 2 >= letters.length
        }

        private fun suppressOverlaps(blocks: List<OverlayBlock>): List<OverlayBlock> {
            val sorted = blocks.sortedByDescending { area(it.rect) }
            val kept = ArrayList<OverlayBlock>(sorted.size)
            for (block in sorted) {
                val duplicate = kept.any { other ->
                    val sameText = normalize(other.translatedText) == normalize(block.translatedText)
                    intersects(other.rect, block.rect) ||
                        (sameText && nearby(other.rect, block.rect)) ||
                        (sameText && closeCenters(other.rect, block.rect))
                }
                if (!duplicate) kept.add(block)
            }
            return kept.sortedWith(compareBy({ it.rect.top }, { it.rect.left }))
        }

        private fun cleanTranslation(original: String, translated: String, target: String): String? {
            var text = translated.trim()
            if (text.isEmpty()) return null
            val originalTrim = original.trim()
            if (text.contains(originalTrim, ignoreCase = true) && text.length > originalTrim.length + 2) {
                text = text.replace(originalTrim, "", ignoreCase = true)
                    .replace("؟", "")
                    .replace("?", "")
                    .trim()
            }
            if (hasArabic(text)) {
                // Drop echoed source words, but keep brand names / domains / codes
                // (Eneba, Google Play, eneba.com, 4K) which belong in the translation.
                originalTrim.split(WS)
                    .filter { it.length >= 3 && !isProtectedToken(it) }
                    .forEach { word ->
                        text = text.replace(Regex("(?i)(?<![\\p{L}\\p{N}])${Regex.escape(word)}(?![\\p{L}\\p{N}])"), " ")
                    }
            }
            text = collapseDuplicateWords(text)
                .replace(WS, " ")
                .trim()
            if (text.isEmpty()) return null
            if (target.startsWith("ar")) {
                text = postProcessArabic(originalTrim, text)
                if (isArabicGibberish(text)) return null
            }
            if (target.startsWith("ar") && looksLatin(text) &&
                normalize(text) == normalize(originalTrim)
            ) {
                return null
            }
            val letters = text.filter { it.isLetter() }
            if (letters.isEmpty()) return null
            val lower = text.lowercase()
            if (lower in setOf("و", "في", "من", "أو", "ال", "and", "in", "or")) return null
            return text
        }

        private fun postProcessArabic(original: String, arabicText: String): String {
            var text = arabicText
            val origLower = original.lowercase()

            // 1. Social media context: "post" mistranslated to "المنصب" (job position)
            if (origLower.contains(Regex("\\bposts?\\b"))) {
                text = text.replace("هذا المنصب غير متاح", "هذا المنشور غير متوفر")
                    .replace("هذا المنصب", "هذا المنشور")
                    .replace("لرؤية هذا المنصب", "لمشاهدة هذا المنشور")
                    .replace("عرض المنصب", "عرض المنشور")
                    .replace("مشاركة المنصب", "مشاركة المنشور")
                    .replace(Regex("\\bالمنصب\\b"), "المنشور")
                    .replace(Regex("\\bمنصب\\b"), "منشور")
            }

            // 2. Gaming platforms & App brands (e.g. Steam -> بخار)
            if (origLower.contains("steam") && text.contains("بخار")) {
                text = text.replace(Regex("\\bبخار\\b"), "ستيم")
            }
            if (origLower.contains("magicmic") || origLower.contains("magic mic")) {
                text = text.replace("السحر يوتيوب", "ماجيك مايك")
                    .replace(Regex("\\bالسحر\\b"), "ماجيك مايك")
            }
            if (origLower.contains("dns") && (text.contains("ماء") || text.contains("طاقات") || text.contains("مبدل"))) {
                text = "مغيّر DNS"
            }

            // 3. Search and Store terms
            if (origLower.contains("people also") || text.contains("الناس أيضا البحث عن")) {
                text = text.replace("الناس أيضا البحث عن", "عمليات بحث ذات صلة")
                    .replace("الناس أيضا يسألون", "أسئلة شائعة")
            }
            if (text.contains("قراءة خدمة العملاء مراجعات") || origLower.contains("customer service reviews")) {
                text = text.replace("قراءة خدمة العملاء مراجعات", "قراءة تقييمات العملاء")
            }
            if (origLower.contains("legit") && (text.startsWith("هو ") || text.contains("شرعي"))) {
                text = text.replace(Regex("^هو\\s+"), "هل ")
                    .replace(Regex("[\\.\\s]+شرعي.*"), " موثوق وآمن؟")
            }

            // 4. Remove trailing broken single-letter OCR noise (e.g. "غير متاح ا.")
            text = text.replace(Regex("\\s+[ا-ي]\\s*\\.?$"), "")
                .replace(Regex("^[ا-ي]\\s+"), "")
                .trim()

            return text
        }

        /**
         * Detects Arabic output produced from OCR noise / logos, e.g. "أولال ل"
         * (from "Släljl L"). Such strings carry no meaning and only clutter the overlay.
         */
        private fun isArabicGibberish(text: String): Boolean {
            val words = text.split(WS).filter { it.isNotBlank() }
            if (words.isEmpty()) return true
            // Same letter repeated three or more times in a row.
            if (Regex("([\\u0621-\\u064A])\\1{2,}").containsMatchIn(text)) return true
            val arabicWords = words.filter { w -> w.any { Character.UnicodeBlock.of(it) == Character.UnicodeBlock.ARABIC } }
            if (arabicWords.isEmpty()) return false
            // Stand-alone single letters other than "و" are not real words.
            val strayLetters = arabicWords.count { w ->
                val letters = w.filter { it.isLetter() }
                letters.length == 1 && letters != "و"
            }
            if (strayLetters > 0 && words.size <= 3) return true
            if (strayLetters * 2 >= arabicWords.size) return true
            // Words made only of repeated ا/ل runs (typical of logo OCR garbage).
            val runs = arabicWords.count { w -> w.length >= 3 && w.all { it == 'ا' || it == 'ل' || it == 'أ' || it == 'إ' } }
            return runs > 0 && runs * 2 >= arabicWords.size
        }

        private fun collapseDuplicateWords(text: String): String {
            val parts = text.split(WS).filter { it.isNotBlank() }
            if (parts.isEmpty()) return text
            val out = ArrayList<String>(parts.size)
            for (part in parts) {
                if (out.isEmpty() || !out.last().equals(part, ignoreCase = true)) {
                    out += part
                }
            }
            return out.joinToString(" ")
        }

        private fun hasArabic(text: String): Boolean =
            text.any { Character.UnicodeBlock.of(it) == Character.UnicodeBlock.ARABIC }

        private fun looksLatin(text: String): Boolean {
            val letters = text.filter { it.isLetter() }
            if (letters.isEmpty()) return false
            return letters.count { it in 'A'..'Z' || it in 'a'..'z' } * 2 >= letters.length
        }

        private fun intersects(a: Rect, b: Rect): Boolean {
            return iou(a, b) >= 0.08f
        }

        private fun normalize(text: String): String {
            return text.lowercase().replace(WS_PUNCT, "")
        }

        private fun area(box: Rect): Int =
            box.width().coerceAtLeast(0) * box.height().coerceAtLeast(0)

        private fun iou(a: Rect, b: Rect): Float {
            val left = maxOf(a.left, b.left)
            val top = maxOf(a.top, b.top)
            val right = minOf(a.right, b.right)
            val bottom = minOf(a.bottom, b.bottom)
            val inter = maxOf(0, right - left) * maxOf(0, bottom - top)
            if (inter <= 0) return 0f
            val union = area(a) + area(b) - inter
            return if (union <= 0) 0f else inter.toFloat() / union
        }

        private fun nearby(a: Rect, b: Rect): Boolean {
            val gapX = maxOf(0, maxOf(a.left, b.left) - minOf(a.right, b.right))
            val gapY = maxOf(0, maxOf(a.top, b.top) - minOf(a.bottom, b.bottom))
            val scale = maxOf(a.height(), b.height()).coerceAtLeast(16)
            return gapX <= scale * 3 && gapY <= scale * 2
        }

        private fun closeCenters(a: Rect, b: Rect): Boolean {
            val dx = a.centerX() - b.centerX()
            val dy = a.centerY() - b.centerY()
            val limit = maxOf(a.height(), b.height(), 40) * 3
            return dx * dx + dy * dy <= limit * limit
        }
    }
}
