package com.cortextransl.translateonscreen.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Rect
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.cortextransl.translateonscreen.MainActivity
import com.cortextransl.translateonscreen.R
import com.cortextransl.translateonscreen.capture.MediaProjectionConsent
import com.cortextransl.translateonscreen.capture.ScreenFrameCapturer
import com.cortextransl.translateonscreen.data.model.BubbleActions
import com.cortextransl.translateonscreen.data.model.PipelineResult
import com.cortextransl.translateonscreen.data.model.TranslationModes
import com.cortextransl.translateonscreen.data.preferences.UserPreferences
import com.cortextransl.translateonscreen.data.translation.LanguageCatalog
import com.cortextransl.translateonscreen.data.translation.TranslationEngines
import com.cortextransl.translateonscreen.data.translation.TranslationRepository
import com.cortextransl.translateonscreen.overlay.BubbleMenuUiState
import com.cortextransl.translateonscreen.overlay.OverlayManager
import com.cortextransl.translateonscreen.pipeline.InstantTranslationPipeline
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@AndroidEntryPoint
class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val CHANNEL_ID = "screen_capture_channel"
        private const val NOTIFICATION_ID = 1001
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val ACTION_STOP = "com.cortextransl.translateonscreen.ACTION_STOP"
        const val ACTION_TRANSLATE = "com.cortextransl.translateonscreen.ACTION_TRANSLATE"
        const val ACTION_START = "com.cortextransl.translateonscreen.ACTION_START"
        private const val AUTO_TRANSLATE_MS = 2_500L
        private val runningFlag = AtomicBoolean(false)
        private val startingFlag = AtomicBoolean(false)

        fun isRunning(): Boolean = runningFlag.get()
        fun isBusy(): Boolean = runningFlag.get() || startingFlag.get()

        fun createStartIntent(context: Context): Intent {
            return Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_START
            }
        }

        fun createStopIntent(context: Context): Intent {
            return Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_STOP
            }
        }

        fun createTranslateIntent(context: Context): Intent {
            return Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_TRANSLATE
            }
        }
    }

    @Inject lateinit var pipeline: InstantTranslationPipeline
    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var translationRepository: TranslationRepository
    @Inject lateinit var frameCapturer: ScreenFrameCapturer

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val translateMutex = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var autoJob: Job? = null
    private var projectionReadyAt = 0L

    private lateinit var overlayManager: OverlayManager
    private var mediaProjection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    var serviceListener: ServiceListener? = null
    var isCapturing: Boolean = false
        private set(value) {
            field = value
            runningFlag.set(value)
            if (value) startingFlag.set(false)
        }

    inner class LocalBinder : Binder() {
        fun getService(): ScreenCaptureService = this@ScreenCaptureService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            startForegroundWithNotification()
        } catch (error: Exception) {
            Log.e(TAG, "Failed to enter foreground in onCreate", error)
        }
        overlayManager = OverlayManager(
            context = this,
            onBubbleTap = { performBubbleActionAsync(BubbleActions.TRANSLATE) },
            onBubbleLongPress = { performBubbleActionAsync(BubbleActions.OPEN_MENU) },
            onBubbleDoubleTap = {
                serviceScope.launch {
                    performBubbleAction(userPreferences.doubleTapAction.first())
                }
            },
            onBubbleDismiss = {
                stopCapture()
                stopSelf()
            }
        )
        overlayManager.attach()
        serviceScope.launch {
            overlayManager.applyAppearance(
                userPreferences.bubbleSizeDp.first(),
                userPreferences.bubbleColor.first(),
                userPreferences.bubbleOpacity.first()
            )
            combine(
                userPreferences.bubbleSizeDp,
                userPreferences.bubbleColor,
                userPreferences.bubbleOpacity
            ) { size, color, opacity ->
                Triple(size, color, opacity)
            }.collect { (size, color, opacity) ->
                overlayManager.applyAppearance(size, color, opacity)
            }
        }
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")
        try {
            startForegroundWithNotification()
        } catch (error: Exception) {
            Log.e(TAG, "Failed to enter foreground", error)
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_STOP -> {
                stopCapture()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TRANSLATE -> {
                stopAutoTranslate()
                requestInstantTranslate()
                return START_NOT_STICKY
            }
        }

        if (isCapturing && mediaProjection != null) {
            showFloatingBubble()
            return START_NOT_STICKY
        }

        if (startingFlag.get()) {
            return START_NOT_STICKY
        }

        val consent = MediaProjectionConsent.consume()
        @Suppress("DEPRECATION")
        val resultData = consent?.second ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        val resultCode = consent?.first
            ?: intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED

        // RESULT_OK is -1. That is success, not a missing extra.
        if (resultData == null || resultCode != Activity.RESULT_OK) {
            Log.w(TAG, "Missing screen-capture consent resultCode=$resultCode data=${resultData != null}")
            if (isCapturing || mediaProjection != null) {
                showFloatingBubble()
            } else {
                mainHandler.postDelayed({
                    if (!isCapturing && mediaProjection == null && !startingFlag.get()) {
                        stopSelf()
                    }
                }, 2_000L)
            }
            return START_NOT_STICKY
        }

        startingFlag.set(true)
        showFloatingBubble()
        initializeMediaProjection(resultCode, resultData)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        stopCapture()
        overlayManager.destroy()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )
    }

    private fun buildNotification(): Notification {
        val openPendingIntent = PendingIntent.getService(
            this, 0, createStartIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPendingIntent = PendingIntent.getService(
            this, 1, createStopIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val translatePendingIntent = PendingIntent.getService(
            this, 2, createTranslateIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_stat_c)
            .setColor(0xFF0F766E.toInt())
            .setContentIntent(openPendingIntent)
            .addAction(R.drawable.ic_stat_c, getString(R.string.notification_action_translate), translatePendingIntent)
            .addAction(R.drawable.ic_stop, getString(R.string.notification_action_stop), stopPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun initializeMediaProjection(resultCode: Int, resultData: Intent) {
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        try {
            val projection = projectionManager.getMediaProjection(resultCode, resultData)
            if (projection == null) {
                throw IllegalStateException("MediaProjection was null")
            }
            mediaProjection = projection
            projectionCallback = object : MediaProjection.Callback() {
                override fun onStop() {
                    val startedAgo = SystemClock.elapsedRealtime() - projectionReadyAt
                    if (projectionReadyAt == 0L || startedAgo < 1_500L) {
                        Log.w(TAG, "Ignoring early MediaProjection onStop (${startedAgo}ms)")
                        return
                    }
                    Log.d(TAG, "MediaProjection stopped")
                    mainHandler.post {
                        stopCapture(fromProjectionCallback = true)
                        stopSelf()
                    }
                }
            }
            projection.registerCallback(projectionCallback!!, mainHandler)
            frameCapturer.start(projection)
            isCapturing = true
            projectionReadyAt = SystemClock.elapsedRealtime()
            serviceListener?.onCaptureStarted()
            showFloatingBubble()
            Log.d(TAG, "MediaProjection initialized")
        } catch (error: Exception) {
            startingFlag.set(false)
            Log.e(TAG, "Failed to initialize MediaProjection", error)
            serviceListener?.onCaptureError(error)
            stopSelf()
        }
    }

    private fun showFloatingBubble() {
        if (!::overlayManager.isInitialized) return
        val shown = overlayManager.showBubble()
        if (!shown) {
            Toast.makeText(this, R.string.error_overlay_permission, Toast.LENGTH_LONG).show()
            return
        }
        serviceScope.launch { refreshBubbleGestures() }
    }

    fun requestInstantTranslate() {
        serviceScope.launch {
            if (!translateMutex.tryLock()) return@launch
            try {
                runInstantTranslate()
            } finally {
                translateMutex.unlock()
            }
        }
    }

    private suspend fun runInstantTranslate() {
        val projection = mediaProjection
        if (projection == null || !isCapturing) {
            Toast.makeText(this, R.string.error_capture_inactive, Toast.LENGTH_SHORT).show()
            return
        }

        val mode = TranslationModes.normalize(userPreferences.translationMode.first())
        var region: Rect? = null
        if (TranslationModes.usesRegion(mode)) {
            val saved = userPreferences.getLastRegion()
            val pickEveryTime = mode == TranslationModes.REGION
            if (pickEveryTime || saved == null) {
                overlayManager.hideOptionsMenu()
                overlayManager.hideTranslations()
                overlayManager.hideBubble()
                region = overlayManager.selectRegion()
                if (region == null) {
                    overlayManager.showBubble()
                    if (TranslationModes.isAuto(mode)) stopAutoTranslate()
                    return
                }
                userPreferences.setLastRegion(region)
            } else {
                region = saved
            }
        }

        overlayManager.hideOptionsMenu()
        overlayManager.hideTranslations()
        overlayManager.hideBubble()
        overlayManager.setBubbleLoading(true)
        frameCapturer.noteOverlaysHidden()
        delay(280)

        val source = userPreferences.sourceLanguage.first()
        val target = userPreferences.targetLanguage.first()
        val engine = userPreferences.translationEngine.first()
        if (TranslationEngines.usesOnDevicePacks(engine)) {
            val downloaded = translationRepository.downloadedLanguages.value.ifEmpty {
                translationRepository.refreshDownloaded()
            }
            if (target !in downloaded ||
                (source != UserPreferences.AUTO_LANGUAGE && source !in downloaded)
            ) {
                Toast.makeText(this, R.string.downloading_model, Toast.LENGTH_SHORT).show()
            }
        }

        try {
            when (val result = pipeline.translateScreen(region)) {
                is PipelineResult.Success -> {
                    overlayManager.showTranslations(
                        result.blocks,
                        getString(
                            TranslationEngines.overlaySubtitleRes(engine),
                            LanguageCatalog.shortCode(result.sourceLanguage),
                            LanguageCatalog.shortCode(result.targetLanguage)
                        )
                    )
                }
                PipelineResult.NoTextFound -> {
                    Toast.makeText(this, R.string.no_text_found, Toast.LENGTH_SHORT).show()
                }
                is PipelineResult.Error -> {
                    val detail = result.detail?.let { " · $it" } ?: ""
                    Toast.makeText(
                        this,
                        getString(result.messageRes) + detail,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } finally {
            overlayManager.setBubbleLoading(false)
            overlayManager.showBubble()
        }
    }

    fun getMediaProjection(): MediaProjection? = mediaProjection

    private fun performBubbleActionAsync(action: String) {
        serviceScope.launch { performBubbleAction(action) }
    }

    private suspend fun performBubbleAction(action: String) {
        when (action) {
            BubbleActions.NONE -> Unit
            BubbleActions.OPEN_MENU -> {
                stopAutoTranslate()
                if (overlayManager.isOptionsMenuShowing()) {
                    overlayManager.hideOptionsMenu()
                } else {
                    showBubbleMenu()
                }
            }
            BubbleActions.TRANSLATE -> {
                stopAutoTranslate()
                overlayManager.hideOptionsMenu()
                requestInstantTranslate()
            }
        }
    }

    private suspend fun refreshBubbleGestures() {
        overlayManager.doubleTapEnabled =
            userPreferences.doubleTapAction.first() != BubbleActions.NONE
    }

    private suspend fun showBubbleMenu() {
        val source = userPreferences.sourceLanguage.first()
        val target = userPreferences.targetLanguage.first()
        val engine = userPreferences.translationEngine.first()
        val state = BubbleMenuUiState(
            mode = userPreferences.translationMode.first(),
            doubleTapAction = userPreferences.doubleTapAction.first(),
            sourceName = languageDisplayName(source),
            targetName = languageDisplayName(target),
            engineLabel = getString(TranslationEngines.shortRes(engine))
        )
        overlayManager.showOptionsMenu(
            state = state,
            onSelectMode = { mode -> serviceScope.launch { onMenuModeSelected(mode) } },
            onDoubleTapAction = { action ->
                serviceScope.launch {
                    userPreferences.setDoubleTapAction(action)
                    refreshBubbleGestures()
                    showBubbleMenu()
                }
            },
            onSwapLanguages = { serviceScope.launch { swapLanguagesFromMenu() } },
            onOpenApp = {
                overlayManager.hideOptionsMenu()
                startActivity(
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                )
            }
        )
    }

    private suspend fun onMenuModeSelected(mode: String) {
        userPreferences.setTranslationMode(mode)
        overlayManager.hideOptionsMenu()
        stopAutoTranslate()
        requestInstantTranslate()
        if (TranslationModes.isAuto(mode)) {
            startAutoTranslate()
        }
    }

    private suspend fun swapLanguagesFromMenu() {
        val source = userPreferences.sourceLanguage.first()
        val target = userPreferences.targetLanguage.first()
        if (source == UserPreferences.AUTO_LANGUAGE) {
            Toast.makeText(this, R.string.cannot_swap_auto_language, Toast.LENGTH_SHORT).show()
        } else {
            userPreferences.setSourceLanguage(target)
            userPreferences.setTargetLanguage(source)
        }
        showBubbleMenu()
    }

    private fun languageDisplayName(code: String): String {
        return if (code == UserPreferences.AUTO_LANGUAGE) {
            getString(R.string.language_auto)
        } else {
            LanguageCatalog.displayName(code)
        }
    }

    private fun startAutoTranslate() {
        autoJob?.cancel()
        autoJob = serviceScope.launch {
            delay(AUTO_TRANSLATE_MS)
            while (isActive && isCapturing) {
                if (!translateMutex.tryLock()) {
                    delay(AUTO_TRANSLATE_MS)
                    continue
                }
                try {
                    runInstantTranslate()
                } finally {
                    translateMutex.unlock()
                }
                delay(AUTO_TRANSLATE_MS)
            }
        }
    }

    private fun stopAutoTranslate() {
        autoJob?.cancel()
        autoJob = null
    }

    fun stopCapture(fromProjectionCallback: Boolean = false) {
        if (!isCapturing && mediaProjection == null) return
        Log.d(TAG, "Stopping capture")
        stopAutoTranslate()
        overlayManager.cancelPendingRegion()
        overlayManager.hideOptionsMenu()
        overlayManager.hideTranslations()
        overlayManager.removeBubble()
        frameCapturer.stop()

        projectionCallback?.let { callback ->
            try {
                mediaProjection?.unregisterCallback(callback)
            } catch (error: Exception) {
                Log.w(TAG, "Error unregistering projection callback", error)
            }
        }
        projectionCallback = null

        if (!fromProjectionCallback) {
            try {
                mediaProjection?.stop()
            } catch (error: Exception) {
                Log.w(TAG, "Error stopping media projection", error)
            }
        }
        mediaProjection = null
        isCapturing = false
        startingFlag.set(false)
        projectionReadyAt = 0L
        serviceListener?.onCaptureStopped()
    }

    interface ServiceListener {
        fun onCaptureStarted()
        fun onCaptureStopped()
        fun onCaptureError(error: Exception)
    }
}
