package com.offlinepdf.studio.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {
    @Test
    fun formatFileSize_handlesBytesKilobytesMegabytes() {
        assertEquals("0 B", Formatters.formatFileSize(0L))
        assertEquals("512 B", Formatters.formatFileSize(512L))
        assertEquals("1.0 KB", Formatters.formatFileSize(1024L))
        assertEquals("2.5 MB", Formatters.formatFileSize((2.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun formatFileSize_handlesNegativeSafely() {
        assertEquals("0 B", Formatters.formatFileSize(-100L))
    }
}
