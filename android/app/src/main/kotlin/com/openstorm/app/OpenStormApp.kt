package com.openstorm.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.maplibre.android.MapLibre
import timber.log.Timber

@HiltAndroidApp
class OpenStormApp : Application() {

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Initialize MapLibre GL Native
        MapLibre.getInstance(this)
    }
}
