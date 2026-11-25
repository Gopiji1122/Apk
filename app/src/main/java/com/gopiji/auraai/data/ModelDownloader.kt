package com.gopiji.auraai.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

data class DownloadProgress(
    val modelId: String,
    val fileName: String,
    val downloadedMb: Double,
    val totalMb: Double,
    val percent: Int
)

class ModelDownloader {
    private val cancelFlag = AtomicBoolean(false)

    fun cancel() {
        cancelFlag.set(true)
    }

    suspend fun downloadModel(
        model: CatalogModel,
        destDir: File,
        onProgress: (DownloadProgress) -> Unit
    ): File = withContext(Dispatchers.IO) {
        cancelFlag.set(false)
        if (!destDir.exists()) destDir.mkdirs()

        val finalFile = File(destDir, model.fileName)
        val partFile = File(destDir, "${model.fileName}.part")
        val existingBytes = if (partFile.exists()) partFile.length() else 0L

        var currentUrl = model.downloadUrl
        var conn: HttpURLConnection? = null
        var redirects = 0

        while (redirects < 6) {
            val c = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 30000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "AuraAI-Android/2.0")
                if (existingBytes > 0) {
                    setRequestProperty("Range", "bytes=$existingBytes-")
                }
            }
            val code = c.responseCode
            if (code in 301..308) {
                val loc = c.getHeaderField("Location")
                c.disconnect()
                if (loc.isNullOrBlank()) throw IllegalStateException("Redirect missing location.")
                currentUrl = if (loc.startsWith("http")) loc else URL(URL(currentUrl), loc).toString()
                redirects++
            } else {
                conn = c
                break
            }
        }

        val activeConn = conn ?: throw IllegalStateException("Too many redirects while downloading.")
        try {
            val code = activeConn.responseCode
            val isResume = (code == HttpURLConnection.HTTP_PARTIAL && existingBytes > 0)
            if (code != HttpURLConnection.HTTP_OK && !isResume) {
                throw IllegalStateException("Server returned HTTP $code")
            }

            val contentLen = activeConn.contentLengthLong
            val totalBytes = if (contentLen > 0) {
                if (isResume) existingBytes + contentLen else contentLen
            } else {
                (model.sizeGb * 1024 * 1024 * 1024).toLong()
            }

            activeConn.inputStream.use { input ->
                FileOutputStream(partFile, isResume).use { output ->
                    val buffer = ByteArray(1024 * 256)
                    var downloaded = if (isResume) existingBytes else 0L
                    var lastEmit = 0L

                    while (true) {
                        if (cancelFlag.get()) {
                            throw IllegalStateException("Download paused/cancelled.")
                        }
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read

                        val now = System.currentTimeMillis()
                        if (now - lastEmit >= 200L) {
                            lastEmit = now
                            val pct = if (totalBytes > 0) ((downloaded * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
                            onProgress(
                                DownloadProgress(
                                    modelId = model.id,
                                    fileName = model.fileName,
                                    downloadedMb = downloaded / (1024.0 * 1024.0),
                                    totalMb = totalBytes / (1024.0 * 1024.0),
                                    percent = pct
                                )
                            )
                        }
                    }
                }
            }

            if (finalFile.exists()) finalFile.delete()
            if (!partFile.renameTo(finalFile)) {
                throw IllegalStateException("Failed to finalize downloaded model file.")
            }

            onProgress(
                DownloadProgress(
                    modelId = model.id,
                    fileName = model.fileName,
                    downloadedMb = finalFile.length() / (1024.0 * 1024.0),
                    totalMb = finalFile.length() / (1024.0 * 1024.0),
                    percent = 100
                )
            )
            finalFile
        } finally {
            activeConn.disconnect()
        }
    }
}
