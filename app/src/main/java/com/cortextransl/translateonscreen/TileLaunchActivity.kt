package com.cortextransl.translateonscreen

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cortextransl.translateonscreen.capture.MediaProjectionConsent
import com.cortextransl.translateonscreen.data.preferences.UserPreferences
import com.cortextransl.translateonscreen.service.ScreenCaptureService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Invisible trampoline used by the Quick Settings tile and status-bar shortcut.
 * Starts the floating bubble only — never opens the main app UI.
 */
@AndroidEntryPoint
class TileLaunchActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferences: UserPreferences

    private lateinit var captureLauncher: ActivityResultLauncher<Intent>
    private var waitingForConsent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            waitingForConsent = false
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                MediaProjectionConsent.store(result.resultCode, result.data!!)
                startBubbleService()
            } else {
                Toast.makeText(this, R.string.screen_capture_denied, Toast.LENGTH_LONG).show()
            }
            finishAndRemoveTask()
        }
        launchBubbleFlow()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!waitingForConsent) {
            launchBubbleFlow()
        }
    }

    private fun launchBubbleFlow() {
        if (ScreenCaptureService.isBusy()) {
            try {
                startService(ScreenCaptureService.createStartIntent(this))
            } catch (error: Exception) {
                Log.w(TAG, "Could not raise bubble", error)
            }
            finishAndRemoveTask()
            return
        }
        lifecycleScope.launch {
            try {
                val ready = userPreferences.onboardingCompleted.first()
                if (!ready) {
                    openMainApp()
                    return@launch
                }
                if (!Settings.canDrawOverlays(this@TileLaunchActivity)) {
                    Toast.makeText(
                        this@TileLaunchActivity,
                        R.string.overlay_permission_required,
                        Toast.LENGTH_LONG
                    ).show()
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:$packageName")
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    finishAndRemoveTask()
                    return@launch
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        this@TileLaunchActivity,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    openMainApp()
                    return@launch
                }
                waitingForConsent = true
                captureLauncher.launch(
                    MediaProjectionConsent.createCaptureIntent(
                        this@TileLaunchActivity,
                        userPreferences.singleAppCapture.first()
                    )
                )
            } catch (error: Exception) {
                Log.e(TAG, "Tile launch failed", error)
                finishAndRemoveTask()
            }
        }
    }

    private fun startBubbleService() {
        try {
            ContextCompat.startForegroundService(
                this,
                ScreenCaptureService.createStartIntent(this)
            )
        } catch (error: Exception) {
            Log.e(TAG, "Failed to start capture service", error)
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_LONG).show()
        }
    }

    private fun openMainApp() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        )
        finish()
    }

    companion object {
        private const val TAG = "TileLaunchActivity"
    }
}
