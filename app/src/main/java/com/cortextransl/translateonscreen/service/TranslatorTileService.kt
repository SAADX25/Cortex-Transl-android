package com.cortextransl.translateonscreen.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.cortextransl.translateonscreen.R
import com.cortextransl.translateonscreen.TileLaunchActivity

class TranslatorTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        try {
            if (ScreenCaptureService.isBusy()) {
                updateTile()
                return
            }
            collapseAndLaunchBubble()
        } catch (error: Exception) {
            Log.e(TAG, "Tile click failed", error)
        }
    }

    private fun collapseAndLaunchBubble() {
        val launch = Intent(this, TileLaunchActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this,
                TILE_REQUEST_CODE,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pending)
        } else {
            startActivityAndCollapseLegacy(launch)
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun startActivityAndCollapseLegacy(intent: Intent) {
        startActivityAndCollapse(intent)
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val running = ScreenCaptureService.isRunning()
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.qs_tile_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_c)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(if (running) R.string.qs_tile_on else R.string.qs_tile_off)
        }
        try {
            tile.updateTile()
        } catch (error: Exception) {
            Log.w(TAG, "updateTile failed", error)
        }
    }

    companion object {
        private const val TAG = "TranslatorTile"
        private const val TILE_REQUEST_CODE = 41
    }
}
