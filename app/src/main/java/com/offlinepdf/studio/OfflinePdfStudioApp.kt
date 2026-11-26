package com.offlinepdf.studio

import android.app.Application
import com.offlinepdf.studio.core.imageprocessing.OpenCvInitializer
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class OfflinePdfStudioApp : Application() {
    override fun onCreate() {
        super.onCreate()
        OpenCvInitializer.ensureInitialized()
    }
}
