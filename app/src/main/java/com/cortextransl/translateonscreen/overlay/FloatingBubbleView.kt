package com.cortextransl.translateonscreen.overlay

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Outline
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.core.view.isVisible
import com.cortextransl.translateonscreen.R
import com.cortextransl.translateonscreen.util.ScreenMetrics

class FloatingBubbleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val icon: ImageView
    private val progress: ProgressBar
    private var sizeDp: Int = BubbleStyle.DEFAULT_DP
    private var fillColor: Int = BubbleStyle.DEFAULT_COLOR
    private var opacityPercent: Int = BubbleStyle.DEFAULT_OPACITY

    init {
        elevation = ScreenMetrics.dp(context, 14f).toFloat()
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width.coerceAtLeast(1), view.height.coerceAtLeast(1))
            }
        }
        clipToOutline = true
        isClickable = true
        isFocusable = true
        contentDescription = context.getString(R.string.bubble_content_description)

        icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_bubble_c)
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        addView(icon, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        progress = ProgressBar(context).apply {
            isIndeterminate = true
            isVisible = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        addView(progress, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        applyAppearance(BubbleStyle.DEFAULT_DP, BubbleStyle.DEFAULT_COLOR, BubbleStyle.DEFAULT_OPACITY)
    }

    fun applyAppearance(sizeDp: Int, color: Int, opacity: Int = BubbleStyle.DEFAULT_OPACITY) {
        this.sizeDp = sizeDp.coerceIn(BubbleStyle.MIN_DP, BubbleStyle.MAX_DP)
        this.fillColor = color
        this.opacityPercent = opacity.coerceIn(BubbleStyle.MIN_OPACITY, BubbleStyle.MAX_OPACITY)
        val size = ScreenMetrics.dp(context, this.sizeDp.toFloat())
        val params = layoutParams ?: LayoutParams(size, size)
        params.width = size
        params.height = size
        layoutParams = params
        background = BubbleStyle.background(context, fillColor)
        alpha = opacityPercent / 100f
        val glyph = BubbleStyle.glyphColor(fillColor)
        icon.imageTintList = ColorStateList.valueOf(glyph)
        val pad = (size * 0.18f).toInt()
        icon.setPadding(pad, pad, pad, pad)
        progress.indeterminateTintList = ColorStateList.valueOf(glyph)
        val spinner = (size * 0.42f).toInt().coerceAtLeast(ScreenMetrics.dp(context, 18f))
        progress.layoutParams = LayoutParams(spinner, spinner, Gravity.CENTER)
        elevation = ScreenMetrics.dp(context, 14f).toFloat()
        invalidateOutline()
    }

    fun setLoading(loading: Boolean) {
        progress.isVisible = loading
        icon.alpha = if (loading) 0.18f else 1f
        isEnabled = !loading
        contentDescription = context.getString(
            if (loading) R.string.bubble_translating else R.string.bubble_content_description
        )
    }

    companion object {
        const val BUBBLE_DP = BubbleStyle.DEFAULT_DP.toFloat()
    }
}
