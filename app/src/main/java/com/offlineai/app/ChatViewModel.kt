package com.offlineai.app

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class Msg(val role: String, val text: String)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    val messages = mutableStateListOf<Msg>()
    var status by mutableStateOf("No model loaded. Open the Models tab.")
    var loadedName by mutableStateOf<String?>(null)
    var generating by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    val progress = mutableStateMapOf<String, Float>()
    val downloaded = mutableStateListOf<String>()
    val entries = mutableStateListOf<KnowledgeStore.Entry>()

    private val store = KnowledgeStore(app)
    private val prefs = app.getSharedPreferences("offlineai", 0)
    private val modelsDir = File(app.getExternalFilesDir(null) ?: app.filesDir, "models").apply { mkdirs() }
    private var tmpl = Tmpl.CHATML
    @Volatile private var cancelDl = false

    init {
        refreshFiles()
        refreshEntries()
        val last = prefs.getString("last", null)
        if (last != null && File(modelsDir, last).exists()) {
            loadModel(last, Catalog.guess(last))
        }
    }

    fun refreshFiles() {
        downloaded.clear()
        modelsDir.listFiles()?.filter { it.name.endsWith(".gguf") }?.sortedBy { it.name }
            ?.forEach { downloaded += it.name }
    }

    fun refreshEntries() {
        val list = store.entries()
        entries.clear()
        entries.addAll(list)
    }

    // ---------------- models ----------------

    fun loadModel(file: String, t: Tmpl) {
        if (busy || generating) return
        viewModelScope.launch(Dispatchers.IO) {
            busy = true
            status = "Loading $file ..."
            LlamaBridge.nativeUnload()
            loadedName = null
            val ok = LlamaBridge.nativeLoad(File(modelsDir, file).absolutePath, 4096, 4)
            if (ok) {
                loadedName = file
                tmpl = t
                prefs.edit().putString("last", file).apply()
                status = "Ready: $file"
            } else {
                status = "Could not load $file (not enough RAM, corrupt download, or llama.cpp too old for this model)."
            }
            busy = false
        }
    }

    fun deleteModel(file: String) {
        if (generating || busy) return
        if (loadedName == file) { LlamaBridge.nativeUnload(); loadedName = null; status = "No model loaded." }
        File(modelsDir, file).delete()
        File(modelsDir, "$file.part").delete()
        refreshFiles()
    }

    fun downloadModel(m: ModelInfo) = startDownload(m.file, m.url)

    fun downloadCustom(url: String) {
        val file = url.trim().substringBefore('?').substringAfterLast('/')
        if (!url.startsWith("https://") || !file.endsWith(".gguf")) {
            error = "Enter a direct https link ending in .gguf"
            return
        }
        startDownload(file, url.trim())
    }

    fun cancelDownload() { cancelDl = true }

    private fun startDownload(file: String, url: String) {
        if (progress.isNotEmpty()) { error = "Another download is running."; return }
        cancelDl = false
        progress[file] = 0f
        viewModelScope.launch(Dispatchers.IO) {
            try {
                downloadBlocking(file, url)
            } catch (e: Exception) {
                error = if (cancelDl) "Download paused. Tap Download to resume."
                else "Download failed: ${e.message}. Tap Download to resume."
            } finally {
                progress.remove(file)
                refreshFiles()
            }
        }
    }

    private fun downloadBlocking(file: String, url: String) {
        val dest = File(modelsDir, file)
        val part = File(modelsDir, "$file.part")
        var existing = if (part.exists()) part.length() else 0L
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 20000
        conn.readTimeout = 30000
        if (existing > 0) conn.setRequestProperty("Range", "bytes=$existing-")
        conn.connect()
        val code = conn.responseCode
        if (code == 416) { part.renameTo(dest); return }
        if (code != 200 && code != 206) throw IOException("HTTP $code")
        val append = code == 206
        if (!append) existing = 0
        val total = existing + conn.contentLengthLong
        FileOutputStream(part, append).use { out ->
            conn.inputStream.use { inp ->
                val buf = ByteArray(64 * 1024)
                var done = existing
                var lastUi = 0L
                while (true) {
                    if (cancelDl) throw IOException("paused")
                    val n = inp.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastUi > 300 && total > 0) {
                        progress[file] = done.toFloat() / total
                        lastUi = now
                    }
                }
            }
        }
        if (!part.renameTo(dest)) throw IOException("could not finalize file")
    }

    fun importModel(uri: Uri) {
        val ctx = getApplication<Application>()
        val name = displayName(uri) ?: "custom-model.gguf"
        if (!name.endsWith(".gguf")) { error = "Pick a .gguf model file."; return }
        viewModelScope.launch(Dispatchers.IO) {
            busy = true
            status = "Copying $name into app storage ..."
            try {
                ctx.contentResolver.openInputStream(uri)!!.use { inp ->
                    FileOutputStream(File(modelsDir, name)).use { inp.copyTo(it, 1 shl 20) }
                }
                status = "Copied $name."
            } catch (e: Exception) {
                error = "Copy failed: ${e.message}"
            }
            busy = false
            refreshFiles()
        }
    }

    private fun displayName(uri: Uri): String? =
        getApplication<Application>().contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && i >= 0) c.getString(i) else null
        }

    // ---------------- knowledge ----------------

    fun addNote(text: String) {
        store.addNote(text)
        refreshEntries()
    }

    fun importText(uri: Uri) {
        val name = displayName(uri) ?: "document"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val text = getApplication<Application>().contentResolver.openInputStream(uri)!!
                    .use { it.readBytes().take(5_000_000).toByteArray().toString(Charsets.UTF_8) }
                if (text.count { it == '\u0000' } > 0) { error = "That looks like a binary file. Use .txt / .md files (PDF not supported)."; return@launch }
                val n = store.addDocument(name, text)
                status = "Saved $name ($n chunks)."
            } catch (e: Exception) {
                error = "Import failed: ${e.message}"
            }
            refreshEntries()
        }
    }

    fun deleteEntry(e: KnowledgeStore.Entry) {
        store.delete(e.ids)
        refreshEntries()
    }

    // ---------------- chat ----------------

    fun clearChat() { if (!generating) messages.clear() }
    fun stop() { LlamaBridge.nativeStop() }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || generating || busy) return
        if (t.startsWith("remember ", ignoreCase = true)) {
            store.addNote(t.substring(9))
            refreshEntries()
            messages += Msg("user", t)
            messages += Msg("assistant", "Saved to memory. I will use it in future answers.")
            return
        }
        if (loadedName == null) { error = "Load a model first (Models tab)."; return }

        messages += Msg("user", t)
        val idx = messages.size
        messages += Msg("assistant", "")
        generating = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prompt = buildPrompt(t, idx)
                val sb = StringBuilder()
                var count = 0
                val t0 = SystemClock.elapsedRealtime()
                val err = LlamaBridge.nativeGenerate(prompt.toByteArray(Charsets.UTF_8), 600,
                    object : LlamaBridge.TokenCallback {
                        override fun onToken(bytes: ByteArray) {
                            sb.append(String(bytes, Charsets.UTF_8))
                            count++
                            messages[idx] = Msg("assistant", clean(sb.toString()))
                        }
                    })
                if (err.isNotEmpty()) messages[idx] = Msg("assistant", "Error: $err")
                else {
                    val secs = (SystemClock.elapsedRealtime() - t0) / 1000.0
                    status = "Ready: $loadedName  |  %.1f tokens/s (incl. prompt)".format(count / secs.coerceAtLeast(0.1))
                }
            } catch (e: Throwable) {
                messages[idx] = Msg("assistant", "Error: ${e.message}")
            } finally {
                generating = false
            }
        }
    }

    private fun clean(s: String): String =
        s.replace(Regex("<think>.*?(</think>|$)", RegexOption.DOT_MATCHES_ALL), "").trimStart()

    private fun buildPrompt(userText: String, upto: Int): String {
        val notes = store.notesText(1500)
        val docs = store.search(userText, 3)
        val sys = StringBuilder("You are a helpful assistant running fully offline on the user's phone. Be clear and concise.")
        if (notes.isNotBlank()) sys.append("\n\nThings the user taught you (trust these):\n").append(notes)
        if (docs.isNotEmpty()) sys.append("\n\nRelevant saved documents:\n").append(docs.joinToString("\n---\n"))
        sys.append("\n\nIf the saved information does not answer the question, answer from general knowledge and say so.")

        var hist = messages.take(upto).takeLast(7)
        while (hist.size > 1 && hist.sumOf { it.text.length } > 3000) hist = hist.drop(1)
        while (hist.isNotEmpty() && hist.first().role != "user") hist = hist.drop(1)
        return Templates.build(tmpl, sys.toString(), hist.map { it.role to it.text })
    }
}
