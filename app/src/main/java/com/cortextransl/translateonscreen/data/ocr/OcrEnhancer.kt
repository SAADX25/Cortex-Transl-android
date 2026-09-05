package com.cortextransl.translateonscreen.data.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Prepares captured frames for OCR.
 *
 * Game dialogue boxes and small captions are often rendered with stylised
 * fonts on busy backgrounds and are captured at a fairly low resolution.
 * ML Kit recognises such text far more reliably when the image is upscaled
 * (letters ~30 px tall or more) and the contrast is boosted. The enhancer
 * also computes a tiny grayscale signature that lets the pipeline skip OCR
 * entirely when the screen has not changed (auto modes).
 */
object OcrEnhancer {

    class Prepared(val bitmap: Bitmap, val scale: Float, val owned: Boolean) {
        fun mapBack(box: Rect): Rect {
            if (scale == 1f) return Rect(box)
            return Rect(
                (box.left / scale).toInt(),
                (box.top / scale).toInt(),
                (box.right / scale + 0.5f).toInt(),
                (box.bottom / scale + 0.5f).toInt()
            )
        }

        fun release() {
            if (owned && !bitmap.isRecycled) bitmap.recycle()
        }
    }

    /**
     * @param upscale allow enlarging small crops (region / game modes).
     * @param enhance apply grayscale + contrast boost.
     */
    fun prepare(source: Bitmap, upscale: Boolean, enhance: Boolean): Prepared {
        val w = source.width
        val h = source.height
        if (w <= 0 || h <= 0) return Prepared(source, 1f, owned = false)

        var scale = 1f
        if (upscale) {
            val longSide = max(w, h).toFloat()
            val shortSide = min(w, h).toFloat()
            // Aim for a comfortable working resolution without exploding OCR time.
            val target = if (shortSide < 420f) TARGET_LONG_SIDE_SMALL else TARGET_LONG_SIDE
            scale = (target / longSide).coerceIn(1f, MAX_SCALE)
            // Do not create bitmaps larger than the memory budget.
            val pixels = (w * scale) * (h * scale)
            if (pixels > MAX_PIXELS) scale = kotlin.math.sqrt(MAX_PIXELS / (w.toFloat() * h)).coerceAtLeast(1f)
            if (scale < 1.15f) scale = 1f
        }
        if (scale == 1f && !enhance) return Prepared(source, 1f, owned = false)

        val outW = (w * scale).toInt().coerceAtLeast(1)
        val outH = (h * scale).toInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        if (enhance) {
            paint.colorFilter = ColorMatrixColorFilter(contrastMatrix())
        }
        canvas.drawBitmap(source, null, Rect(0, 0, outW, outH), paint)
        return Prepared(out, scale, owned = true)
    }

    private fun contrastMatrix(): ColorMatrix {
        val gray = ColorMatrix().apply { setSaturation(0f) }
        val c = CONTRAST
        val t = (1f - c) * 128f
        val contrast = ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, t,
                0f, c, 0f, 0f, t,
                0f, 0f, c, 0f, t,
                0f, 0f, 0f, 1f, 0f
            )
        )
        return ColorMatrix().apply {
            postConcat(gray)
            postConcat(contrast)
        }
    }

    /** Small luminance thumbnail used to detect "nothing changed" between frames. */
    fun signature(bitmap: Bitmap): ByteArray {
        val thumb = Bitmap.createScaledBitmap(bitmap, SIG_SIZE, SIG_SIZE, true)
        val pixels = IntArray(SIG_SIZE * SIG_SIZE)
        thumb.getPixels(pixels, 0, SIG_SIZE, 0, 0, SIG_SIZE, SIG_SIZE)
        if (thumb !== bitmap) thumb.recycle()
        return ByteArray(pixels.size) { i ->
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            ((r * 299 + g * 587 + b * 114) / 1000).toByte()
        }
    }

    /** True when two signatures describe (nearly) the same picture. */
    fun similar(a: ByteArray?, b: ByteArray?): Boolean {
        if (a == null || b == null || a.size != b.size) return false
        var total = 0L
        var changed = 0
        for (i in a.indices) {
            val d = abs((a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF))
            total += d
            if (d > 24) changed++
        }
        val mean = total.toFloat() / a.size
        return mean < 3.5f && changed < a.size / 40
    }

    private const val TARGET_LONG_SIDE = 1600f
    private const val TARGET_LONG_SIDE_SMALL = 1400f
    private const val MAX_SCALE = 3f
    private const val MAX_PIXELS = 3_500_000f
    private const val CONTRAST = 1.25f
    private const val SIG_SIZE = 32
}
