package com.offlineai.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlin.math.ln
import kotlin.math.min

/** Local knowledge: short "notes" (always sent) and documents (chunked, retrieved by BM25 keyword search). */
class KnowledgeStore(ctx: Context) : SQLiteOpenHelper(ctx, "kb.db", null, 1) {

    data class Item(val id: Long, val kind: String, val source: String, val text: String)
    data class Entry(val kind: String, val title: String, val preview: String, val ids: List<Long>)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE items(id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT, source TEXT, text TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}

    private fun insert(db: SQLiteDatabase, kind: String, source: String, text: String) {
        val cv = ContentValues().apply { put("kind", kind); put("source", source); put("text", text) }
        db.insert("items", null, cv)
    }

    fun addNote(text: String) {
        if (text.isBlank()) return
        insert(writableDatabase, "note", "note", text.trim())
    }

    fun addDocument(source: String, text: String): Int {
        val chunks = chunk(text)
        val db = writableDatabase
        db.beginTransaction()
        try {
            chunks.forEach { insert(db, "doc", source, it) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return chunks.size
    }

    fun all(): List<Item> {
        val out = ArrayList<Item>()
        readableDatabase.rawQuery("SELECT id, kind, source, text FROM items ORDER BY id", null).use { c ->
            while (c.moveToNext()) out += Item(c.getLong(0), c.getString(1), c.getString(2), c.getString(3))
        }
        return out
    }

    fun entries(): List<Entry> {
        val items = all()
        val result = ArrayList<Entry>()
        items.filter { it.kind == "note" }.reversed().forEach {
            result += Entry("note", "Note", it.text.take(140), listOf(it.id))
        }
        items.filter { it.kind == "doc" }.groupBy { it.source }.forEach { (src, list) ->
            result += Entry("doc", src, "${list.size} chunks", list.map { it.id })
        }
        return result
    }

    fun delete(ids: List<Long>) {
        val db = writableDatabase
        ids.forEach { db.delete("items", "id=?", arrayOf(it.toString())) }
    }

    fun notesText(maxChars: Int): String {
        val sb = StringBuilder()
        for (n in all().filter { it.kind == "note" }.reversed()) {
            if (sb.length + n.text.length > maxChars) break
            sb.append("- ").append(n.text).append("\n")
        }
        return sb.toString().trim()
    }

    // ---- retrieval (BM25 keyword search, fully offline) ----

    private val stop = setOf("the", "and", "for", "with", "what", "are", "was", "how", "you", "this", "that", "can", "does", "from", "about", "tell")

    private fun tokens(s: String): List<String> =
        s.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 2 && it !in stop }

    fun search(query: String, k: Int): List<String> {
        val docs = all().filter { it.kind == "doc" }
        val q = tokens(query).distinct()
        if (docs.isEmpty() || q.isEmpty()) return emptyList()
        val toks = docs.map { tokens(it.text) }
        val n = docs.size
        val avg = toks.sumOf { it.size }.toDouble().coerceAtLeast(1.0) / n
        val df = HashMap<String, Int>()
        toks.forEach { t -> t.toSet().forEach { df[it] = (df[it] ?: 0) + 1 } }
        val k1 = 1.5
        val b = 0.75
        val scored = docs.indices.map { i ->
            val tf = HashMap<String, Int>()
            toks[i].forEach { tf[it] = (tf[it] ?: 0) + 1 }
            var s = 0.0
            for (w in q) {
                val f = (tf[w] ?: 0).toDouble()
                if (f == 0.0) continue
                val d = (df[w] ?: 0).toDouble()
                val idf = ln(1 + (n - d + 0.5) / (d + 0.5))
                s += idf * f * (k1 + 1) / (f + k1 * (1 - b + b * toks[i].size / avg))
            }
            i to s
        }
        return scored.filter { it.second > 0 }.sortedByDescending { it.second }.take(k)
            .map { "[${docs[it.first].source}] ${docs[it.first].text}" }
    }

    companion object {
        fun chunk(text: String, size: Int = 700, overlap: Int = 100): List<String> {
            val paras = text.replace("\r", "").split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotEmpty() }
            val out = ArrayList<String>()
            var cur = StringBuilder()
            fun flush() { if (cur.isNotEmpty()) { out += cur.toString(); cur = StringBuilder() } }
            for (p in paras) {
                if (p.length > size) {
                    flush()
                    var i = 0
                    while (i < p.length) {
                        val e = min(p.length, i + size)
                        out += p.substring(i, e)
                        if (e == p.length) break
                        i = e - overlap
                    }
                } else {
                    if (cur.length + p.length + 2 > size) flush()
                    if (cur.isNotEmpty()) cur.append("\n\n")
                    cur.append(p)
                }
            }
            flush()
            return out
        }
    }
}
