package com.cortextransl.translateonscreen

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import com.cortextransl.translateonscreen.capture.MediaProjectionConsent
import com.cortextransl.translateonscreen.data.preferences.UserPreferences
import com.cortextransl.translateonscreen.service.ScreenCaptureService
import com.cortextransl.translateonscreen.ui.navigation.AppRoot
import com.cortextransl.translateonscreen.ui.permissions.PermissionScreen
import com.cortextransl.translateonscreen.ui.theme.TranslateOnScreenTheme
import com.cortextransl.translateonscreen.util.QuickAccessTiles
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity(), ScreenCaptureService.ServiceListener {

    companion object {
        private const val TAG = "MainActivity"
        const val EXTRA_START_FROM_TILE = "extra_start_from_tile"
    }

    @Inject
    lateinit var userPreferences: UserPreferences

    private var hasOverlayPermission by mutableStateOf(false)
    private var hasNotificationPermission by mutableStateOf(false)
    private var onboardingCompleted by mutableStateOf(false)
    private var isServiceRunning by mutableStateOf(false)
    private var uiReady by mutableStateOf(false)

    private var captureService: ScreenCaptureService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ScreenCaptureService.LocalBinder
            captureService = binder.getService()
            captureService?.serviceListener = this@MainActivity
            isBound = true
            isServiceRunning = captureService?.isCapturing ?: false
            Log.d(TAG, "Bound to ScreenCaptureService")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            captureService?.serviceListener = null
            captureService = null
            isBound = false
            isServiceRunning = false
            Log.d(TAG, "Unbound from ScreenCaptureService")
        }
    }

    private lateinit var screenCaptureLauncher: ActivityResultLauncher<Intent>
    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>
    private var pendingSingleAppCapture = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        screenCaptureLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                Log.d(TAG, "Screen capture consent granted")
                MediaProjectionConsent.store(result.resultCode, result.data!!)
                if (!startScreenCaptureService()) {
                    lifecycleScope.launch {
                        lifecycle.withResumed {
                            startScreenCaptureService()
                        }
                    }
                }
            } else {
                Log.w(TAG, "Screen capture consent denied")
                Toast.makeText(this, R.string.screen_capture_denied, Toast.LENGTH_LONG).show()
            }
        }
        notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            launchScreenCaptureIntent(pendingSingleAppCapture)
        }

        lifecycleScope.launch {
            onboardingCompleted = userPreferences.onboardingCompleted.first()
            uiReady = true
            if (onboardingCompleted) {
                QuickAccessTiles.offerOnce(this@MainActivity, userPreferences)
            }
        }

        setContent {
            val darkPref by userPreferences.darkMode.collectAsStateWithLifecycle(initialValue = null)
            TranslateOnScreenTheme(darkTheme = darkPref ?: isSystemInDarkTheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when {
                        !uiReady -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        !onboardingCompleted -> {
                            PermissionScreen(
                                hasOverlayPermission = hasOverlayPermission,
                                hasNotificationPermission = hasNotificationPermission,
                                onPermissionsGranted = {
                                    lifecycleScope.launch {
                                        userPreferences.setOnboardingCompleted(true)
                                        QuickAccessTiles.offerOnce(
                                            this@MainActivity,
                                            userPreferences
                                        )
                                    }
                                    onboardingCompleted = true
                                }
                            )
                        }
                        else -> {
                            AppRoot(
                                isServiceRunning = isServiceRunning,
                                hasOverlayPermission = hasOverlayPermission,
                                onStartTranslator = { singleApp -> requestScreenCapture(singleApp) },
                                onStopTranslator = { stopScreenCaptureService() },
                                onRequestOverlayPermission = { openOverlaySettings() }
                            )
                        }
                    }
                }
            }
        }
        handleQsTileIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleQsTileIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStates()
    }

    override fun onStart() {
        super.onStart()
        bindToServiceIfRunning()
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            captureService?.serviceListener = null
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun refreshPermissionStates() {
        hasOverlayPermission = Settings.canDrawOverlays(this)
        hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun handleQsTileIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_START_FROM_TILE, false) != true) return
        intent.removeExtra(EXTRA_START_FROM_TILE)
        startActivity(Intent(this, TileLaunchActivity::class.java))
        moveTaskToBack(true)
    }

    fun requestScreenCapture(singleApp: Boolean = false) {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.overlay_permission_required, Toast.LENGTH_LONG).show()
            openOverlaySettings()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingSingleAppCapture = singleApp
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        launchScreenCaptureIntent(singleApp)
    }

    private fun launchScreenCaptureIntent(singleApp: Boolean) {
        val captureIntent = MediaProjectionConsent.createCaptureIntent(this, singleApp)
        try {
            screenCaptureLauncher.launch(captureIntent)
        } catch (error: Exception) {
            Log.e(TAG, "Could not start screen capture prompt", error)
            Toast.makeText(this, R.string.screen_capture_denied, Toast.LENGTH_LONG).show()
        }
    }

    private fun startScreenCaptureService(): Boolean {
        val serviceIntent = ScreenCaptureService.createStartIntent(this)
        return try {
            ContextCompat.startForegroundService(this, serviceIntent)
            if (!isBound) {
                isBound = bindService(
                    Intent(this, ScreenCaptureService::class.java),
                    serviceConnection,
                    0
                )
            }
            Log.d(TAG, "ScreenCaptureService start requested")
            true
        } catch (error: Exception) {
            Log.e(TAG, "Failed to start capture service", error)
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun stopScreenCaptureService() {
        captureService?.stopCapture()
        startService(ScreenCaptureService.createStopIntent(this))
        if (isBound) {
            captureService?.serviceListener = null
            unbindService(serviceConnection)
            isBound = false
        }
        captureService = null
        isServiceRunning = false
        Log.d(TAG, "ScreenCaptureService stop requested")
    }

    private fun bindToServiceIfRunning() {
        if (isBound) return
        isBound = bindService(Intent(this, ScreenCaptureService::class.java), serviceConnection, 0)
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
    }

    override fun onCaptureStarted() {
        runOnUiThread { isServiceRunning = true }
    }

    override fun onCaptureStopped() {
        runOnUiThread { isServiceRunning = false }
    }

    override fun onCaptureError(error: Exception) {
        runOnUiThread {
            isServiceRunning = false
            Toast.makeText(
                this,
                getString(R.string.error_translation_failed) + ": ${error.localizedMessage}",
                Toast.LENGTH_LONG
            ).show()
            Log.e(TAG, "Capture error", error)
        }
    }
}
