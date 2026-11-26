package com.offlinepdf.studio.core.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

object Formatters {
    fun formatFileSize(bytes: Long): String {
        if (bytes < 0L) return "0 B"
        if (bytes < 1024L) return "$bytes B"
        val unit = 1024.0
        val exp = (ln(bytes.toDouble()) / ln(unit)).toInt().coerceAtMost(4)
        val suffix = "KMGT"[exp - 1]
        return String.format(Locale.US, "%.1f %sB", bytes / unit.pow(exp.toDouble()), suffix)
    }

    fun formatTimestamp(epochMillis: Long, locale: Locale = Locale.getDefault()): String {
        if (epochMillis <= 0L) return ""
        val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", locale)
        return formatter.format(Date(epochMillis))
    }
}
