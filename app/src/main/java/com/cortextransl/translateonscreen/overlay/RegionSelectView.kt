package com.cortextransl.translateonscreen.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class RegionSelectView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onConfirm: ((Rect) -> Unit)? = null
    var onCancel: (() -> Unit)? = null

    private val selection = RectF()
    private val dimPaint = Paint().apply { color = 0x99000000.toInt() }
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00BFA5.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3 * resources.displayMetrics.density
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00BFA5.toInt()
        style = Paint.Style.FILL
    }
    private val cancelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xE61B1C1B.toInt()
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 14 * resources.displayMetrics.density
        isFakeBoldText = true
    }

    private val handleRadius = 10 * resources.displayMetrics.density
    private val buttonRadius = 22 * resources.displayMetrics.density
    private val minSize = 80 * resources.displayMetrics.density

    private var dragMode = DragMode.None
    private var lastX = 0f
    private var lastY = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val padX = w * 0.08f
        val padY = h * 0.22f
        selection.set(padX, padY, w - padX, h - padY)
    }

    override fun onDraw(canvas: Canvas) {
        val saved = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawRoundRect(selection, 16f, 16f, clearPaint)
        canvas.restoreToCount(saved)

        canvas.drawRoundRect(selection, 16f, 16f, strokePaint)
        drawHandle(canvas, selection.left, selection.top)
        drawHandle(canvas, selection.right, selection.top)
        drawHandle(canvas, selection.left, selection.bottom)
        drawHandle(canvas, selection.right, selection.bottom)

        val confirm = confirmCenter()
        val cancel = cancelCenter()
        canvas.drawCircle(cancel.x, cancel.y, buttonRadius, cancelPaint)
        canvas.drawCircle(confirm.x, confirm.y, buttonRadius, buttonPaint)
        canvas.drawText("✕", cancel.x, cancel.y + labelPaint.textSize / 3f, labelPaint)
        canvas.drawText("✓", confirm.x, confirm.y + labelPaint.textSize / 3f, labelPaint)
    }

    private fun drawHandle(canvas: Canvas, x: Float, y: Float) {
        canvas.drawCircle(x, y, handleRadius, handlePaint)
        canvas.drawCircle(x, y, handleRadius, strokePaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                dragMode = hitTest(event.x, event.y)
                if (dragMode == DragMode.Confirm) {
                    onConfirm?.invoke(selectionRect())
                    return true
                }
                if (dragMode == DragMode.Cancel) {
                    onCancel?.invoke()
                    return true
                }
                return dragMode != DragMode.None
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY
                when (dragMode) {
                    DragMode.Move -> moveSelection(dx, dy)
                    DragMode.TopLeft -> resize(left = dx, top = dy)
                    DragMode.TopRight -> resize(right = dx, top = dy)
                    DragMode.BottomLeft -> resize(left = dx, bottom = dy)
                    DragMode.BottomRight -> resize(right = dx, bottom = dy)
                    else -> Unit
                }
                lastX = event.x
                lastY = event.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragMode = DragMode.None
        }
        return super.onTouchEvent(event)
    }

    fun selectionRect(): Rect {
        return Rect(
            selection.left.toInt().coerceAtLeast(0),
            selection.top.toInt().coerceAtLeast(0),
            selection.right.toInt().coerceAtMost(width),
            selection.bottom.toInt().coerceAtMost(height)
        )
    }

    private fun hitTest(x: Float, y: Float): DragMode {
        val slop = handleRadius * 2.2f
        if (hypot((x - confirmCenter().x).toDouble(), (y - confirmCenter().y).toDouble()) <= buttonRadius * 1.2f) {
            return DragMode.Confirm
        }
        if (hypot((x - cancelCenter().x).toDouble(), (y - cancelCenter().y).toDouble()) <= buttonRadius * 1.2f) {
            return DragMode.Cancel
        }
        if (near(x, y, selection.left, selection.top, slop)) return DragMode.TopLeft
        if (near(x, y, selection.right, selection.top, slop)) return DragMode.TopRight
        if (near(x, y, selection.left, selection.bottom, slop)) return DragMode.BottomLeft
        if (near(x, y, selection.right, selection.bottom, slop)) return DragMode.BottomRight
        if (selection.contains(x, y)) return DragMode.Move
        return DragMode.None
    }

    private fun near(x: Float, y: Float, hx: Float, hy: Float, slop: Float): Boolean {
        return abs(x - hx) <= slop && abs(y - hy) <= slop
    }

    private fun moveSelection(dx: Float, dy: Float) {
        val width = selection.width()
        val height = selection.height()
        var left = selection.left + dx
        var top = selection.top + dy
        left = left.coerceIn(0f, this.width - width)
        top = top.coerceIn(0f, this.height - height)
        selection.set(left, top, left + width, top + height)
    }

    private fun resize(left: Float = 0f, top: Float = 0f, right: Float = 0f, bottom: Float = 0f) {
        selection.left = min(selection.left + left, selection.right - minSize).coerceAtLeast(0f)
        selection.top = min(selection.top + top, selection.bottom - minSize).coerceAtLeast(0f)
        selection.right = max(selection.right + right, selection.left + minSize).coerceAtMost(width.toFloat())
        selection.bottom = max(selection.bottom + bottom, selection.top + minSize).coerceAtMost(height.toFloat())
    }

    private fun confirmCenter() = Point(
        selection.centerX() + 36 * resources.displayMetrics.density,
        min(selection.bottom + 40 * resources.displayMetrics.density, height - buttonRadius * 1.4f)
    )

    private fun cancelCenter() = Point(
        selection.centerX() - 36 * resources.displayMetrics.density,
        min(selection.bottom + 40 * resources.displayMetrics.density, height - buttonRadius * 1.4f)
    )

    private data class Point(val x: Float, val y: Float)

    private enum class DragMode {
        None, Move, TopLeft, TopRight, BottomLeft, BottomRight, Confirm, Cancel
    }
}
