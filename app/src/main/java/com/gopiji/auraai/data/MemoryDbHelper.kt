package com.gopiji.auraai.data

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

data class ChatSession(
    val id: Long,
    val title: String,
    val updatedAt: Long
)

data class StoredChatMessage(
    val id: Long,
    val sessionId: Long,
    val isUser: Boolean,
    val text: String,
    val usedNotesCount: Int = 0
)

class MemoryDbHelper(context: Context) : SQLiteOpenHelper(context, "aura_ai_memory.db", null, 2) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

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
        createChatTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createChatTables(db)
        }
    }

    private fun createChatTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS chat_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS chat_messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id INTEGER NOT NULL,
                is_user INTEGER NOT NULL,
                content TEXT NOT NULL,
                used_notes_count INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                FOREIGN KEY(session_id) REFERENCES chat_sessions(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }

    fun createSession(title: String = "New Chat"): ChatSession {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("title", title)
            put("updated_at", now)
        }
        val id = writableDatabase.insert("chat_sessions", null, values)
        return ChatSession(id, title, now)
    }

    fun getAllSessions(): List<ChatSession> {
        val result = mutableListOf<ChatSession>()
        val cursor = readableDatabase.query(
            "chat_sessions",
            arrayOf("id", "title", "updated_at"),
            null, null, null, null,
            "updated_at DESC"
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                result.add(ChatSession(c.getLong(0), c.getString(1), c.getLong(2)))
            }
        }
        return result
    }

    fun updateSessionTitle(sessionId: Long, newTitle: String) {
        val clean = newTitle.trim().ifEmpty { "New Chat" }.take(44)
        val values = ContentValues().apply {
            put("title", clean)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.update("chat_sessions", values, "id = ?", arrayOf(sessionId.toString()))
    }

    fun touchSession(sessionId: Long) {
        val values = ContentValues().apply {
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.update("chat_sessions", values, "id = ?", arrayOf(sessionId.toString()))
    }

    fun deleteSession(sessionId: Long) {
        writableDatabase.delete("chat_messages", "session_id = ?", arrayOf(sessionId.toString()))
        writableDatabase.delete("chat_sessions", "id = ?", arrayOf(sessionId.toString()))
    }

    fun addMessage(sessionId: Long, isUser: Boolean, text: String, usedNotesCount: Int = 0): Long {
        val values = ContentValues().apply {
            put("session_id", sessionId)
            put("is_user", if (isUser) 1 else 0)
            put("content", text)
            put("used_notes_count", usedNotesCount)
            put("created_at", System.currentTimeMillis())
        }
        val id = writableDatabase.insert("chat_messages", null, values)
        touchSession(sessionId)
        return id
    }

    fun deleteMessage(messageId: Long) {
        writableDatabase.delete("chat_messages", "id = ?", arrayOf(messageId.toString()))
    }

    fun getMessagesForSession(sessionId: Long): List<StoredChatMessage> {
        val result = mutableListOf<StoredChatMessage>()
        val cursor = readableDatabase.query(
            "chat_messages",
            arrayOf("id", "session_id", "is_user", "content", "used_notes_count"),
            "session_id = ?",
            arrayOf(sessionId.toString()),
            null, null,
            "id ASC"
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                result.add(
                    StoredChatMessage(
                        id = c.getLong(0),
                        sessionId = c.getLong(1),
                        isUser = c.getInt(2) == 1,
                        text = c.getString(3),
                        usedNotesCount = c.getInt(4)
                    )
                )
            }
        }
        return result
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
            null, null, null, null,
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
