package com.cortextransl.translateonscreen.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import com.cortextransl.translateonscreen.util.ScreenMetrics
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

data class CaptureFrame(
    val bitmap: Bitmap,
    val screenWidth: Int,
    val screenHeight: Int,
    val scaleX: Float,
    val scaleY: Float
)

@Singleton
class ScreenFrameCapturer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val lock = Any()
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var thread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private var captureWidth: Int = 0
    private var captureHeight: Int = 0
    private var densityDpi: Int = 0
    @Volatile
    private var running = false
    private val pendingGrab = AtomicReference<CompletableDeferred<Bitmap>?>(null)
    private val latest = AtomicReference<CachedFrame?>(null)
    private val overlaysHiddenAt = AtomicLong(0)

    val isRunning: Boolean
        get() = synchronized(lock) { running && virtualDisplay != null }

    fun start(projection: MediaProjection) {
        stop()
        val metrics = ScreenMetrics.info(context)
        captureWidth = even(metrics.width.coerceAtMost(MAX_CAPTURE_WIDTH).coerceAtLeast(2))
        captureHeight = even(
            ((metrics.height.toFloat() / metrics.width.coerceAtLeast(1)) * captureWidth)
                .toInt()
                .coerceAtLeast(2)
        )
        screenWidth = metrics.width
        screenHeight = metrics.height
        densityDpi = metrics.densityDpi

        val reader = ImageReader.newInstance(
            captureWidth,
            captureHeight,
            PixelFormat.RGBA_8888,
            4
        )
        val handlerThread = HandlerThread("cortex-frame-capture").apply { start() }
        val handler = Handler(handlerThread.looper)

        reader.setOnImageAvailableListener({ imageSource ->
            val image = imageSource.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                // The display can push up to 60 frames/s. Converting each one to a
                // 10 MB bitmap burns CPU and battery while the user is just browsing.
                // Only decode when a grab is waiting or our cached frame is stale.
                val now = SystemClock.elapsedRealtime()
                val cachedAt = latest.get()?.capturedAt ?: 0L
                if (pendingGrab.get() == null && now - cachedAt < IDLE_FRAME_INTERVAL_MS) {
                    return@setOnImageAvailableListener
                }
                ingest(image.toBitmap())
            } catch (error: Exception) {
                pendingGrab.getAndSet(null)?.completeExceptionally(error)
            } finally {
                image.close()
            }
        }, handler)

        try {
            val display = try {
                projection.createVirtualDisplay(
                    "cortex-capture",
                    captureWidth,
                    captureHeight,
                    densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader.surface,
                    null,
                    handler
                )
            } catch (error: Exception) {
                Log.w(TAG, "AUTO_MIRROR virtual display failed, retrying", error)
                projection.createVirtualDisplay(
                    "cortex-capture",
                    captureWidth,
                    captureHeight,
                    densityDpi,
                    0,
                    reader.surface,
                    null,
                    handler
                )
            }
            synchronized(lock) {
                imageReader = reader
                virtualDisplay = display
                thread = handlerThread
                captureHandler = handler
                running = true
            }
            Log.d(TAG, "Virtual display started ${captureWidth}x$captureHeight")
        } catch (error: Exception) {
            reader.setOnImageAvailableListener(null, null)
            reader.close()
            handlerThread.quitSafely()
            throw error
        }
    }

    fun noteOverlaysHidden() {
        overlaysHiddenAt.set(SystemClock.elapsedRealtime())
    }

    fun stop() {
        val display: VirtualDisplay?
        val reader: ImageReader?
        val handlerThread: HandlerThread?
        synchronized(lock) {
            running = false
            pendingGrab.getAndSet(null)?.cancel()
            latest.getAndSet(null)?.bitmap?.let { cached ->
                if (!cached.isRecycled) {
                    try {
                        cached.recycle()
                    } catch (_: Exception) {
                    }
                }
            }
            display = virtualDisplay
            virtualDisplay = null
            reader = imageReader
            imageReader = null
            captureHandler = null
            handlerThread = thread
            thread = null
        }
        try {
            display?.release()
        } catch (error: Exception) {
            Log.w(TAG, "Failed to release virtual display", error)
        }
        try {
            reader?.setOnImageAvailableListener(null, null)
            reader?.close()
        } catch (error: Exception) {
            Log.w(TAG, "Failed to close image reader", error)
        }
        handlerThread?.quitSafely()
    }

    suspend fun grab(): CaptureFrame {
        check(isRunning) { "Screen capture is not running" }
        val deferred = CompletableDeferred<Bitmap>()
        pendingGrab.set(deferred)
        requestFreshFrame()
        val bitmap = try {
            withTimeoutOrNull(FRESH_FRAME_MS) { deferred.await() }
                ?: copyLatestIfFresh()
                ?: withTimeoutOrNull(CAPTURE_TIMEOUT_MS) { deferred.await() }
                ?: error("Timed out waiting for a screen frame")
        } finally {
            pendingGrab.compareAndSet(deferred, null)
        }
        if (bitmap.width <= 0 || bitmap.height <= 0 || bitmap.isRecycled) {
            error("Invalid capture frame")
        }
        return CaptureFrame(
            bitmap = bitmap,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            scaleX = screenWidth.toFloat() / bitmap.width,
            scaleY = screenHeight.toFloat() / bitmap.height
        )
    }

    private fun requestFreshFrame() {
        val handler: Handler
        synchronized(lock) {
            handler = captureHandler ?: return
        }
        handler.post { tryIngestFromReader() }
        handler.postDelayed({
            if (pendingGrab.get() == null) return@postDelayed
            tryIngestFromReader()
        }, 180)
        handler.postDelayed({
            if (pendingGrab.get() == null) return@postDelayed
            tryIngestFromReader()
        }, 400)
    }

    private fun tryIngestFromReader(): Boolean {
        return try {
            val image = imageReader?.acquireLatestImage() ?: return false
            try {
                ingest(image.toBitmap())
            } finally {
                image.close()
            }
            true
        } catch (error: Exception) {
            Log.w(TAG, "Immediate frame acquire failed", error)
            false
        }
    }

    private fun ingest(bitmap: Bitmap) {
        if (!running) {
            if (!bitmap.isRecycled) bitmap.recycle()
            return
        }
        val capturedAt = SystemClock.elapsedRealtime()
        rememberFrame(bitmap, capturedAt)
        val grab = pendingGrab.get() ?: return
        if (grab.isCompleted) return
        if (capturedAt < overlaysHiddenAt.get()) return
        val grabCopy = copyBitmap(bitmap)
        if (grabCopy == null) return
        if (pendingGrab.compareAndSet(grab, null) && !grab.isCompleted) {
            grab.complete(grabCopy)
        } else if (!grabCopy.isRecycled) {
            grabCopy.recycle()
        }
    }

    private fun rememberFrame(bitmap: Bitmap, capturedAt: Long) {
        synchronized(lock) {
            val next = CachedFrame(bitmap, capturedAt)
            latest.getAndSet(next)?.let { previous ->
                if (previous.bitmap !== bitmap && !previous.bitmap.isRecycled) {
                    try {
                        previous.bitmap.recycle()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private fun copyLatestIfFresh(): Bitmap? {
        synchronized(lock) {
            val source = latest.get() ?: return null
            if (source.bitmap.isRecycled) return null
            if (source.capturedAt < overlaysHiddenAt.get()) return null
            return copyBitmap(source.bitmap)
        }
    }

    private fun copyBitmap(source: Bitmap): Bitmap? {
        return try {
            if (source.isRecycled) null else source.copy(Bitmap.Config.ARGB_8888, false)
        } catch (error: Exception) {
            Log.w(TAG, "Bitmap copy failed", error)
            null
        }
    }

    private fun Image.toBitmap(): Bitmap {
        val plane = planes.getOrNull(0) ?: error("Image has no planes")
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride.coerceAtLeast(1)
        val rowStride = plane.rowStride.coerceAtLeast(pixelStride)
        val w = width
        val h = height
        if (w <= 0 || h <= 0) error("Invalid image size ${w}x$h")
        val rowPadding = (rowStride - pixelStride * w).coerceAtLeast(0)
        val bitmapWidth = (w + rowPadding / pixelStride).coerceAtLeast(w)
        val bitmap = Bitmap.createBitmap(bitmapWidth, h, Bitmap.Config.ARGB_8888)
        buffer.rewind()
        val expected = bitmap.rowBytes * h
        val remaining = buffer.remaining()
        try {
            if (remaining == expected) {
                bitmap.copyPixelsFromBuffer(buffer)
            } else {
                val padded = ByteBuffer.allocate(expected)
                val toCopy = remaining.coerceAtMost(expected)
                if (toCopy > 0) {
                    val chunk = ByteArray(toCopy)
                    buffer.get(chunk)
                    padded.put(chunk)
                }
                padded.rewind()
                bitmap.copyPixelsFromBuffer(padded)
            }
        } catch (error: RuntimeException) {
            if (!bitmap.isRecycled) bitmap.recycle()
            throw error
        }
        return if (bitmap.width == w) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, w, h).also { bitmap.recycle() }
        }
    }

    private data class CachedFrame(
        val bitmap: Bitmap,
        val capturedAt: Long
    )

    companion object {
        private const val TAG = "ScreenFrameCapturer"
        private const val MAX_CAPTURE_WIDTH = 1080
        private const val FRESH_FRAME_MS = 800L
        private const val CAPTURE_TIMEOUT_MS = 2_000L
        private const val IDLE_FRAME_INTERVAL_MS = 700L

        private fun even(value: Int): Int = (value and 0x7FFFFFFE).coerceAtLeast(2)
    }
}
