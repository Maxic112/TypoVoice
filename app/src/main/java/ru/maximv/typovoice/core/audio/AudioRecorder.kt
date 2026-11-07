package ru.maximv.typovoice.core.audio

import android.content.Context
import android.media.MediaRecorder
import java.io.File

/**
 * Records the microphone to an OGG/Opus file: small uploads, and the server decodes it natively.
 * Must be started while the app is allowed to use the microphone (foreground service of type microphone).
 */
class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    val isRecording get() = recorder != null

    fun start(): File {
        check(recorder == null) { "Already recording" }
        val dir = File(context.cacheDir, "recordings").apply { mkdirs() }
        val output = File(dir, "rec-${System.currentTimeMillis()}.ogg")
        val mr = MediaRecorder(context).apply {
            setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            setOutputFormat(MediaRecorder.OutputFormat.OGG)
            setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            setAudioSamplingRate(SAMPLE_RATE)
            setAudioChannels(1)
            setAudioEncodingBitRate(BIT_RATE)
            setOutputFile(output)
        }
        try {
            mr.prepare()
            mr.start()
        } catch (e: Exception) {
            mr.release()
            output.delete()
            throw e
        }
        recorder = mr
        file = output
        startedAt = System.currentTimeMillis()
        return output
    }

    /** Peak amplitude since the previous call, normalized to 0..1, for the level indicator. */
    fun level(): Float = (recorder?.maxAmplitude ?: 0) / 32767f

    /** Stops recording and returns the file with its duration, or null if nothing usable was recorded. */
    fun stop(): Recording? {
        val mr = recorder ?: return null
        val output = file
        val durationMs = System.currentTimeMillis() - startedAt
        recorder = null
        file = null
        val ok = try {
            mr.stop()
            true
        } catch (_: RuntimeException) {
            // stop() throws when no data was captured (stopped right after start).
            false
        } finally {
            mr.release()
        }
        if (!ok || output == null || durationMs < MIN_DURATION_MS) {
            output?.delete()
            return null
        }
        return Recording(output, durationMs)
    }

    fun cancel() {
        stop()?.file?.delete()
    }

    data class Recording(val file: File, val durationMs: Long)

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val BIT_RATE = 32_000
        const val MIN_DURATION_MS = 300L
    }
}
