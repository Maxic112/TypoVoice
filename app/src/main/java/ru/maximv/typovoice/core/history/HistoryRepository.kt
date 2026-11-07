package ru.maximv.typovoice.core.history

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import ru.maximv.typovoice.core.api.TranscriptionResult
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import ru.maximv.typovoice.R
import ru.maximv.typovoice.str

@Serializable
enum class RetryStatus {
    /** Waiting for the next background attempt. */
    QUEUED,

    /** All attempts used up (or the error can't be fixed by retrying); manual retry still possible. */
    EXHAUSTED,
}

@Serializable
data class HistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    /** DictationSource name, or SHARE / SYSTEM for other entry points. */
    val source: String,
    val durationMs: Long? = null,
    val serverName: String? = null,
    /** Final text (after post-processing, if any). */
    val text: String? = null,
    /** Transcription before LLM post-processing; null when none was applied. */
    val rawText: String? = null,
    val error: String? = null,
    /** Server remark on a successful result: parameters it ignored, LLM fallback to the raw text. */
    val note: String? = null,
    /** Audio kept for failed entries so they can be retried; deleted once transcribed. */
    val audioPath: String? = null,
    /** Auto-retry queue status, see [RetryStatus]; null when not queued. */
    val retryStatus: RetryStatus? = null,
    /** Background attempts already made. */
    val retryAttempts: Int = 0,
    val nextRetryAt: Long? = null,
) {
    val failed get() = text == null
    val canRetry get() = failed && audioPath != null && File(audioPath).exists()
}

/** What the user should know about a successful [TranscriptionResult]; null when nothing. */
fun TranscriptionResult.historyNote(): String? {
    val parts = buildList {
        if (ignoredParams.isNotEmpty()) {
            add(str(R.string.hist_ignored_params, ignoredParams.joinToString { ignoredLabel(it) }))
        }
        postprocessError?.let { add(str(R.string.hist_postprocess_failed, it)) }
    }
    return parts.joinToString("\n").ifEmpty { null }
}

private fun ignoredLabel(param: String) = when (param) {
    "language" -> str(R.string.hist_param_language)
    "prompt" -> str(R.string.hist_param_prompt)
    "extv1.preset" -> str(R.string.hist_param_preset)
    else -> param
}

/**
 * Dictation history in a small JSON file (newest first, capped). Failed recordings keep their
 * audio in [audioDir] so nothing dictated is lost to a network error.
 */
class HistoryRepository(baseDir: File) {
    private val file = File(baseDir, "history.json")
    val audioDir = File(baseDir, "history-audio").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    private var loaded = false

    suspend fun load() = mutex.withLock { ensureLoaded() }

    suspend fun get(id: String): HistoryEntry? {
        load()
        return _entries.value.find { it.id == id }
    }

    suspend fun add(entry: HistoryEntry) = update { listOf(entry) + it }

    suspend fun replace(entry: HistoryEntry) = update { list -> list.map { if (it.id == entry.id) entry else it } }

    suspend fun delete(id: String) = update { list ->
        list.find { it.id == id }?.audioPath?.let { File(it).delete() }
        list.filterNot { it.id == id }
    }

    suspend fun clear() = update { list ->
        list.forEach { entry -> entry.audioPath?.let { File(it).delete() } }
        emptyList()
    }

    /** Moves a failed recording into history storage and returns its new path. */
    fun keepAudio(recording: File): String? {
        val target = File(audioDir, recording.name)
        return if (recording.renameTo(target) || runCatching { recording.copyTo(target, overwrite = true) }.isSuccess) {
            recording.delete()
            target.absolutePath
        } else {
            null
        }
    }

    private suspend fun update(change: (List<HistoryEntry>) -> List<HistoryEntry>) = mutex.withLock {
        ensureLoaded()
        val updated = change(_entries.value)
        val kept = updated.take(MAX_ENTRIES)
        updated.drop(MAX_ENTRIES).forEach { entry -> entry.audioPath?.let { File(it).delete() } }
        _entries.value = kept
        withContext(Dispatchers.IO) {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(json.encodeToString(kept))
            tmp.renameTo(file)
        }
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        _entries.value = withContext(Dispatchers.IO) {
            runCatching { json.decodeFromString<List<HistoryEntry>>(file.readText()) }.getOrDefault(emptyList())
        }
        loaded = true
    }

    private companion object {
        const val MAX_ENTRIES = 300
    }
}
