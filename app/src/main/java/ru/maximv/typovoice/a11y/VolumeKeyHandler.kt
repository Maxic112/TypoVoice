package ru.maximv.typovoice.a11y

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.ViewConfiguration
import ru.maximv.typovoice.core.dictation.DictationController
import ru.maximv.typovoice.core.dictation.DictationSource
import ru.maximv.typovoice.core.settings.VolumeKeyMode
import ru.maximv.typovoice.core.settings.VolumeTrigger

/**
 * Dictation on a volume key (screen on only: that's when key events reach accessibility services).
 * [trigger] picks the gesture: a press, a double press, a hold (push-to-talk) or a hold that toggles.
 * Where a short press must still change the volume, the key is held back until the gesture is clear,
 * then the volume change is replayed. While a recording is going on, a press of the key stops it;
 * with [VolumeTrigger.HOLD_TOGGLE] only a hold does, and a short press keeps changing the volume.
 */
class VolumeKeyHandler(
    private val context: Context,
    private val dictation: DictationController,
) {
    var mode = VolumeKeyMode.OFF
    var trigger = VolumeTrigger.HOLD

    private val handler = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(AudioManager::class.java)
    private val doublePressMs = ViewConfiguration.getDoubleTapTimeout().toLong()
    private var pressed = false
    private var longPress = false
    private var stopOnRelease = false

    /** First press of a possible double press; replays the volume change if no second press comes. */
    private var awaitingSecond = false
    private var secondPress = false

    private val onHold = Runnable {
        longPress = true
        if (trigger == VolumeTrigger.HOLD_TOGGLE) {
            dictation.toggle(DictationSource.VOLUME_KEY)
        } else {
            dictation.start(DictationSource.VOLUME_KEY)
        }
    }
    private val singlePress = Runnable {
        awaitingSecond = false
        replayVolumeChange()
    }

    /** Returns true when the event is consumed. */
    fun onKeyEvent(event: KeyEvent): Boolean {
        val key = when (mode) {
            VolumeKeyMode.OFF -> return false
            VolumeKeyMode.VOLUME_DOWN -> KeyEvent.KEYCODE_VOLUME_DOWN
            VolumeKeyMode.VOLUME_UP -> KeyEvent.KEYCODE_VOLUME_UP
        }
        if (event.keyCode != key) return false
        // Calls and ringing keep their native volume-key behaviour.
        if (!pressed && audio.mode != AudioManager.MODE_NORMAL) return false

        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) onDown()
            KeyEvent.ACTION_UP -> onUp()
        }
        return true
    }

    private fun onDown() {
        pressed = true
        longPress = false
        stopOnRelease = dictation.state.value.isBusy && trigger != VolumeTrigger.HOLD_TOGGLE
        if (stopOnRelease) return
        when (trigger) {
            VolumeTrigger.HOLD, VolumeTrigger.HOLD_TOGGLE -> handler.postDelayed(onHold, HOLD_MS)
            VolumeTrigger.DOUBLE_PRESS -> if (awaitingSecond) {
                handler.removeCallbacks(singlePress)
                awaitingSecond = false
                secondPress = true
            }
            VolumeTrigger.PRESS -> Unit
        }
    }

    private fun onUp() {
        handler.removeCallbacks(onHold)
        pressed = false
        if (stopOnRelease) {
            stopOnRelease = false
            dictation.stop()
            return
        }
        when (trigger) {
            VolumeTrigger.HOLD -> if (longPress) dictation.stop() else replayVolumeChange()
            VolumeTrigger.HOLD_TOGGLE -> if (!longPress) replayVolumeChange()
            VolumeTrigger.PRESS -> dictation.toggle(DictationSource.VOLUME_KEY)
            VolumeTrigger.DOUBLE_PRESS -> if (secondPress) {
                secondPress = false
                dictation.toggle(DictationSource.VOLUME_KEY)
            } else {
                awaitingSecond = true
                handler.postDelayed(singlePress, doublePressMs)
            }
        }
    }

    private fun replayVolumeChange() {
        val direction = if (mode == VolumeKeyMode.VOLUME_DOWN) AudioManager.ADJUST_LOWER else AudioManager.ADJUST_RAISE
        audio.adjustSuggestedStreamVolume(direction, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI)
    }

    private companion object {
        const val HOLD_MS = 400L
    }
}
