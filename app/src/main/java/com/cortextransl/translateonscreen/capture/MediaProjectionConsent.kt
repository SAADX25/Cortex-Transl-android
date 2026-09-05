package com.cortextransl.translateonscreen.capture

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build

/**
 * Holds the MediaProjection consent Intent without parceling it into the
 * service start Intent. Nesting that Binder token as an extra is dropped on
 * several Android 14+ devices and then getMediaProjection() fails.
 */
object MediaProjectionConsent {
    @Volatile
    private var resultCode: Int = 0

    @Volatile
    private var resultData: Intent? = null

    @Synchronized
    fun store(code: Int, data: Intent) {
        resultCode = code
        resultData = data
    }

    @Synchronized
    fun consume(): Pair<Int, Intent>? {
        val data = resultData ?: return null
        val code = resultCode
        resultData = null
        resultCode = 0
        return code to data
    }

    fun createCaptureIntent(context: Context, singleApp: Boolean = false): Intent {
        val projectionManager =
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val config = if (singleApp) {
                    MediaProjectionConfig.createConfigForUserChoice()
                } else {
                    MediaProjectionConfig.createConfigForDefaultDisplay()
                }
                projectionManager.createScreenCaptureIntent(config)
            } else {
                projectionManager.createScreenCaptureIntent()
            }
        } catch (_: Exception) {
            projectionManager.createScreenCaptureIntent()
        }
    }
}
