package com.promusic.player

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class Store(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("pro_music_store", Context.MODE_PRIVATE)

    fun getString(key: String, def: String): String = sp.getString(key, def) ?: def
    fun putString(key: String, v: String) { sp.edit().putString(key, v).apply() }
    fun getInt(key: String, def: Int): Int = sp.getInt(key, def)
    fun putInt(key: String, v: Int) { sp.edit().putInt(key, v).apply() }
    fun getLong(key: String, def: Long): Long = sp.getLong(key, def)
    fun putLong(key: String, v: Long) { sp.edit().putLong(key, v).apply() }
    fun getBool(key: String, def: Boolean): Boolean = sp.getBoolean(key, def)
    fun putBool(key: String, v: Boolean) { sp.edit().putBoolean(key, v).apply() }

    fun getLongs(key: String): List<Long> =
        getString(key, "").split(',').mapNotNull { it.trim().toLongOrNull() }

    fun putLongs(key: String, v: List<Long>) = putString(key, v.joinToString(","))

    fun playlists(): LinkedHashMap<String, List<Long>> {
        val out = LinkedHashMap<String, List<Long>>()
        val raw = sp.getString("playlists", null) ?: return out
        try {
            val o = JSONObject(raw)
            val it = o.keys()
            while (it.hasNext()) {
                val name = it.next()
                val arr = o.getJSONArray(name)
                val ids = ArrayList<Long>()
                for (i in 0 until arr.length()) ids.add(arr.getLong(i))
                out[name] = ids
            }
        } catch (_: Exception) {
        }
        return out
    }

    fun putPlaylists(map: Map<String, List<Long>>) {
        val o = JSONObject()
        for ((name, ids) in map) {
            val arr = JSONArray()
            for (id in ids) arr.put(id)
            o.put(name, arr)
        }
        putString("playlists", o.toString())
    }

    fun playCounts(): Map<Long, Int> {
        val out = HashMap<Long, Int>()
        val raw = sp.getString("play_counts", null) ?: return out
        try {
            val o = JSONObject(raw)
            val it = o.keys()
            while (it.hasNext()) {
                val k = it.next()
                val id = k.toLongOrNull() ?: continue
                out[id] = o.getInt(k)
            }
        } catch (_: Exception) {
        }
        return out
    }

    fun putPlayCounts(map: Map<Long, Int>) {
        val o = JSONObject()
        for ((id, n) in map) o.put(id.toString(), n)
        putString("play_counts", o.toString())
    }
}
