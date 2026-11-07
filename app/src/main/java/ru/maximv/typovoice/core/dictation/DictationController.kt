package ru.maximv.typovoice.core.dictation

import android.content.Context
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.maximv.typovoice.core.api.SttApiClient
import ru.maximv.typovoice.core.api.TranscriptionResult
import ru.maximv.typovoice.core.audio.AudioRecorder
import ru.maximv.typovoice.core.servers.AllServersFailedException
import ru.maximv.typovoice.core.history.HistoryEntry
import ru.maximv.typovoice.core.history.historyNote
import ru.maximv.typovoice.core.postprocess.Postprocess
import ru.maximv.typovoice.core.history.HistoryRepository
import ru.maximv.typovoice.core.history.RetryQueue
import ru.maximv.typovoice.core.recognition.SilenceDetector
import java.io.File
import ru.maximv.typovoice.core.servers.ServerPool
import ru.maximv.typovoice.core.settings.AppSettings
import ru.maximv.typovoice.core.settings.AutoStopMode
import ru.maximv.typovoice.core.settings.SettingsRepository
import ru.maximv.typovoice.delivery.TextDelivery
import ru.maximv.typovoice.R
import ru.maximv.typovoice.str

/** Where a dictation was triggered from; used for logs and to pick where the text goes. */
enum class DictationSource { APP, OVERLAY, TILE, VOLUME_KEY, WIDGET, SHORTCUT, SIDE_KEY, ASSISTANT }

sealed interface DictationState {
    data object Idle : DictationState
    data class Starting(val source: DictationSource, val tapped: Boolean = true) : DictationState
    data class Recording(val source: DictationSource, val startedAt: Long) : DictationState
    /** [partial] grows while a streamed response arrives. */
    data class Transcribing(val source: DictationSource, val partial: String = "") : DictationState
    data class Done(val text: String, val delivery: TextDelivery.Outcome, val serverName: String) : DictationState
    data class Failed(val message: String) : DictationState

    val isBusy get() = this is Starting || this is Recording || this is Transcribing
}

/**
 * Single owner of the dictation pipeline: record → upload → deliver.
 * Entry points only call [start], [stop], [toggle] and [cancel]; recording itself happens
 * while [DictationService] holds a microphone foreground service.
 */
