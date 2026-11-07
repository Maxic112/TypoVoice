package ru.maximv.typovoice.entry

import android.content.ContextParams
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ru.maximv.typovoice.TypoVoiceApp
import ru.maximv.typovoice.core.recognition.VoiceCapture

/**
 * System speech recognizer backed by the speech server. Apps that use Android's SpeechRecognizer
 * get it once the user picks TypoVoice as the default voice input; nothing changes otherwise.
 * Results arrive after the phrase ends: the server sends no partial results here.
 */
class TypoVoiceRecognitionService : RecognitionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var capture: VoiceCapture? = null

    override fun onStartListening(intent: Intent, callback: Callback) {
        val container = TypoVoiceApp.container
        if (capture != null || !container.appSwitch.isEnabled || container.dictation.state.value.isBusy) {
            safely { callback.error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY) }
            return
        }
        // Record on behalf of the calling app: Android attributes the microphone use to it.
        val attributed = createContext(
            ContextParams.Builder().setNextAttributionSource(callback.callingAttributionSource).build(),
        )
        // Streamed text from the server is offered as partial results to callers that ask for them.
        val wantsPartial = intent.getBooleanExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        val listener = object : VoiceCapture.Listener {
            override fun onReady() = safely { callback.readyForSpeech(Bundle()) }
            override fun onSpeechStarted() = safely { callback.beginningOfSpeech() }
            override fun onLevel(level: Float) = safely { callback.rmsChanged(RMS_MIN + (RMS_MAX - RMS_MIN) * level) }
            override fun onSpeechEnded() = safely { callback.endOfSpeech() }
            override fun onPartial(text: String) {
                if (!wantsPartial) return
                safely {
                    callback.partialResults(
                        Bundle().apply { putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text)) },
                    )
                }
            }
            override fun onResult(text: String) {
                capture = null
                safely {
                    callback.results(
                        Bundle().apply {
                            putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text))
                            putFloatArray(SpeechRecognizer.CONFIDENCE_SCORES, floatArrayOf(1f))
                        },
                    )
                }
            }
            override fun onError(code: Int, message: String) {
                capture = null
                safely { callback.error(code) }
            }
        }
        scope.launch {
            val silence = intent.getLongExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 0L)
                .takeIf { it > 0 } ?: container.settings.current().recognitionSilenceMs
            capture = VoiceCapture(attributed, scope, listener).also { it.start(silence) }
        }
    }

    override fun onStopListening(callback: Callback) {
        capture?.stop()
    }

    override fun onCancel(callback: Callback) {
        capture?.cancel()
        capture = null
    }

    override fun onDestroy() {
        capture?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    /** The client may already be gone; its callbacks then throw RemoteException. */
    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (_: Exception) {
        }
    }

    private companion object {
        // Typical rmsChanged range clients expect (dB-ish).
        const val RMS_MIN = -2f
        const val RMS_MAX = 10f
    }
}
