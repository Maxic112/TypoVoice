package ru.maximv.typovoice.core.feedback

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.maximv.typovoice.core.dictation.DictationState
import ru.maximv.typovoice.core.settings.SettingsRepository

/**
 * Sound and vibration cues for dictation: start, stop, result, error. Driven by state
 * transitions, so every entry point (bubble, tile, keys, assistant…) gets the same cues.
 * Sounds play only in normal ringer mode.
 */
class Feedback(
    private val context: Context,
    private val settings: SettingsRepository,
) {
    private enum class Cue { START, STOP, SUCCESS, ERROR }

    private val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
    private val audio = context.getSystemService(AudioManager::class.java)
    private var sound = false
    private var vibration = true

    fun observe(scope: CoroutineScope, state: StateFlow<DictationState>) {
        scope.launch {
            settings.settings.collect {
                sound = it.soundFeedback
                vibration = it.vibrationFeedback
            }
        }
        scope.launch {
            var previous: DictationState = state.value
            state.collect { current ->
                cueFor(previous, current)?.let(::play)
                previous = current
            }
        }
    }

    /** Light confirmation for non-dictation actions (e.g. the bubble menu); respects the vibration switch. */
    fun tick() {
        if (vibration) vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
    }

    private fun cueFor(previous: DictationState, current: DictationState): Cue? = when {
        current is DictationState.Recording && previous !is DictationState.Recording -> Cue.START
        current is DictationState.Transcribing && previous is DictationState.Recording -> Cue.STOP
        current is DictationState.Done && current.text.isNotBlank() -> Cue.SUCCESS
        current is DictationState.Failed -> Cue.ERROR
        else -> null
    }

    private fun play(cue: Cue) {
        if (vibration) {
            vibrator.vibrate(
                when (cue) {
                    Cue.START -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                    Cue.STOP -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                    Cue.SUCCESS -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    Cue.ERROR -> VibrationEffect.createWaveform(longArrayOf(0, 120, 80, 120), -1)
                },
            )
        }
        if (sound && audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            runCatching {
                val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, TONE_VOLUME)
                tone.startTone(
                    when (cue) {
                        Cue.START -> ToneGenerator.TONE_PROP_BEEP
                        Cue.STOP -> ToneGenerator.TONE_PROP_BEEP2
                        Cue.SUCCESS -> ToneGenerator.TONE_PROP_ACK
                        Cue.ERROR -> ToneGenerator.TONE_PROP_NACK
                    },
                    TONE_MS,
                )
                // ToneGenerator holds native resources; free them once the tone is over.
                android.os.Handler(context.mainLooper).postDelayed({ tone.release() }, TONE_MS + 100L)
            }
        }
    }

    private companion object {
        const val TONE_VOLUME = 70
        const val TONE_MS = 150
    }
}
