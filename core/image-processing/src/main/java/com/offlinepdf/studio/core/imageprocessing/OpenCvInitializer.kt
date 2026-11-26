package com.offlinepdf.studio.core.imageprocessing

import org.opencv.android.OpenCVLoader
import java.util.concurrent.atomic.AtomicBoolean

object OpenCvInitializer {
    private val initialized = AtomicBoolean(false)

    fun ensureInitialized(): Boolean {
        if (initialized.get()) return true
        val ok = runCatching { OpenCVLoader.initLocal() }.getOrDefault(false)
        if (ok) initialized.set(true)
        return ok
    }
}
