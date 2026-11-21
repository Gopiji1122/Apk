package com.aura.audiopro.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Real-time 16-bit Stereo PCM Left/Right Balance processor injected into ExoPlayer's DefaultAudioSink.
 * @author Gopal Gorchhiya
 */
class StereoBalanceAudioProcessor : BaseAudioProcessor() {
    @Volatile
    var balance: Float = 0f // -1.0f (Full Left) to +1.0f (Full Right)

    private var inputChannelCount: Int = 2

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        if (inputAudioFormat.channelCount != 1 && inputAudioFormat.channelCount != 2) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        inputChannelCount = inputAudioFormat.channelCount
        // Always output 2-channel 16-bit stereo so both mono and stereo files pan cleanly across L/R speakers
        return AudioProcessor.AudioFormat(
            inputAudioFormat.sampleRate,
            2,
            C.ENCODING_PCM_16BIT
        )
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val bal = balance.coerceIn(-1f, 1f)
        val leftGain = if (bal <= 0f) 1f else (1f - bal).coerceIn(0f, 1f)
        val rightGain = if (bal >= 0f) 1f else (1f + bal).coerceIn(0f, 1f)

        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)

        if (inputChannelCount == 1) {
            // Upmix Mono (2 bytes per sample) -> Stereo (4 bytes per frame) with L/R gain
            val sampleCount = remaining / 2
            val outputBuffer = replaceOutputBuffer(sampleCount * 4)
            outputBuffer.order(ByteOrder.LITTLE_ENDIAN)
            while (inputBuffer.remaining() >= 2) {
                val mono = inputBuffer.short
                val scaledLeft = (mono * leftGain).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
                val scaledRight = (mono * rightGain).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
                outputBuffer.putShort(scaledLeft)
                outputBuffer.putShort(scaledRight)
            }
            outputBuffer.flip()
        } else {
            val outputBuffer = replaceOutputBuffer(remaining)
            outputBuffer.order(ByteOrder.LITTLE_ENDIAN)
            while (inputBuffer.remaining() >= 4) {
                val leftSample = inputBuffer.short
                val rightSample = inputBuffer.short
                val scaledLeft = (leftSample * leftGain).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
                val scaledRight = (rightSample * rightGain).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
                outputBuffer.putShort(scaledLeft)
                outputBuffer.putShort(scaledRight)
            }
            while (inputBuffer.hasRemaining()) {
                outputBuffer.put(inputBuffer.get())
            }
            outputBuffer.flip()
        }
    }
}

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

    val stereoBalanceProcessor = StereoBalanceAudioProcessor()

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
        stereoBalanceProcessor.balance = clamped
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
