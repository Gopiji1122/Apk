package com.promusic.player

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.exoplayer.ExoPlayer

/** Gives UI-side helpers access to the player that lives inside the service (same process). */
object PlayerBridge {
    @Volatile
    var player: ExoPlayer? = null
}

object SleepTimer {
    var endsAt by mutableLongStateOf(0L)
    var endOfTrack by mutableStateOf(false)

    private val handler = Handler(Looper.getMainLooper())
    private val fire = Runnable {
        PlayerBridge.player?.pause()
        endsAt = 0L
    }

    fun startMinutes(min: Int) {
        cancel()
        val ms = min * 60_000L
        endsAt = SystemClock.elapsedRealtime() + ms
        handler.postDelayed(fire, ms)
    }

    fun startEndOfTrack() {
        cancel()
        endOfTrack = true
        PlayerBridge.player?.setPauseAtEndOfMediaItems(true)
    }

    fun onEndOfTrackReached() {
        if (endOfTrack) {
            endOfTrack = false
            PlayerBridge.player?.setPauseAtEndOfMediaItems(false)
        }
    }

    fun cancel() {
        handler.removeCallbacks(fire)
        endsAt = 0L
        if (endOfTrack) {
            endOfTrack = false
            PlayerBridge.player?.setPauseAtEndOfMediaItems(false)
        }
    }
}

object AudioFx {
    private var store: Store? = null
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var session = -1

    var available by mutableStateOf(false)
    var enabled by mutableStateOf(false)
    var bandCount by mutableIntStateOf(0)
    var minLevel by mutableIntStateOf(0)
    var maxLevel by mutableIntStateOf(0)
    val levels = mutableStateListOf<Int>()
    var freqs by mutableStateOf<List<Int>>(emptyList())
    var presets by mutableStateOf<List<String>>(emptyList())
    var preset by mutableIntStateOf(-1)
    var bassStrength by mutableIntStateOf(0)
    var bassSupported by mutableStateOf(false)

    fun init(ctx: Context) {
        if (store != null) return
        val s = Store(ctx.applicationContext)
        store = s
        enabled = s.getBool("eq_on", false)
        bassStrength = s.getInt("eq_bass", 0)
        preset = s.getInt("eq_preset", -1)
    }

    fun attach(sessionId: Int) {
        if (sessionId == 0 || sessionId == session) return
        release()
        session = sessionId
        try {
            val e = Equalizer(0, sessionId)
            eq = e
            val n = e.numberOfBands.toInt()
            bandCount = n
            minLevel = e.bandLevelRange[0].toInt()
            maxLevel = e.bandLevelRange[1].toInt()
            freqs = (0 until n).map { e.getCenterFreq(it.toShort()) / 1000 }
            presets = (0 until e.numberOfPresets.toInt()).map { e.getPresetName(it.toShort()) }
            val saved = (store?.getString("eq_levels", "") ?: "").split(',').mapNotNull { it.toIntOrNull() }
            levels.clear()
            for (i in 0 until n) {
                val lv = (saved.getOrNull(i) ?: e.getBandLevel(i.toShort()).toInt()).coerceIn(minLevel, maxLevel)
                levels.add(lv)
                e.setBandLevel(i.toShort(), lv.toShort())
            }
            e.setEnabled(enabled)
            available = true
        } catch (t: Throwable) {
            eq = null
            available = false
        }
        try {
            val b = BassBoost(0, sessionId)
            bass = b
            bassSupported = b.strengthSupported
            if (bassSupported) b.setStrength(bassStrength.toShort())
            b.setEnabled(enabled)
        } catch (t: Throwable) {
            bass = null
            bassSupported = false
        }
    }

    fun release() {
        try { eq?.release() } catch (_: Throwable) {}
        try { bass?.release() } catch (_: Throwable) {}
        eq = null
        bass = null
        session = -1
    }

    fun enable(on: Boolean) {
        enabled = on
        store?.putBool("eq_on", on)
        try { eq?.setEnabled(on) } catch (_: Throwable) {}
        try { bass?.setEnabled(on) } catch (_: Throwable) {}
    }

    fun changeBand(i: Int, level: Int) {
        if (i !in 0 until levels.size) return
        val v = level.coerceIn(minLevel, maxLevel)
        levels[i] = v
        preset = -1
        try { eq?.setBandLevel(i.toShort(), v.toShort()) } catch (_: Throwable) {}
        persist()
    }

    fun applyPreset(p: Int) {
        val e = eq ?: return
        try {
            e.usePreset(p.toShort())
            preset = p
            for (i in 0 until levels.size) levels[i] = e.getBandLevel(i.toShort()).toInt()
            persist()
        } catch (_: Throwable) {}
    }

    fun changeBass(v: Int) {
        bassStrength = v.coerceIn(0, 1000)
        store?.putInt("eq_bass", bassStrength)
        try { if (bassSupported) bass?.setStrength(bassStrength.toShort()) } catch (_: Throwable) {}
    }

    fun resetAll() {
        for (i in 0 until levels.size) changeBand(i, 0)
        changeBass(0)
    }

    private fun persist() {
        store?.putString("eq_levels", levels.joinToString(","))
        store?.putInt("eq_preset", preset)
    }
}
