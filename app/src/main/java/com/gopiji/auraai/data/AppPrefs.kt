package com.gopiji.auraai.data

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

enum class AppThemeMode(val id: String, val label: String) {
    AMOLED("amoled", "AMOLED Black"),
    DARK("dark", "Dark"),
    LIGHT("light", "Light");

    companion object {
        fun fromId(id: String?): AppThemeMode = entries.find { it.id == id } ?: AMOLED
    }
}

data class DeviceHardwareInfo(
    val deviceName: String,
    val androidVersion: String,
    val cpuCores: Int,
    val totalRamGb: Double,
    val availRamGb: Double
)

class AppPrefs(private val context: Context) {
    private val prefs = context.getSharedPreferences("aura_ai_prefs", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_SYSTEM_PROMPT =
            "You are Aura AI, a concise, accurate offline personal assistant. Answer clearly and directly, remember earlier messages in this chat, and prioritize any saved user notes provided."
    }

    fun getHardwareInfo(): DeviceHardwareInfo {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        val availGb = memInfo.availMem / (1024.0 * 1024.0 * 1024.0)
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        val model = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        return DeviceHardwareInfo(
            deviceName = model,
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            cpuCores = cores,
            totalRamGb = totalGb,
            availRamGb = availGb
        )
    }

    private fun defaultThreads(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        return (cores - 2).coerceIn(2, 6)
    }

    private fun defaultContextSize(): Int {
        val totalRam = getHardwareInfo().totalRamGb
        return when {
            totalRam >= 10.0 -> 4096
            totalRam >= 6.0 -> 2048
            else -> 1024
        }
    }

    var savedModelPath: String?
        get() = prefs.getString("model_path", null)
        set(value) = prefs.edit().putString("model_path", value).apply()

    var themeMode: AppThemeMode
        get() = AppThemeMode.fromId(prefs.getString("theme_mode", AppThemeMode.AMOLED.id))
        set(value) = prefs.edit().putString("theme_mode", value.id).apply()

    var fontScale: Float
        get() = prefs.getFloat("font_scale", 1.0f)
        set(value) = prefs.edit().putFloat("font_scale", value.coerceIn(0.85f, 1.3f)).apply()

    var cpuThreads: Int
        get() = prefs.getInt("cpu_threads", defaultThreads())
        set(value) {
            val maxCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
            prefs.edit().putInt("cpu_threads", value.coerceIn(1, maxCores)).apply()
        }

    var contextSize: Int
        get() = prefs.getInt("context_size", defaultContextSize())
        set(value) = prefs.edit().putInt("context_size", value.coerceIn(512, 8192)).apply()

    var maxTokens: Int
        get() = prefs.getInt("max_tokens", 512)
        set(value) = prefs.edit().putInt("max_tokens", value.coerceIn(128, 2048)).apply()

    var temperature: Float
        get() = prefs.getFloat("temperature", 0.7f)
        set(value) = prefs.edit().putFloat("temperature", value.coerceIn(0.0f, 1.5f)).apply()

    var topK: Int
        get() = prefs.getInt("top_k", 40)
        set(value) = prefs.edit().putInt("top_k", value.coerceIn(1, 100)).apply()

    var topP: Float
        get() = prefs.getFloat("top_p", 0.90f)
        set(value) = prefs.edit().putFloat("top_p", value.coerceIn(0.1f, 1.0f)).apply()

    var fastNoThinkMode: Boolean
        get() = prefs.getBoolean("fast_no_think", true)
        set(value) = prefs.edit().putBoolean("fast_no_think", value).apply()

    var ragEnabled: Boolean
        get() = prefs.getBoolean("rag_enabled", true)
        set(value) = prefs.edit().putBoolean("rag_enabled", value).apply()

    var ragMaxNotes: Int
        get() = prefs.getInt("rag_max_notes", 3)
        set(value) = prefs.edit().putInt("rag_max_notes", value.coerceIn(1, 6)).apply()

    var systemPrompt: String
        get() = prefs.getString("system_prompt", DEFAULT_SYSTEM_PROMPT) ?: DEFAULT_SYSTEM_PROMPT
        set(value) = prefs.edit().putString("system_prompt", value).apply()

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
                val buffer = ByteArray(1024 * 512)
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
