package ru.maximv.typovoice.core.recognition

/**
 * Decides when a spoken phrase is over, from microphone peak levels (0..1) sampled over time.
 * The threshold adapts to the room: it sits a few times above the tracked noise floor.
 */
class SilenceDetector(
    private val silenceMs: Long,
    private val noSpeechTimeoutMs: Long = 6_000,
    private val maxDurationMs: Long = 120_000,
) {
    enum class Event { NONE, SPEECH_STARTED, PHRASE_ENDED, NO_SPEECH, MAX_DURATION }

    private var startedAt = -1L
    private var lastVoiceAt = -1L
    private var noiseFloor = 0.01f
    var speechDetected = false
        private set

    fun feed(level: Float, nowMs: Long): Event {
        if (startedAt < 0) startedAt = nowMs
        val elapsed = nowMs - startedAt
        val threshold = maxOf(MIN_THRESHOLD, noiseFloor * FLOOR_FACTOR)
        val voiced = level > threshold
        if (!voiced) {
            // Slowly follow the background noise; voiced samples don't raise the floor.
            noiseFloor = noiseFloor * 0.9f + level * 0.1f
        }

        if (elapsed >= maxDurationMs) return Event.MAX_DURATION
        if (voiced) {
            lastVoiceAt = nowMs
            if (!speechDetected) {
                speechDetected = true
                return Event.SPEECH_STARTED
            }
            return Event.NONE
        }
        if (!speechDetected) {
            return if (elapsed >= noSpeechTimeoutMs) Event.NO_SPEECH else Event.NONE
        }
        return if (nowMs - lastVoiceAt >= silenceMs) Event.PHRASE_ENDED else Event.NONE
    }

    private companion object {
        const val MIN_THRESHOLD = 0.05f
        const val FLOOR_FACTOR = 4f
    }
}
