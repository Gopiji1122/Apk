package com.offlineai.assistant.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

class AppPrefs(private val context: Context) {
    private val prefs = context.getSharedPreferences("offline_ai_prefs", Context.MODE_PRIVATE)

    var savedModelPath: String?
        get() = prefs.getString("model_path", null)
        set(value) = prefs.edit().putString("model_path", value).apply()

    var cpuThreads: Int
        get() = prefs.getInt("cpu_threads", 6)
        set(value) = prefs.edit().putInt("cpu_threads", value.coerceIn(2, 8)).apply()

    var contextSize: Int
        get() = prefs.getInt("context_size", 2048)
        set(value) = prefs.edit().putInt("context_size", value.coerceIn(1024, 4096)).apply()

    var fastNoThinkMode: Boolean
        get() = prefs.getBoolean("fast_no_think", true)
        set(value) = prefs.edit().putBoolean("fast_no_think", value).apply()

    fun modelsDir(): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun listImportedModels(): List<File> {
        return modelsDir()
            .listFiles { f -> f.isFile && f.name.endsWith(".gguf", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun getFileName(uri: Uri): String {
        var name = "model.gguf"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(idx) ?: name
            }
        }
        return name
    }

    fun importModelFromUri(uri: Uri, onProgress: (Int) -> Unit): File {
        val fileName = getFileName(uri)
        val safeName = if (fileName.endsWith(".gguf", ignoreCase = true)) fileName else "$fileName.gguf"
        val destFile = File(modelsDir(), safeName)

        var totalBytes = -1L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIdx >= 0 && cursor.moveToFirst()) {
                totalBytes = cursor.getLong(sizeIdx)
            }
        }

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                val buffer = ByteArray(1024 * 512) // 512 KB chunks for fast copy
                var copied = 0L
                var lastPct = -1
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    copied += read
                    if (totalBytes > 0) {
                        val pct = ((copied * 100) / totalBytes).toInt().coerceIn(0, 100)
                        if (pct != lastPct) {
                            lastPct = pct
                            onProgress(pct)
                        }
                    }
                }
            }
        } ?: throw IllegalStateException("Unable to read selected file.")

        onProgress(100)
        return destFile
    }
}
