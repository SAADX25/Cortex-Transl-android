package com.cortextransl.translateonscreen.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Log
import android.view.View
import com.cortextransl.translateonscreen.data.model.OverlayBlock
import com.cortextransl.translateonscreen.util.ScreenMetrics
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws translated text cards on top of the original screen text.
 *
 * Layout strategy:
 * 1. Every OCR block gets a "cell": its own rectangle expanded halfway towards
 *    the nearest neighbouring blocks (above/below/left/right). A card never
 *    leaves its cell, so cards can not overlap each other by construction.
 * 2. Inside the cell the translated text is fitted by trying a small set of
 *    consistent text sizes (largest first). A candidate is accepted only when
 *    it fits the cell, respects the max line count and never breaks a word
 *    in the middle (this is what produced vertical "letter ribbons").
 * 3. The card always covers the original text (at least the source rectangle)
 *    so the source and the translation are never visible at the same time.
 * 4. A final pass nudges any residual intersections apart.
 */
class TranslationCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // Neutral translucent grey that sits naturally on both light and dark apps.
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xE63A3B40.toInt()
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x14FFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 0.75f * resources.displayMetrics.density
    }

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = 0f
    }

    private var sourceBlocks: List<OverlayBlock> = emptyList()
    private var layouts: List<DrawnBlock> = emptyList()

    fun setBlocks(blocks: List<OverlayBlock>) {
        sourceBlocks = blocks
        relayout()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (sourceBlocks.isNotEmpty() && w > 0 && h > 0) {
            relayout()
        }
    }

    private fun relayout() {
        val density = resources.displayMetrics.density
        val screen = ScreenMetrics.info(context)
        val screenW = (if (width > 0) width else screen.width).toFloat()
        val screenH = (if (height > 0) height else screen.height).toFloat()
        val margin = SCREEN_MARGIN_DP * density

        val items = sourceBlocks.mapNotNull { block ->
            val text = block.translatedText.trim()
            val rect = RectF(block.rect)
            if (text.isEmpty() || rect.width() < 4f || rect.height() < 4f) return@mapNotNull null
            Item(block, text, rect)
        }

        val cells = items.mapIndexed { index, item ->
            computeCell(index, items, screenW, screenH, margin, density)
        }

        val drawn = ArrayList<DrawnBlock>(items.size)
        items.forEachIndexed { index, item ->
            try {
                layoutItem(item, cells[index], density, screenW, screenH, margin)?.let(drawn::add)
            } catch (error: Exception) {
                Log.w(TAG, "Skipped overlay block", error)
            }
        }
        resolveCollisions(drawn, density, screenH, margin)
        layouts = drawn
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val density = resources.displayMetrics.density
        for (block in layouts) {
            val radius = if (block.isPill) block.rect.height() / 2f else CARD_RADIUS_DP * density
            canvas.drawRoundRect(block.rect, radius, radius, backgroundPaint)
            canvas.drawRoundRect(block.rect, radius, radius, borderPaint)

            canvas.save()
            canvas.clipRect(block.rect)
            canvas.translate(block.textX, block.textY)
            block.layout.draw(canvas)
            canvas.restore()
        }
    }

    // ---------------------------------------------------------------------
    // Cells
    // ---------------------------------------------------------------------

    /**
     * The region a card is allowed to occupy: the source rect grown halfway
     * towards each neighbour, limited by the screen bounds.
     */
    private fun computeCell(
        index: Int,
        items: List<Item>,
        screenW: Float,
        screenH: Float,
        margin: Float,
        density: Float
    ): RectF {
        val me = items[index].rect
        val gap = CELL_GAP_DP * density
        var top = margin
        var bottom = screenH - margin
        var left = margin
        var right = screenW - margin

        for ((j, other) in items.withIndex()) {
            if (j == index) continue
            val o = other.rect
            val horizontalOverlap = o.right > me.left && o.left < me.right
            val verticalOverlap = o.bottom > me.top && o.top < me.bottom

            if (horizontalOverlap) {
                if (o.bottom <= me.top + 1f) {
                    // Neighbour above.
                    top = max(top, midpoint(o.bottom, me.top, gap))
                } else if (o.top >= me.bottom - 1f) {
                    // Neighbour below.
                    bottom = min(bottom, midpoint(me.bottom, o.top, gap, upper = true))
                }
            }
            if (verticalOverlap) {
                if (o.right <= me.left + 1f) {
                    left = max(left, midpoint(o.right, me.left, gap))
                } else if (o.left >= me.right - 1f) {
                    right = min(right, midpoint(me.right, o.left, gap, upper = true))
                }
            }
        }

        // Never make the cell smaller than the source itself.
        top = min(top, me.top)
        bottom = max(bottom, me.bottom)
        left = min(left, me.left)
        right = max(right, me.right)
        return RectF(left, top, right, bottom)
    }

    /** Midpoint between two edges with a small gap so neighbouring cards do not touch. */
    private fun midpoint(a: Float, b: Float, gap: Float, upper: Boolean = false): Float {
        val mid = (a + b) / 2f
        return if (upper) mid - gap / 2f else mid + gap / 2f
    }

    // ---------------------------------------------------------------------
    // Fitting
    // ---------------------------------------------------------------------

    private fun layoutItem(
        item: Item,
        cell: RectF,
        density: Float,
        screenW: Float,
        screenH: Float,
        margin: Float
    ): DrawnBlock? {
        val src = item.rect
        val text = item.text
        val rtl = isRtl(text)
        val srcLines = item.block.lineCount.coerceAtLeast(1)
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val srcLineH = src.height() / srcLines

        // Vertical / very narrow OCR blocks (widget labels, rotated text) are
        // always rendered as a horizontal label centred on the source.
        val narrowSource = src.height() > src.width() * 1.6f && srcLines > 1
        val isShortLabel = narrowSource || (srcLines <= 1 && words.size <= 4) || words.size <= 2

        textPaint.textLocale = if (rtl) Locale.forLanguageTag("ar") else Locale.getDefault()
        textPaint.typeface = Typeface.create(
            if (rtl) "sans-serif-medium" else "sans-serif",
            Typeface.NORMAL
        )

        // Tight padding: the card should hug the original text, not float around it.
        val padH = 4f * density
        val padV = 2f * density

        // Text size follows the source line height (headings stay big, captions
        // stay small), snapped to half-dp steps so labels look uniform.
        val minSize = (if (rtl) 10.5f else 10f) * density
        val maxSize = 42f * density
        val base = snap((srcLineH * 0.80f).coerceIn(minSize, maxSize), density)
        val sizes = generateSequence(base) { it - 0.5f * density }
            .takeWhile { it >= minSize - 0.01f }
            .toList()
            .ifEmpty { listOf(minSize) }

        val cellW = cell.width()
        val cellH = cell.height()
        val maxLayoutW = (cellW - padH * 2f).coerceAtLeast(8f)
        // Width of the original text: the preferred layout width.
        val srcLayoutW = src.width().coerceIn(1f, maxLayoutW)
        // Preferred height: the original block plus a little slack for Arabic shaping.
        val tightH = src.height() + 6f * density
        val maxLines = if (isShortLabel) 2 else (srcLines + 3).coerceAtMost(16)
        val alignCenter = isShortLabel

        fun attempt(maxH: Float): Fit? {
            for (size in sizes) {
                textPaint.textSize = size
                val longestWord = words.maxOfOrNull { textPaint.measureText(it) } ?: 0f
                val natural = textPaint.measureText(text)

                // Candidate widths: the source width first, then the whole cell.
                val candidates = LinkedHashSet<Int>()
                if (isShortLabel) candidates += min(natural + 2f, srcLayoutW).roundToInt()
                candidates += srcLayoutW.roundToInt()
                candidates += maxLayoutW.roundToInt()

                for (w in candidates) {
                    val width = w.coerceAtLeast(1)
                    if (width - 1f < longestWord) continue // would break a word
                    val layout = buildLayout(text, width, size, rtl, maxLines, alignCenter, ellipsize = false)
                    if (layout.lineCount > maxLines) continue
                    if (hasBrokenWord(layout, text)) continue
                    val neededH = layout.height + padV * 2f
                    if (neededH <= maxH + 0.5f && width + padH * 2f <= cellW + 0.5f) {
                        return Fit(layout, width, size)
                    }
                }
            }
            return null
        }

        // Pass 1: stay inside the original block. Pass 2: use the free space
        // around it. Prefer the tight result unless it forces a much smaller font.
        val tight = attempt(min(tightH, cellH))
        val loose = if (tight == null || tight.size < base * 0.8f) attempt(cellH) else null
        val chosen = when {
            tight == null -> loose
            loose == null -> tight
            loose.size >= tight.size + 1.5f * density -> loose
            else -> tight
        }

        val fit = chosen ?: run {
            // Nothing fits perfectly: use the smallest size, the widest width
            // and truncate gracefully instead of drawing garbage.
            val size = minSize
            textPaint.textSize = size
            val longestWord = words.maxOfOrNull { textPaint.measureText(it) } ?: 0f
            val width = max(maxLayoutW, longestWord + 2f)
                .coerceAtMost(screenW - margin * 2f - padH * 2f)
                .roundToInt()
                .coerceAtLeast(1)
            val allowedLines = ((cellH - padV * 2f) / lineHeight(size, rtl)).toInt()
                .coerceIn(1, maxLines)
            Fit(buildLayout(text, width, size, rtl, allowedLines, alignCenter, ellipsize = true), width, size)
        }

        val layout = fit.layout
        val textW = (0 until layout.lineCount).maxOfOrNull { layout.getLineWidth(it) } ?: 0f
        val boxW = max(textW + padH * 2f, src.width() + padH * 2f)
            .coerceAtMost(max(cell.width(), src.width()))
        val boxH = max(layout.height + padV * 2f, src.height() + padV * 2f)
            .coerceAtMost(max(cell.height(), layout.height + padV * 2f))

        // Horizontal placement: labels are centred on the source, paragraphs
        // stay anchored to the source edge.
        val centerX = src.centerX()
        var left = if (isShortLabel) {
            centerX - boxW / 2f
        } else {
            src.left - padH
        }
        left = clampInto(left, boxW, cell.left, cell.right)
        left = clampInto(left, boxW, margin, screenW - margin)

        // Vertical placement: centred on the source, kept inside the cell.
        var top = src.centerY() - boxH / 2f
        top = clampInto(top, boxH, cell.top, cell.bottom)
        top = clampInto(top, boxH, margin, screenH - margin)

        val box = RectF(left, top, left + boxW, top + boxH)
        val isPill = false

        val layoutW = fit.width.toFloat()
        val textX = if (alignCenter) {
            box.left + (box.width() - layoutW) / 2f
        } else if (rtl) {
            box.right - padH - layoutW
        } else {
            box.left + padH
        }
        val textY = box.top + (box.height() - layout.height) / 2f

        return DrawnBlock(
            rect = box,
            layout = layout,
            textX = textX,
            textY = textY,
            isPill = isPill
        )
    }

    private fun clampInto(start: Float, size: Float, lo: Float, hi: Float): Float {
        if (size >= hi - lo) return lo
        return start.coerceIn(lo, hi - size)
    }

    private fun snap(value: Float, density: Float): Float {
        val step = 0.5f * density
        return (value / step).roundToInt() * step
    }

    private fun lineHeight(size: Float, rtl: Boolean): Float {
        textPaint.textSize = size
        val fm = textPaint.fontMetrics
        val base = fm.descent - fm.ascent
        return if (rtl) base * 1.12f + 3f else base * 1.05f + 1f
    }

    /** True when any line ends in the middle of a word (character wrapping). */
    private fun hasBrokenWord(layout: StaticLayout, text: String): Boolean {
        for (line in 0 until layout.lineCount) {
            if (layout.getEllipsisCount(line) > 0) return true
            if (line == layout.lineCount - 1) break
            val end = layout.getLineEnd(line)
            if (end <= 0 || end >= text.length) continue
            val before = text[end - 1]
            val after = text[end]
            val cleanBreak = before.isWhitespace() || after.isWhitespace() ||
                isBreakPunct(before) || isBreakPunct(after)
            if (!cleanBreak) return true
        }
        return false
    }

    private fun isBreakPunct(ch: Char): Boolean =
        ch == '-' || ch == '/' || ch == '|' || ch == '•' || ch == '·' || ch == '،' || ch == ',' ||
            ch == ':' || ch == '؛' || ch == ';'

    @SuppressLint("WrongConstant")
    private fun buildLayout(
        text: String,
        width: Int,
        size: Float,
        rtl: Boolean,
        maxLines: Int,
        alignCenter: Boolean,
        ellipsize: Boolean
    ): StaticLayout {
        textPaint.textSize = size
        textPaint.letterSpacing = 0f
        val alignment = if (alignCenter) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width.coerceAtLeast(1))
            .setAlignment(alignment)
            .setIncludePad(true)
            .setLineSpacing(if (rtl) 3f else 1f, if (rtl) 1.12f else 1.05f)
            .setMaxLines(maxLines.coerceAtLeast(1))
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .setTextDirection(
                if (rtl) TextDirectionHeuristics.FIRSTSTRONG_RTL else TextDirectionHeuristics.FIRSTSTRONG_LTR
            )
        if (ellipsize) {
            builder.setEllipsize(TextUtils.TruncateAt.END)
            builder.setEllipsizedWidth(width.coerceAtLeast(1))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setUseLineSpacingFromFallbacks(true)
        }
        return builder.build()
    }

    // ---------------------------------------------------------------------
    // Collision cleanup
    // ---------------------------------------------------------------------

    /**
     * Cells already prevent overlaps in almost all cases. This pass handles the
     * remainder (cards that had to overflow their cell) by pushing the lower
     * card down when that is cheap, otherwise trimming it to the free space.
     */
    private fun resolveCollisions(blocks: MutableList<DrawnBlock>, density: Float, screenH: Float, margin: Float) {
        val gap = CELL_GAP_DP * density
        blocks.sortWith(compareBy({ it.rect.top }, { it.rect.left }))
        for (i in blocks.indices) {
            val current = blocks[i]
            for (j in 0 until i) {
                val other = blocks[j]
                if (!RectF.intersects(other.rect, current.rect)) continue
                val shift = other.rect.bottom + gap - current.rect.top
                val canShift = shift > 0 && shift <= current.rect.height() * 0.6f &&
                    current.rect.bottom + shift <= screenH - margin
                if (canShift) {
                    current.rect.offset(0f, shift)
                    current.textY += shift
                } else {
                    // Trim the overlapping strip from the top of the lower card.
                    val newTop = min(other.rect.bottom + gap, current.rect.bottom - 12f * density)
                    if (newTop > current.rect.top) {
                        val delta = newTop - current.rect.top
                        current.rect.top = newTop
                        current.textY = current.rect.top +
                            (current.rect.height() - current.layout.height) / 2f
                        if (delta > 0) current.isPill = false
                    }
                }
            }
        }
    }

    private fun isRtl(text: String): Boolean {
        return text.any { ch ->
            val dir = Character.getDirectionality(ch)
            dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
                dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
        }
    }

    private class Item(val block: OverlayBlock, val text: String, val rect: RectF)

    private class Fit(val layout: StaticLayout, val width: Int, val size: Float)

    private class DrawnBlock(
        val rect: RectF,
        val layout: StaticLayout,
        val textX: Float,
        var textY: Float,
        var isPill: Boolean
    )

    companion object {
        private const val TAG = "TranslationCanvas"
        private const val SCREEN_MARGIN_DP = 4f
        private const val CELL_GAP_DP = 3f
        private const val CARD_RADIUS_DP = 4f
    }
}
