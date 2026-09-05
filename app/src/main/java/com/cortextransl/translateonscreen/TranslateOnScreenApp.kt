package com.cortextransl.translateonscreen

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for Translate On Screen.
 *
 * Annotated with @HiltAndroidApp to trigger Hilt's code generation,
 * including a base class for the application that serves as the
 * application-level dependency container.
 */
@HiltAndroidApp
class TranslateOnScreenApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Application-level initialization can go here.
        // ML Kit models will be lazily initialized when first used.
    }
}
