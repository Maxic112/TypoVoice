package ru.maximv.typovoice.core.recognition

import android.content.Context
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.maximv.typovoice.TypoVoiceApp
import ru.maximv.typovoice.core.api.SttApiException
import ru.maximv.typovoice.core.audio.AudioRecorder
import ru.maximv.typovoice.core.history.HistoryEntry
import ru.maximv.typovoice.core.history.historyNote
import ru.maximv.typovoice.core.servers.AllServersFailedException
import ru.maximv.typovoice.R
import ru.maximv.typovoice.str

/**
 * One utterance for system speech recognition: record until the speaker pauses (or [stop]),
 * then transcribe on the server. Errors use [SpeechRecognizer] error codes.
 * [context] should carry the caller's attribution so the mic indicator names the right app.
 */
class VoiceCapture(
    context: Context,
    private val scope: CoroutineScope,
    private val listener: Listener,
) {
    interface Listener {
        fun onReady()
        fun onSpeechStarted()
        fun onLevel(level: Float)
        fun onSpeechEnded()

        /** Text received so far from a streaming server. */
        fun onPartial(text: String) = Unit
        fun onResult(text: String)
        fun onError(code: Int, message: String)
    }

    private val recorder = AudioRecorder(context)
    private var job: Job? = null
    private var finishing = false

    fun start(silenceMs: Long) {
        try {
            recorder.start()
        } catch (e: Exception) {
            Log.e(TAG, "Recorder failed", e)
            listener.onError(SpeechRecognizer.ERROR_AUDIO, str(R.string.vc_start_failed))
            return
        }
        listener.onReady()
        val detector = SilenceDetector(silenceMs)
        job = scope.launch {
            while (isActive && !finishing) {
                val level = recorder.level()
                listener.onLevel(level)
                when (detector.feed(level, System.currentTimeMillis())) {
                    SilenceDetector.Event.SPEECH_STARTED -> listener.onSpeechStarted()
                    SilenceDetector.Event.PHRASE_ENDED, SilenceDetector.Event.MAX_DURATION -> {
                        finish()
                        return@launch
                    }
                    SilenceDetector.Event.NO_SPEECH -> {
                        cancel()
                        listener.onError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT, str(R.string.dict_no_speech))
                        return@launch
                    }
                    SilenceDetector.Event.NONE -> Unit
                }
                delay(POLL_MS)
            }
        }
    }

    /** Ends recording now and transcribes what was said. */
    fun stop() {
        if (!finishing) {
            job?.cancel()
            finish()
        }
    }

    fun cancel() {
        finishing = true
        job?.cancel()
        recorder.cancel()
    }

    private fun finish() {
        finishing = true
        val recording = recorder.stop()
        listener.onSpeechEnded()
        if (recording == null) {
            listener.onError(SpeechRecognizer.ERROR_NO_MATCH, str(R.string.vc_too_short))
            return
        }
        job = scope.launch {
            try {
                val (result, server) = TypoVoiceApp.container.dictation.transcribe(recording.file) { listener.onPartial(it) }
                if (result.text.isBlank()) {
                    listener.onError(SpeechRecognizer.ERROR_NO_MATCH, str(R.string.vc_not_recognized))
                } else {
                    listener.onResult(result.text)
                    TypoVoiceApp.container.dictation.record(
                        HistoryEntry(
                            source = "SYSTEM",
                            durationMs = recording.durationMs,
                            serverName = server,
                            text = result.text,
                            rawText = result.rawText,
                            note = result.historyNote(),
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed", e)
                listener.onError(errorCode(e), e.message ?: str(R.string.dict_recognition_error))
            } finally {
                recording.file.delete()
            }
        }
    }

    private fun errorCode(e: Exception): Int {
        val causes = (e as? AllServersFailedException)?.failures?.map { it.second } ?: listOf(e)
        return when {
            causes.isEmpty() -> SpeechRecognizer.ERROR_SERVER
            causes.all { it is SttApiException.Network } -> SpeechRecognizer.ERROR_NETWORK
            causes.any { it is SttApiException.RateLimited } -> SpeechRecognizer.ERROR_TOO_MANY_REQUESTS
            else -> SpeechRecognizer.ERROR_SERVER
        }
    }

    private companion object {
        const val TAG = "VoiceCapture"
        const val POLL_MS = 100L
    }
}
