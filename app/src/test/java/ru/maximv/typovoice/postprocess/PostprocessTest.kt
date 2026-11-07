package ru.maximv.typovoice.postprocess

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.maximv.typovoice.core.api.TranscriptionRequest
import ru.maximv.typovoice.core.postprocess.Postprocess
import ru.maximv.typovoice.core.servers.ServerConfig

class PostprocessTest {
    private val server = ServerConfig(
        "s", "S", "http://s", "k",
        model = "ggml-whisper-large-v3", preset = "cleanup", customPrompt = "P {text}",
        language = "ru", initialPrompt = "TrueNAS", vad = true, stream = true,
    )
    private val plain = server.copy(preset = null, customPrompt = null)

    @Test
    fun `quick choice overrides the server preset and keeps recognition settings`() {
        val base = TranscriptionRequest(
            model = "ggml-whisper-large-v3", language = "ru", prompt = "TrueNAS", vad = true, stream = true,
            preset = "cleanup", customPrompt = "P {text}",
        )
        assertEquals(base, Postprocess.request(server, null))
        assertEquals(base.copy(preset = "none", customPrompt = null), Postprocess.request(server, Postprocess.NONE))
        assertEquals(base.copy(preset = "summary", customPrompt = null), Postprocess.request(server, "summary"))
        assertEquals(base, Postprocess.request(server, Postprocess.CUSTOM))
        assertEquals(base.copy(preset = null, customPrompt = null), Postprocess.request(plain, Postprocess.CUSTOM))
    }

    @Test
    fun `menu lists presets between fixed entries`() {
        assertEquals(
            listOf(null, "none", "cleanup", "summary", ":custom"),
            Postprocess.choices(listOf("cleanup", "none", "summary"), hasCustomPrompt = true),
        )
        assertEquals(listOf(null, "none"), Postprocess.choices(emptyList(), hasCustomPrompt = false))
    }
}
