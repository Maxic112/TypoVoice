package ru.maximv.typovoice.recognition

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.maximv.typovoice.core.recognition.SilenceDetector
import ru.maximv.typovoice.core.recognition.SilenceDetector.Event

class SilenceDetectorTest {
    /** Feeds [levels] every 100 ms and returns the first non-NONE event with its time. */
    private fun run(detector: SilenceDetector, levels: List<Float>): List<Pair<Long, Event>> =
        levels.mapIndexedNotNull { i, level ->
            val t = i * 100L
            detector.feed(level, t).takeIf { it != Event.NONE }?.let { t to it }
        }

    private fun quiet(n: Int) = List(n) { 0.01f }
    private fun loud(n: Int) = List(n) { 0.4f }

    @Test
    fun `phrase ends after the configured pause`() {
        val events = run(SilenceDetector(silenceMs = 1_500), quiet(5) + loud(10) + quiet(20))
        assertEquals(Event.SPEECH_STARTED, events[0].second)
        assertEquals(500L, events[0].first)
        // Last loud sample at 1400 ms → ends at 2900 ms.
        assertEquals(2_900L to Event.PHRASE_ENDED, events[1])
    }

    @Test
    fun `short pauses inside speech do not end the phrase`() {
        val events = run(SilenceDetector(silenceMs = 1_500), loud(5) + quiet(10) + loud(5) + quiet(5))
        assertEquals(listOf(Event.SPEECH_STARTED), events.map { it.second })
    }

    @Test
    fun `silence only times out as no speech`() {
        val events = run(SilenceDetector(silenceMs = 1_500, noSpeechTimeoutMs = 3_000), quiet(40))
        assertEquals(3_000L to Event.NO_SPEECH, events.first())
    }

    @Test
    fun `steady background noise is not speech`() {
        val noisy = List(50) { if (it % 2 == 0) 0.03f else 0.04f }
        val events = run(SilenceDetector(silenceMs = 1_500, noSpeechTimeoutMs = 4_000), noisy)
        assertEquals(Event.NO_SPEECH, events.first().second)
    }

    @Test
    fun `recording stops at max duration`() {
        val events = run(SilenceDetector(silenceMs = 1_500, maxDurationMs = 2_000), loud(30))
        assertEquals(2_000L to Event.MAX_DURATION, events[1])
    }
}
