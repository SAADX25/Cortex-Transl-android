package com.cortextransl.translateonscreen.util

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowInsets
import android.view.WindowManager

object ScreenMetrics {
    data class Info(
        val width: Int,
        val height: Int,
        val densityDpi: Int
    )

    fun info(context: Context): Info {
        val app = context.applicationContext
        val fallback = app.resources.displayMetrics
        val windowManager = try {
            app.getSystemService(WindowManager::class.java)
        } catch (_: Exception) {
            null
        }
        val measured = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && windowManager != null) {
                val bounds = windowManager.maximumWindowMetrics.bounds
                bounds.width() to bounds.height()
            } else if (windowManager != null) {
                val metrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getRealMetrics(metrics)
                metrics.widthPixels to metrics.heightPixels
            } else {
                0 to 0
            }
        } catch (_: Exception) {
            0 to 0
        }
        return Info(
            width = (if (measured.first > 0) measured.first else fallback.widthPixels).coerceAtLeast(2),
            height = (if (measured.second > 0) measured.second else fallback.heightPixels).coerceAtLeast(2),
            densityDpi = fallback.densityDpi.coerceAtLeast(120)
        )
    }

    fun dp(context: Context, dp: Float): Int {
        return (dp * context.resources.displayMetrics.density + 0.5f).toInt()
    }

    fun navigationBarHeight(context: Context): Int {
        val fallback = dp(context, 28f)
        return try {
            val windowManager = context.applicationContext.getSystemService(WindowManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && windowManager != null) {
                windowManager.currentWindowMetrics.windowInsets
                    .getInsets(WindowInsets.Type.navigationBars())
                    .bottom
                    .coerceAtLeast(fallback)
            } else {
                val id = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
                if (id != 0) context.resources.getDimensionPixelSize(id).coerceAtLeast(fallback) else fallback
            }
        } catch (_: Exception) {
            fallback
        }
    }
}
