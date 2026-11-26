package com.aurapdf.editor.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.aurapdf.editor.theme.PdfThemeMode
import java.io.File
import java.io.FileOutputStream

data class RecentPdfItem(
    val file: File,
    val name: String,
    val sizeKb: Long,
    val modifiedAt: Long,
    val isStarred: Boolean
)

class PdfStorage(private val context: Context) {
    private val prefs = context.getSharedPreferences("aura_pdf_prefs", Context.MODE_PRIVATE)

    var themeMode: PdfThemeMode
        get() = PdfThemeMode.fromId(prefs.getString("theme_mode", PdfThemeMode.DARK.id))
        set(value) = prefs.edit().putString("theme_mode", value.id).apply()

    private fun getStarredSet(): MutableSet<String> {
        return (prefs.getStringSet("starred_pdfs", emptySet()) ?: emptySet()).toMutableSet()
    }

    fun isStarred(file: File): Boolean = getStarredSet().contains(file.name)

    fun toggleStar(file: File): Boolean {
        val set = getStarredSet()
        val nowStarred = if (set.contains(file.name)) {
            set.remove(file.name); false
        } else {
            set.add(file.name); true
        }
        prefs.edit().putStringSet("starred_pdfs", set).apply()
        return nowStarred
    }

    fun pdfDir(): File {
        val dir = File(context.filesDir, "pdfs")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun scanCacheDir(): File {
        val dir = File(context.cacheDir, "scans")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun createCameraCaptureUri(): Pair<File, Uri> {
        val file = File(scanCacheDir(), "scan_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        return file to uri
    }

    fun undoDir(): File {
        val dir = File(context.cacheDir, "undo_snapshots")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun createUndoSnapshot(source: File): File {
        val snap = File(undoDir(), "snap_${System.nanoTime()}.pdf")
        source.copyTo(snap, overwrite = true)
        return snap
    }

    fun listRecentPdfs(): List<RecentPdfItem> {
        val starred = getStarredSet()
        return pdfDir()
            .listFiles { f -> f.isFile && f.name.endsWith(".pdf", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.map { f ->
                RecentPdfItem(
                    file = f,
                    name = f.name,
                    sizeKb = (f.length() / 1024L).coerceAtLeast(1L),
                    modifiedAt = f.lastModified(),
                    isStarred = starred.contains(f.name)
                )
            }
            ?: emptyList()
    }

    fun newOutputFile(baseName: String): File {
        val safe = baseName.trim().ifEmpty { "Aura_Document" }
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .removeSuffix(".pdf")
            .removeSuffix(".PDF")
        var f = File(pdfDir(), "$safe.pdf")
        var idx = 1
        while (f.exists()) {
            f = File(pdfDir(), "${safe}_$idx.pdf")
            idx++
        }
        return f
    }

    fun renamePdf(file: File, newBaseName: String): File {
        val target = newOutputFile(newBaseName)
        if (file.renameTo(target)) {
            target.setLastModified(System.currentTimeMillis())
            return target
        }
        return file
    }

    fun getDisplayName(uri: Uri): String {
        var name = "Document_${System.currentTimeMillis()}.pdf"
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) {
                name = c.getString(idx) ?: name
            }
        }
        return name
    }

    fun importPdfFromUri(uri: Uri, customBaseName: String? = null): File {
        val name = customBaseName?.let { "$it.pdf" } ?: getDisplayName(uri)
        val safeName = if (name.endsWith(".pdf", ignoreCase = true)) name else "$name.pdf"
        val dest = File(pdfDir(), safeName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Cannot read selected PDF.")
        dest.setLastModified(System.currentTimeMillis())
        return dest
    }

    fun shareFile(file: File, mime: String = "application/pdf") {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share File"))
    }
}
