package com.offlineai.assistant.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class KnowledgeNote(
    val id: Long,
    val title: String,
    val category: String,
    val content: String,
    val createdAt: Long
)

class MemoryDbHelper(context: Context) : SQLiteOpenHelper(context, "offline_ai_memory.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE knowledge (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                category TEXT NOT NULL,
                content TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS knowledge")
        onCreate(db)
    }

    fun addNote(title: String, category: String, content: String): Long {
        val cleanTitle = title.trim().ifEmpty { "Saved Note" }
        val cleanCategory = category.trim().ifEmpty { "General" }
        val cleanContent = content.trim()
        if (cleanContent.isEmpty()) return -1L

        val values = ContentValues().apply {
            put("title", cleanTitle)
            put("category", cleanCategory)
            put("content", cleanContent)
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insert("knowledge", null, values)
    }

    /**
     * Splits large imported text files into overlapping ~600-char chunks so RAG stays
     * fast and fits cleanly inside the 2048-token context window.
     */
    fun addChunkedDocument(title: String, category: String, rawText: String): Int {
        val normalized = rawText.replace("\r\n", "\n").trim()
        if (normalized.isEmpty()) return 0

        val chunkSize = 600
        val overlap = 80
        var start = 0
        var part = 1
        while (start < normalized.length) {
            val end = (start + chunkSize).coerceAtMost(normalized.length)
            val chunk = normalized.substring(start, end).trim()
            if (chunk.isNotEmpty()) {
                val chunkTitle = if (normalized.length > chunkSize) "$title (Part $part)" else title
                addNote(chunkTitle, category, chunk)
                part++
            }
            if (end == normalized.length) break
            start += (chunkSize - overlap)
        }
        return part - 1
    }

    fun getAllNotes(): List<KnowledgeNote> {
        val result = mutableListOf<KnowledgeNote>()
        val cursor = readableDatabase.query(
            "knowledge",
            arrayOf("id", "title", "category", "content", "created_at"),
            null,
            null,
            null,
            null,
            "created_at DESC"
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                result.add(
                    KnowledgeNote(
                        id = c.getLong(0),
                        title = c.getString(1),
                        category = c.getString(2),
                        content = c.getString(3),
                        createdAt = c.getLong(4)
                    )
                )
            }
        }
        return result
    }

    fun deleteNote(id: Long) {
        writableDatabase.delete("knowledge", "id = ?", arrayOf(id.toString()))
    }

    /**
     * Lightweight offline keyword + phrase relevance ranker (RAG).
     * Works in English, Hinglish, and Hindi with zero external embedding models needed.
     */
    fun searchRelevant(query: String, limit: Int = 3): List<KnowledgeNote> {
        val all = getAllNotes()
        if (all.isEmpty()) return emptyList()

        val stopWords = setOf(
            "the", "is", "are", "was", "were", "a", "an", "and", "or", "to", "of",
            "in", "on", "for", "with", "my", "me", "i", "you", "it", "this", "that",
            "how", "what", "when", "where", "why", "can", "do", "does", "please", "tell"
        )

        val keywords = query.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 2 && it !in stopWords }
            .distinct()

        if (keywords.isEmpty()) return emptyList()

        val scored = all.mapNotNull { note ->
            val titleLower = note.title.lowercase()
            val catLower = note.category.lowercase()
            val bodyLower = note.content.lowercase()

            var score = 0
            for (kw in keywords) {
                if (titleLower.contains(kw)) score += 4
                if (catLower.contains(kw)) score += 2
                if (bodyLower.contains(kw)) score += 3
            }
            if (score > 0) note to score else null
        }

        return scored
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }
}
