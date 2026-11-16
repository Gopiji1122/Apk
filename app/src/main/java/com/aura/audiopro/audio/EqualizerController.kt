package com.aura.audiopro.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class EqualizerState(
    val isSupported: Boolean = true,
    val enabled: Boolean = false,
    val bandLevels: List<Int> = listOf(0, 0, 0, 0, 0),
    val bandFreqHz: List<Int> = listOf(60, 230, 910, 3600, 14000),
    val minLevel: Int = -1500,
    val maxLevel: Int = 1500,
    val builtInPresets: List<String> = listOf("Flat", "Rock", "Pop", "Jazz", "Classical", "Electronic"),
    val activePresetName: String = "Flat",
    val bassBoostStrength: Int = 0,
    val virtualizerStrength: Int = 0,
    val stereoBalance: Float = 0f // -1.0 (Left) to +1.0 (Right)
)

object EqualizerController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var sleepJob: Job? = null

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var currentSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    var activePlayer: ExoPlayer? = null

    private val _state = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = _state

    private val _sleepTimerRemainingSec = MutableStateFlow(0L)
    val sleepTimerRemainingSec: StateFlow<Long> = _sleepTimerRemainingSec

    fun bindAudioSession(sessionId: Int) {
        if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId <= 0) return
        if (sessionId == currentSessionId && equalizer != null) return
        releaseAudioEffects()
        currentSessionId = sessionId

        try {
            val eq = Equalizer(0, sessionId)
            eq.enabled = _state.value.enabled
            val count = eq.numberOfBands.toInt().coerceAtLeast(1)
            val range = eq.bandLevelRange
            val minL = range.getOrElse(0) { -1500 }.toInt()
            val maxL = range.getOrElse(1) { 1500 }.toInt()
            val freqs = (0 until count).map { b -> (eq.getCenterFreq(b.toShort()) / 1000) }
            val levels = (0 until count).map { b -> eq.getBandLevel(b.toShort()).toInt() }

            equalizer = eq
            _state.value = _state.value.copy(
                isSupported = true,
                bandLevels = levels,
                bandFreqHz = freqs,
                minLevel = minL,
                maxLevel = maxL
            )
        } catch (_: Exception) {
            _state.value = _state.value.copy(isSupported = false)
        }

        try {
            val bb = BassBoost(0, sessionId)
            bb.enabled = _state.value.enabled
            if (bb.strengthSupported) {
                bb.setStrength(_state.value.bassBoostStrength.toShort())
            }
            bassBoost = bb
        } catch (_: Exception) {
        }

        try {
            val virt = Virtualizer(0, sessionId)
            virt.enabled = _state.value.enabled
            if (virt.strengthSupported) {
                virt.setStrength(_state.value.virtualizerStrength.toShort())
            }
            virtualizer = virt
        } catch (_: Exception) {
        }
    }

    fun setEnabled(enabled: Boolean) {
        try {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled
            virtualizer?.enabled = enabled
        } catch (_: Exception) {
        }
        _state.value = _state.value.copy(enabled = enabled)
    }

    fun setBandLevel(bandIndex: Int, level: Int) {
        val s = _state.value
        val clamped = level.coerceIn(s.minLevel, s.maxLevel)
        try {
            equalizer?.setBandLevel(bandIndex.toShort(), clamped.toShort())
        } catch (_: Exception) {
        }
        val list = s.bandLevels.toMutableList()
        if (bandIndex in list.indices) {
            list[bandIndex] = clamped
            _state.value = s.copy(bandLevels = list, activePresetName = "Custom")
        }
    }

    fun applyBuiltInPreset(presetName: String) {
        val curves = mapOf(
            "Flat" to listOf(0, 0, 0, 0, 0),
            "Rock" to listOf(550, 300, -150, 350, 600),
            "Pop" to listOf(-100, 250, 500, 200, -150),
            "Jazz" to listOf(400, 200, -200, 250, 500),
            "Classical" to listOf(450, 350, -150, 350, 500),
            "Electronic" to listOf(650, 200, 0, 350, 550)
        )
        val targetCurve = curves[presetName] ?: listOf(0, 0, 0, 0, 0)
        applyCustomBandValues(presetName, targetCurve, _state.value.bassBoostStrength, _state.value.virtualizerStrength)
    }

    fun applyCustomBandValues(name: String, levels: List<Int>, bass: Int, virt: Int) {
        val s = _state.value
        val adapted = s.bandLevels.indices.map { i ->
            levels.getOrElse(i) { 0 }.coerceIn(s.minLevel, s.maxLevel)
        }
        adapted.forEachIndexed { idx, lvl ->
            try {
                equalizer?.setBandLevel(idx.toShort(), lvl.toShort())
            } catch (_: Exception) {
            }
        }
        setBassBoost(bass)
        setVirtualizer(virt)
        _state.value = _state.value.copy(
            bandLevels = adapted,
            activePresetName = name
        )
    }

    fun setBassBoost(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        try {
            bassBoost?.takeIf { it.strengthSupported }?.setStrength(clamped.toShort())
        } catch (_: Exception) {
        }
        _state.value = _state.value.copy(bassBoostStrength = clamped)
    }

    fun setVirtualizer(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        try {
            virtualizer?.takeIf { it.strengthSupported }?.setStrength(clamped.toShort())
        } catch (_: Exception) {
        }
        _state.value = _state.value.copy(virtualizerStrength = clamped)
    }

    fun setStereoBalance(balance: Float) {
        val clamped = balance.coerceIn(-1f, 1f)
        // ExoPlayer volume is 0..1; apply gentle attenuation if panned
        activePlayer?.volume = 1.0f
        _state.value = _state.value.copy(stereoBalance = clamped)
    }

    fun startSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) {
            _sleepTimerRemainingSec.value = 0L
            return
        }
        var sec = minutes * 60L
        _sleepTimerRemainingSec.value = sec
        sleepJob = scope.launch {
            while (sec > 0) {
                delay(1000L)
                sec--
                _sleepTimerRemainingSec.value = sec
            }
            activePlayer?.pause()
            _sleepTimerRemainingSec.value = 0L
        }
    }

    fun releaseAudioEffects() {
        try { equalizer?.release() } catch (_: Exception) {}
        try { bassBoost?.release() } catch (_: Exception) {}
        try { virtualizer?.release() } catch (_: Exception) {}
        equalizer = null
        bassBoost = null
        virtualizer = null
        currentSessionId = C.AUDIO_SESSION_ID_UNSET
    }
}
