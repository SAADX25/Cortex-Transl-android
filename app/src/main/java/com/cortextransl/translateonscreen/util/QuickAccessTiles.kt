package com.cortextransl.translateonscreen.util

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import com.cortextransl.translateonscreen.R
import com.cortextransl.translateonscreen.data.preferences.UserPreferences
import com.cortextransl.translateonscreen.service.TranslatorTileService
import java.util.function.Consumer
import kotlinx.coroutines.flow.first

object QuickAccessTiles {

    suspend fun offerOnce(context: Context, preferences: UserPreferences) {
        if (preferences.quickAccessOffered.first()) return
        preferences.setQuickAccessOffered(true)
        preferences.setQuickAccessEnabled(true)
        offer(context)
    }

    fun offer(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestAdd(context)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestAdd(context: Context) {
        val statusBar = context.getSystemService(StatusBarManager::class.java) ?: return
        try {
            statusBar.requestAddTileService(
                ComponentName(context, TranslatorTileService::class.java),
                context.getString(R.string.qs_tile_label),
                Icon.createWithResource(context, R.drawable.ic_stat_c),
                context.mainExecutor,
                Consumer { }
            )
        } catch (_: Exception) {
        }
    }
}