class DictationController(
    private val context: Context,
    private val settings: SettingsRepository,
    private val api: SttApiClient,
    private val pool: ServerPool,
    private val delivery: TextDelivery,
    private val appSwitch: ru.maximv.typovoice.core.power.AppSwitch,
    private val history: HistoryRepository,
    private val retryQueue: RetryQueue,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val recorder = AudioRecorder(context)

    private val _state = MutableStateFlow<DictationState>(DictationState.Idle)
    val state: StateFlow<DictationState> = _state.asStateFlow()

    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    private var stopRequested = false
    private var snapshot = AppSettings()

    init {
        scope.launch { settings.settings.collect { snapshot = it } }
    }
    private var levelJob: Job? = null
    private var transcribeJob: Job? = null
    private var resetJob: Job? = null

    fun toggle(source: DictationSource) {
        when (_state.value) {
            is DictationState.Recording, is DictationState.Starting -> stop()
            is DictationState.Transcribing -> Unit
            else -> start(source, tapped = true)
        }
    }

    /** [tapped] = started by a tap (may auto-stop on a pause); false = held push-to-talk. */
    fun start(source: DictationSource, tapped: Boolean = false) {
        if (_state.value.isBusy || !appSwitch.isEnabled) return
        stopRequested = false
        resetJob?.cancel()
        _state.value = DictationState.Starting(source, tapped)
        DictationService.start(context)
    }

    /** Called by [DictationService] once it is in the foreground and may use the microphone. */
    internal fun onServiceReady() {
        val starting = _state.value as? DictationState.Starting ?: return
        try {
            recorder.start()
        } catch (e: Exception) {
            Log.e(TAG, "Recorder failed to start", e)
            fail(str(R.string.dict_start_failed_detail, e.message))
            return
        }
        _state.value = DictationState.Recording(starting.source, System.currentTimeMillis())
        val detector = if (autoStops(starting)) SilenceDetector(snapshot.recognitionSilenceMs) else null
        val limitMs = snapshot.maxRecordingMinutes * 60_000L
        val startedAt = System.currentTimeMillis()
        levelJob = scope.launch {
            while (isActive) {
                // A forgotten recording stops by itself and is sent, so nothing said is lost.
                if (limitMs > 0 && System.currentTimeMillis() - startedAt >= limitMs) {
                    Toast.makeText(context, str(R.string.dict_limit_reached, snapshot.maxRecordingMinutes), Toast.LENGTH_LONG).show()
                    stop()
                    return@launch
                }
                val level = recorder.level()
                _level.value = level
                when (detector?.feed(level, System.currentTimeMillis())) {
                    SilenceDetector.Event.PHRASE_ENDED, SilenceDetector.Event.MAX_DURATION -> {
                        stop()
                        return@launch
                    }
                    SilenceDetector.Event.NO_SPEECH -> {
                        fail(str(R.string.dict_no_speech))
                        return@launch
                    }
                    else -> Unit
                }
                delay(LEVEL_INTERVAL_MS)
            }
        }
        // Push-to-talk released before the service came up: honour it now.
        if (stopRequested) stop()
    }

    private fun autoStops(starting: DictationState.Starting) = starting.tapped && when (snapshot.autoStop) {
        AutoStopMode.OFF -> false
        AutoStopMode.ASSISTANT_ONLY -> starting.source == DictationSource.ASSISTANT
        AutoStopMode.ALL_TAPS -> true
    }

    internal fun onServiceFailed(error: Throwable) {
        Log.e(TAG, "Foreground service failed", error)
        fail(str(R.string.dict_background_denied, error.message))
    }

    fun stop() {
        when (val current = _state.value) {
            is DictationState.Starting -> stopRequested = true
            is DictationState.Recording -> finishRecording(current.source)
            else -> Unit
        }
    }

    fun cancel() {
        transcribeJob?.cancel()
        stopLevel()
        recorder.cancel()
        _state.value = DictationState.Idle
        DictationService.stop(context)
    }

    /** Shows a finished/failed state for a moment so UIs can flash it, then returns to idle. */
    private fun finish(state: DictationState) {
        _state.value = state
        resetJob?.cancel()
        resetJob = scope.launch {
            delay(RESULT_VISIBLE_MS)
            if (_state.value === state) _state.value = DictationState.Idle
        }
    }

    private fun finishRecording(source: DictationSource) {
        stopLevel()
        val recording = recorder.stop()
        if (recording == null) {
            _state.value = DictationState.Idle
            DictationService.stop(context)
            return
        }
        _state.value = DictationState.Transcribing(source)
        transcribeJob = scope.launch {
            var keptAudio = false
            try {
                val (result, serverName) = transcribe(recording.file) { partial ->
                    val current = _state.value
                    if (current is DictationState.Transcribing) _state.value = current.copy(partial = partial)
                }
                if (result.ignoredParams.isNotEmpty()) Log.w(TAG, "Ignored by the key: ${result.ignoredParams}")
                val outcome = delivery.deliver(result.text, source)
                finish(DictationState.Done(result.text, outcome, serverName))
                record(HistoryEntry(source = source.name, durationMs = recording.durationMs, serverName = serverName, text = result.text, rawText = result.rawText, note = result.historyNote()))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e !is AllServersFailedException) Log.e(TAG, "Transcription failed", e)
                val message = e.message ?: str(R.string.dict_recognition_error)
                // Keep the audio so the dictation can be retried from history.
                val audio = if (snapshot.historyEnabled) history.keepAudio(recording.file) else null
                keptAudio = audio != null
                fail(if (keptAudio) str(R.string.dict_saved_in_history, message) else message)
                val entry = HistoryEntry(source = source.name, durationMs = recording.durationMs, error = message, audioPath = audio)
                record(entry)
                if (keptAudio) retryQueue.enqueueIfEnabled(entry, (e as? AllServersFailedException)?.retryable ?: true)
            } finally {
                if (!keptAudio) recording.file.delete()
                DictationService.stop(context)
            }
        }
    }

    /** Adds to history if it is enabled. */
    suspend fun record(entry: HistoryEntry) {
        if (snapshot.historyEnabled) history.add(entry)
    }

    /** Re-sends a failed recording from history; on success the text can be copied to the clipboard. */
    suspend fun retry(entry: HistoryEntry, copyToClipboard: Boolean = true): Result<String> {
        val audio = entry.audioPath?.let(::File)?.takeIf { it.exists() }
            ?: return Result.failure(IllegalStateException(str(R.string.dict_recording_not_found)))
        return try {
            val (result, serverName) = transcribe(audio)
            history.replace(
                entry.copy(
                    text = result.text,
                    rawText = result.rawText,
                    note = result.historyNote(),
                    serverName = serverName,
                    error = null,
                    audioPath = null,
                    retryStatus = null,
                    nextRetryAt = null,
                ),
            )
            audio.delete()
            if (copyToClipboard) delivery.copyToClipboard(result.text)
            Result.success(result.text)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            history.replace(entry.copy(error = e.message ?: str(R.string.dict_recognition_error)))
            Result.failure(e)
        }
    }

    /**
     * Transcribes an existing audio file through the server pool; returns the result and the server name.
     * [onPartial] gets the text received so far when the server streams (called on the main thread).
     */
    suspend fun transcribe(file: java.io.File, onPartial: ((String) -> Unit)? = null): Pair<TranscriptionResult, String> {
        val current = settings.current()
        val (result, newState) = pool.execute(
            current.servers,
            current.policy,
            current.fixedServerId,
            current.poolState,
            quickRetry = current.autoRetry,
        ) { server ->
            val streamed = StringBuilder()
            val onDelta: ((String) -> Unit)? = onPartial?.let { callback ->
                { delta: String ->
                    val text = synchronized(streamed) { streamed.append(delta).toString() }
                    scope.launch { callback(text) }
                    Unit
                }
            }
            api.transcribe(server.endpoint, file, Postprocess.request(server, current.postprocessChoice), onDelta) to server.name
        }
        settings.setPoolState(newState)
        return result
    }

    private fun fail(message: String) {
        stopLevel()
        recorder.cancel()
        finish(DictationState.Failed(message))
        DictationService.stop(context)
    }

    private fun stopLevel() {
        levelJob?.cancel()
        levelJob = null
        _level.value = 0f
    }

    private companion object {
        const val TAG = "DictationController"
        const val LEVEL_INTERVAL_MS = 80L
        const val RESULT_VISIBLE_MS = 1500L
    }
}
