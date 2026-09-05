package com.cortextransl.translateonscreen.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.cortextransl.translateonscreen.R
import com.cortextransl.translateonscreen.data.model.OverlayBlock
import com.cortextransl.translateonscreen.ui.theme.TranslateOnScreenTheme
import com.cortextransl.translateonscreen.util.ScreenMetrics
import kotlinx.coroutines.CompletableDeferred
import kotlin.math.hypot

class OverlayManager(
    private val context: Context,
    private val onBubbleTap: () -> Unit,
    private val onBubbleLongPress: () -> Unit,
    private val onBubbleDoubleTap: () -> Unit,
    private val onBubbleDismiss: () -> Unit
) {
    private val windowManager =
        context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val gestureHandler = Handler(Looper.getMainLooper())
    private var bubbleView: FloatingBubbleView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var translationRoot: View? = null
    private var regionView: RegionSelectView? = null
    private var regionDeferred: CompletableDeferred<Rect?>? = null
    private var optionsRoot: FrameLayout? = null
    private var optionsLifecycle: OverlayLifecycleOwner? = null
    private var optionsToken = 0
    private var optionsClosing = false
    private val sheetMotion = PathInterpolator(0.2f, 0f, 0f, 1f)
    private var deleteZone: View? = null
    private var bubbleVisible = false
    var doubleTapEnabled: Boolean = false
    private var appearanceSizeDp: Int = BubbleStyle.DEFAULT_DP
    private var appearanceColor: Int = BubbleStyle.DEFAULT_COLOR
    private var appearanceOpacity: Int = BubbleStyle.DEFAULT_OPACITY

    private val configCallback = object : android.content.ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            hideTranslations()
            hideOptionsMenu()
            clampBubble()
        }

        override fun onLowMemory() = Unit
    }

    fun attach() {
        context.applicationContext.registerComponentCallbacks(configCallback)
    }

    fun destroy() {
        context.applicationContext.unregisterComponentCallbacks(configCallback)
        regionDeferred?.complete(null)
        regionDeferred = null
        hideRegionSelector()
        removeOptionsImmediate()
        hideTranslations()
        hideDeleteZone()
        removeBubble()
    }

    fun isOptionsMenuShowing(): Boolean = optionsRoot != null

    fun showBubble(): Boolean {
        if (bubbleView != null) {
            bubbleView?.visibility = View.VISIBLE
            bubbleVisible = true
            clampBubble()
            if (optionsRoot == null) {
                raiseBubble()
            }
            return true
        }
        return try {
            val size = ScreenMetrics.info(context)
            val bubbleSize = ScreenMetrics.dp(context, appearanceSizeDp.toFloat())
            val edge = ScreenMetrics.dp(context, 16f)
            val params = baseOverlayParams(
                width = bubbleSize,
                height = bubbleSize,
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            ).apply {
                // LEFT is physical left in Arabic (RTL) and English, so the bubble
                // stays on the right edge instead of flying off-screen.
                gravity = Gravity.TOP or Gravity.LEFT
                x = (size.width - bubbleSize - edge).coerceAtLeast(edge)
                y = (size.height / 2).coerceAtLeast(edge)
            }
            val bubble = FloatingBubbleView(
                ContextThemeWrapper(context.applicationContext, R.style.Theme_TranslateOnScreen)
            )
            bubble.applyAppearance(appearanceSizeDp, appearanceColor, appearanceOpacity)
            attachBubbleTouch(bubble, params)
            windowManager.addView(bubble, params)
            bubbleView = bubble
            bubbleParams = params
            bubbleVisible = true
            true
        } catch (error: Exception) {
            android.util.Log.e("OverlayManager", "Failed to show bubble", error)
            bubbleView = null
            bubbleParams = null
            false
        }
    }

    fun hideBubble() {
        bubbleView?.visibility = View.GONE
        bubbleVisible = false
    }

    fun removeBubble() {
        bubbleView?.let { view ->
            try {
                windowManager.removeViewImmediate(view)
            } catch (_: Exception) {
            }
        }
        bubbleView = null
        bubbleParams = null
        bubbleVisible = false
    }

    fun raiseBubble() {
        val view = bubbleView ?: return
        val params = bubbleParams ?: return
        if (view.visibility != View.VISIBLE) return
        try {
            windowManager.removeViewImmediate(view)
            windowManager.addView(view, params)
        } catch (_: Exception) {
        }
    }

    fun setBubbleLoading(loading: Boolean) {
        bubbleView?.setLoading(loading)
    }

    fun applyAppearance(sizeDp: Int, color: Int, opacity: Int = appearanceOpacity) {
        appearanceSizeDp = sizeDp.coerceIn(BubbleStyle.MIN_DP, BubbleStyle.MAX_DP)
        appearanceColor = color
        appearanceOpacity = opacity.coerceIn(BubbleStyle.MIN_OPACITY, BubbleStyle.MAX_OPACITY)
        val view = bubbleView ?: return
        val params = bubbleParams ?: return
        val px = ScreenMetrics.dp(context, appearanceSizeDp.toFloat())
        val old = params.width
        view.applyAppearance(appearanceSizeDp, appearanceColor, appearanceOpacity)
        params.width = px
        params.height = px
        params.x += (old - px) / 2
        clampBubbleParams(params)
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Exception) {
        }
        if (old > 0 && kotlin.math.abs(old - px) >= ScreenMetrics.dp(context, 6f)) {
            val factor = old.toFloat() / px.toFloat()
            syncBubbleScale(view, factor, 1f, 170)
        }
    }

    fun showOptionsMenu(
        state: BubbleMenuUiState,
        onSelectMode: (String) -> Unit,
        onDoubleTapAction: (String) -> Unit,
        onSwapLanguages: () -> Unit,
        onOpenApp: () -> Unit
    ) {
        optionsToken += 1
        if (optionsRoot != null) {
            optionsRoot?.animate()?.cancel()
            removeOptionsImmediate()
        }
        hideTranslations()
        val lifecycle = OverlayLifecycleOwner()
        val themedContext = ContextThemeWrapper(
            context.applicationContext,
            R.style.Theme_TranslateOnScreen
        )
        try {
            lifecycle.onCreate()
            val metrics = ScreenMetrics.info(context)
            val composeView = ComposeView(themedContext).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            }
            val root = FrameLayout(themedContext)
            lifecycle.attachTo(root)
            lifecycle.attachTo(composeView)
            root.addView(
                composeView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            val params = baseOverlayParams(
                width = metrics.width,
                height = metrics.height,
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            ).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                x = 0
                y = 0
            }
            val bottomInset = ScreenMetrics.navigationBarHeight(context)
            composeView.setContent {
                TranslateOnScreenTheme {
                    BubbleOptionsSheet(
                        state = state,
                        extraBottomPx = bottomInset,
                        onSelectMode = onSelectMode,
                        onDoubleTapAction = onDoubleTapAction,
                        onSwapLanguages = onSwapLanguages,
                        onOpenApp = onOpenApp,
                        onDismiss = { hideOptionsMenu() }
                    )
                }
            }
            root.alpha = 0f
            root.translationY = ScreenMetrics.dp(context, 18f).toFloat()
            windowManager.addView(root, params)
            optionsRoot = root
            optionsLifecycle = lifecycle
            optionsClosing = false
            root.animate().cancel()
            root.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(200)
                .setInterpolator(sheetMotion)
                .start()
        } catch (_: Exception) {
            lifecycle.onDestroy()
            optionsRoot = null
            optionsLifecycle = null
            optionsClosing = false
        }
    }

    fun hideOptionsMenu() {
        val view = optionsRoot ?: return
        if (optionsClosing) return
        optionsClosing = true
        val token = optionsToken
        view.animate().cancel()
        view.animate()
            .alpha(0f)
            .translationY(ScreenMetrics.dp(context, 16f).toFloat())
            .setDuration(160)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                if (token != optionsToken) return@withEndAction
                removeOptionsImmediate()
            }
            .start()
    }

    private fun removeOptionsImmediate() {
        optionsRoot?.animate()?.cancel()
        optionsRoot?.let { view ->
            try {
                windowManager.removeViewImmediate(view)
            } catch (_: Exception) {
            }
        }
        optionsLifecycle?.onDestroy()
        optionsRoot = null
        optionsLifecycle = null
        optionsClosing = false
    }

    fun showTranslations(blocks: List<OverlayBlock>, subtitle: String) {
        hideTranslations()
        val metrics = ScreenMetrics.info(context)
        val root = FrameLayout(context)
        val canvas = TranslationCanvasView(context)
        try {
            canvas.setBlocks(blocks)
        } catch (error: Exception) {
            android.util.Log.e("OverlayManager", "Failed to layout translations", error)
        }
        root.addView(
            canvas,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(buildToolbar(subtitle) { hideTranslations() })
        root.setOnClickListener { hideTranslations() }

        val params = baseOverlayParams(
            width = metrics.width,
            height = metrics.height,
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = 0
            y = 0
        }
        try {
            windowManager.addView(root, params)
            translationRoot = root
            raiseBubble()
        } catch (_: Exception) {
            translationRoot = null
        }
    }

    fun hideTranslations() {
        translationRoot?.let { view ->
            try {
                windowManager.removeViewImmediate(view)
            } catch (_: Exception) {
            }
        }
        translationRoot = null
    }

    suspend fun selectRegion(): Rect? {
        hideRegionSelector()
        val deferred = CompletableDeferred<Rect?>()
        regionDeferred = deferred
        val metrics = ScreenMetrics.info(context)
        val view = RegionSelectView(context).apply {
            onConfirm = { rect ->
                if (deferred.isActive) deferred.complete(rect)
                hideRegionSelector()
            }
            onCancel = {
                if (deferred.isActive) deferred.complete(null)
                hideRegionSelector()
            }
        }
        val params = baseOverlayParams(
            width = metrics.width,
            height = metrics.height,
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = 0
            y = 0
        }
        try {
            windowManager.addView(view, params)
            regionView = view
        } catch (_: Exception) {
            regionView = null
            deferred.complete(null)
        }
        return try {
            deferred.await()
        } finally {
            if (regionDeferred === deferred) {
                regionDeferred = null
            }
        }
    }

    fun hideRegionSelector() {
        regionView?.let { view ->
            try {
                windowManager.removeViewImmediate(view)
            } catch (_: Exception) {
            }
        }
        regionView = null
    }

    fun cancelPendingRegion() {
        regionDeferred?.complete(null)
        regionDeferred = null
        hideRegionSelector()
    }

    private fun buildToolbar(subtitle: String, onClose: () -> Unit): View {
        val padH = ScreenMetrics.dp(context, 14f)
        val padV = ScreenMetrics.dp(context, 8f)
        val toolbar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_overlay_toolbar)
            setPadding(padH, padV, padH / 2, padV)
            elevation = ScreenMetrics.dp(context, 8f).toFloat()
            setOnClickListener { }
        }
        val label = TextView(context).apply {
            text = subtitle
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            maxLines = 1
        }
        val close = ImageButton(context).apply {
            setImageResource(R.drawable.ic_close_white)
            background = null
            contentDescription = context.getString(R.string.close_translation)
            setOnClickListener { onClose() }
        }
        toolbar.addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        toolbar.addView(close, LinearLayout.LayoutParams(ScreenMetrics.dp(context, 36f), ScreenMetrics.dp(context, 36f)))

        return FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = ScreenMetrics.dp(context, 48f)
        }.let { params ->
            toolbar.layoutParams = params
            toolbar
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachBubbleTouch(bubble: FloatingBubbleView, params: WindowManager.LayoutParams) {
        val slop = ScreenMetrics.dp(context, 12f)
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        var longPressFired = false
        var pendingSingleTap: Runnable? = null
        val longPressRunnable = Runnable {
            if (!dragging) {
                longPressFired = true
                bubble.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                syncBubbleScale(bubble, bubble.scaleX, 1f, 140)
                onBubbleLongPress()
            }
        }

        bubble.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    longPressFired = false
                    gestureHandler.removeCallbacks(longPressRunnable)
                    gestureHandler.postDelayed(longPressRunnable, LONG_PRESS_MS)
                    syncBubbleScale(view, view.scaleX, 0.94f, 80)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && hypot(dx.toDouble(), dy.toDouble()) > slop) {
                        dragging = true
                        gestureHandler.removeCallbacks(longPressRunnable)
                        syncBubbleScale(view, view.scaleX, 1.05f, 90)
                        showDeleteZone()
                    }
                    if (dragging) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        clampBubbleParams(params)
                        highlightDeleteZone(event.rawX, event.rawY)
                        try {
                            windowManager.updateViewLayout(view, params)
                        } catch (_: Exception) {
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    gestureHandler.removeCallbacks(longPressRunnable)
                    if (!longPressFired) {
                        syncBubbleScale(view, view.scaleX, 1f, 140)
                    }
                    if (dragging) {
                        val drop = isOverDeleteZone(event.rawX, event.rawY)
                        hideDeleteZone()
                        if (drop) {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onBubbleDismiss()
                        } else {
                            clampBubble()
                        }
                    } else if (!longPressFired) {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        if (doubleTapEnabled) {
                            pendingSingleTap?.let { gestureHandler.removeCallbacks(it) }
                            val fireTap = pendingSingleTap == null
                            if (!fireTap) {
                                pendingSingleTap = null
                                onBubbleDoubleTap()
                            } else {
                                val tap = Runnable {
                                    pendingSingleTap = null
                                    onBubbleTap()
                                }
                                pendingSingleTap = tap
                                gestureHandler.postDelayed(tap, DOUBLE_TAP_MS)
                            }
                        } else {
                            onBubbleTap()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    gestureHandler.removeCallbacks(longPressRunnable)
                    pendingSingleTap?.let { gestureHandler.removeCallbacks(it) }
                    pendingSingleTap = null
                    syncBubbleScale(view, view.scaleX, 1f, 140)
                    hideDeleteZone()
                    true
                }
                else -> false
            }
        }
    }

    private fun showDeleteZone() {
        if (deleteZone != null) return
        val buttonSize = ScreenMetrics.dp(context, 48f)
        val pad = ScreenMetrics.dp(context, 13f)
        val zone = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            setBackgroundResource(R.drawable.bg_delete_dock)
            setPadding(0, ScreenMetrics.dp(context, 8f), 0, ScreenMetrics.dp(context, 22f))
            val button = ImageView(context).apply {
                setImageResource(R.drawable.ic_close_white)
                setBackgroundResource(R.drawable.bg_delete_zone)
                setPadding(pad, pad, pad, pad)
                contentDescription = context.getString(R.string.drop_to_remove)
            }
            addView(
                button,
                LinearLayout.LayoutParams(buttonSize, buttonSize).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
            )
            val label = TextView(context).apply {
                text = context.getString(R.string.delete_zone_label)
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, ScreenMetrics.dp(context, 6f), 0, 0)
            }
            addView(
                label,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
            )
        }
        val params = baseOverlayParams(
            width = WindowManager.LayoutParams.MATCH_PARENT,
            height = ScreenMetrics.dp(context, 104f),
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            x = 0
            y = 0
        }
        try {
            windowManager.addView(zone, params)
            deleteZone = zone
        } catch (_: Exception) {
            deleteZone = null
        }
    }

    private fun highlightDeleteZone(rawX: Float, rawY: Float) {
        val zone = deleteZone ?: return
        val hot = isOverDeleteZone(rawX, rawY)
        val button = (zone as? FrameLayout)?.getChildAt(0) ?: zone
        button.scaleX = if (hot) 1.18f else 1f
        button.scaleY = button.scaleX
        zone.alpha = if (hot) 1f else 0.82f
    }

    private fun isOverDeleteZone(rawX: Float, rawY: Float): Boolean {
        val size = ScreenMetrics.info(context)
        val zoneHeight = ScreenMetrics.dp(context, 112f)
        return rawY >= (size.height - zoneHeight) && rawX >= 0 && rawX <= size.width
    }

    private fun hideDeleteZone() {
        deleteZone?.let { view ->
            try {
                windowManager.removeViewImmediate(view)
            } catch (_: Exception) {
            }
        }
        deleteZone = null
    }

    private fun syncBubbleScale(view: View, from: Float, to: Float, duration: Long) {
        view.animate().cancel()
        view.scaleX = from
        view.scaleY = from
        view.animate()
            .scaleX(to)
            .scaleY(to)
            .setDuration(duration)
            .setInterpolator(sheetMotion)
            .start()
    }

    private fun clampBubble() {
        val params = bubbleParams ?: return
        clampBubbleParams(params)
        bubbleView?.let { view ->
            try {
                windowManager.updateViewLayout(view, params)
            } catch (_: Exception) {
            }
        }
    }

    private fun clampBubbleParams(params: WindowManager.LayoutParams) {
        val size = ScreenMetrics.info(context)
        val bubbleSize = ScreenMetrics.dp(context, appearanceSizeDp.toFloat())
        params.x = params.x.coerceIn(0, (size.width - bubbleSize).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (size.height - bubbleSize).coerceAtLeast(0))
    }

    private fun baseOverlayParams(width: Int, height: Int, flags: Int): WindowManager.LayoutParams {
        val params = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            params.setFitInsetsTypes(0)
        }
        return params
    }

    companion object {
        private const val LONG_PRESS_MS = 450L
        private const val DOUBLE_TAP_MS = 280L
    }
}
