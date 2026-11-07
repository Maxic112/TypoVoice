package ru.maximv.typovoice.api

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import ru.maximv.typovoice.core.api.ServerEndpoint
import ru.maximv.typovoice.core.api.SttApiClient
import ru.maximv.typovoice.core.api.SttApiException
import ru.maximv.typovoice.core.api.TranscriptionRequest
import java.io.File

class SttApiClientTest {
    private val server = MockWebServer()
    private val client = SttApiClient()
    private lateinit var audio: File

    @Before
    fun setUp() {
        server.start()
        audio = File.createTempFile("voice", ".ogg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
    }

    @After
    fun tearDown() {
        server.close()
        audio.delete()
    }

    private val endpoint get() = ServerEndpoint(server.url("/api/transcribe/v1").toString(), "stt-test")

    @Test
    fun `base url accepts site root, api path and full v1 url`() {
        assertEquals("https://stt.x.ru/api/transcribe/v1", SttApiClient.normalizeBaseUrl("https://stt.x.ru/"))
        assertEquals("https://stt.x.ru/api/transcribe/v1", SttApiClient.normalizeBaseUrl(" https://stt.x.ru/api/transcribe "))
        assertEquals("http://h:1/api/transcribe/v1", SttApiClient.normalizeBaseUrl("http://h:1/api/transcribe/v1/"))
        assertEquals("http://h:1/api/transcribe/v1/models", SttApiClient.url("http://h:1", "models"))
    }

    @Test
    fun `form fields carry model, params and extv1 only when set`() {
        assertEquals(
            listOf("model" to "default", "response_format" to "json"),
            client.formFields(TranscriptionRequest()),
        )
        val full = client.formFields(
            TranscriptionRequest(
                model = "ggml-whisper-large-v3",
                language = "ru",
                prompt = "TrueNAS",
                vad = true,
                stream = true,
                preset = "cleanup",
                customPrompt = "Исправь: {text}",
            ),
        ).toMap()
        assertEquals("ggml-whisper-large-v3", full["model"])
        assertEquals("ru", full["language"])
        assertEquals("TrueNAS", full["prompt"])
        assertEquals("auto", full["chunking_strategy"])
        assertEquals("true", full["stream"])
        assertEquals("""{"preset":"cleanup","prompt":"Исправь: {text}"}""", full["extv1"])
    }

    @Test
    fun `transcribe sends multipart with key and parses json with extv1`() = runTest {
        server.enqueue(
            MockResponse.Builder().body(
                """{"text":" Итог ","usage":{"type":"duration","seconds":2},
                   "extv1":{"model":"ggml-whisper-large-v3","language_code":"ru","raw_text":"сырой","preset":"cleanup",
                   "custom_prompt":false,"postprocess_error":null,"ignored_params":["language"],
                   "timings_ms":{"queue":0,"processing":850,"postprocess":420}}}""",
            ).build(),
        )

        val result = client.transcribe(endpoint, audio, TranscriptionRequest(preset = "cleanup"))

        assertEquals("Итог", result.text)
        assertEquals("сырой", result.rawText)
        assertEquals("cleanup", result.preset)
        assertEquals("ggml-whisper-large-v3", result.model)
        assertEquals("ru", result.languageCode)
        assertEquals(2.0, result.durationSeconds!!, 0.0)
        assertEquals(listOf("language"), result.ignoredParams)
        assertNull(result.postprocessError)

        val request = server.takeRequest()
        assertEquals("/api/transcribe/v1/audio/transcriptions", request.url.encodedPath)
        assertEquals("Bearer stt-test", request.headers["Authorization"])
        val body = request.body!!.utf8()
        assertTrue(body.contains("name=\"file\"; filename=\"${audio.name}\""))
        assertTrue(body.contains("name=\"extv1\"") && body.contains("""{"preset":"cleanup"}"""))
    }

    @Test
    fun `raw text equal to the final text is not reported`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"text":"просто","extv1":{"raw_text":"просто","preset":null}}""").build())
        val result = client.transcribe(endpoint, audio)
        assertEquals("просто", result.text)
        assertNull(result.rawText)
        assertNull(result.preset)
    }

    @Test
    fun `stream collects deltas and returns the done event`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .addHeader("Content-Type", "text/event-stream")
                .body(
                    "data: {\"type\":\"transcript.text.delta\",\"delta\":\"Привет\"}\n\n" +
                        "data: {\"type\":\"transcript.text.delta\",\"delta\":\"! Как дела?\"}\n\n" +
                        "data: {\"type\":\"transcript.text.done\",\"text\":\"Привет! Как дела?\"," +
                        "\"usage\":{\"type\":\"duration\",\"seconds\":8},\"extv1\":{\"stream\":\"native\",\"raw_text\":\"Привет! Как дела?\"}}\n\n",
                ).build(),
        )
        val deltas = mutableListOf<String>()

        val result = client.transcribe(endpoint, audio, TranscriptionRequest(stream = true)) { deltas += it }

        assertEquals(listOf("Привет", "! Как дела?"), deltas)
        assertEquals("Привет! Как дела?", result.text)
        assertEquals("native", result.streamMode)
        assertEquals("text/event-stream", server.takeRequest().headers["Accept"])
    }

    @Test
    fun `error event inside a stream becomes a typed exception`() = runTest {
        server.enqueue(
            MockResponse.Builder().body(
                "data: {\"type\":\"error\",\"error\":{\"message\":\"Engine crashed\",\"type\":\"server_error\",\"param\":null,\"code\":\"transcription_failed\"}}\n\n",
            ).build(),
        )
        try {
            client.transcribe(endpoint, audio, TranscriptionRequest(stream = true))
            fail("expected exception")
        } catch (e: SttApiException.ServerUnavailable) {
            assertEquals("transcription_failed", e.code)
            assertTrue(e.tryAnotherServer)
        }
    }

    @Test
    fun `errors map to typed exceptions`() = runTest {
        fun error(code: Int, errorCode: String?, extra: String = "", headers: (MockResponse.Builder) -> Unit = {}) {
            val codeJson = errorCode?.let { "\"$it\"" } ?: "null"
            server.enqueue(
                MockResponse.Builder().code(code)
                    .body("""{"error":{"message":"m$code","type":"t","param":null,"code":$codeJson}$extra}""")
                    .apply(headers).build(),
            )
        }
        error(401, "invalid_api_key")
        error(429, "queue_full") { it.addHeader("Retry-After", "7") }
        error(413, "file_too_large")
        error(400, "invalid_audio")
        error(400, "preset_required")
        error(403, "model_switch_forbidden")
        error(404, "preset_not_found")
        error(502, "postprocess_failed", extra = ""","extv1":{"raw_text":"сырой"}""")
        error(503, "worker_unavailable")

        suspend fun capture(): SttApiException = try {
            client.transcribe(endpoint, audio)
            fail("expected exception")
            throw IllegalStateException()
        } catch (e: SttApiException) {
            e
        }

        val key = capture()
        assertTrue(key is SttApiException.InvalidKey)
        val limited = capture() as SttApiException.RateLimited
        assertEquals(7, limited.retryAfterSeconds)
        assertEquals("queue_full", limited.code)
        assertTrue(capture() is SttApiException.TooLarge)
        val bad = capture()
        assertTrue(bad is SttApiException.BadRequest && !bad.tryAnotherServer)
        val presetRequired = capture()
        assertTrue(presetRequired is SttApiException.BadRequest && presetRequired.tryAnotherServer)
        assertTrue(capture() is SttApiException.Forbidden)
        assertEquals("preset_not_found", (capture() as SttApiException.NotFound).code)
        assertEquals("сырой", (capture() as SttApiException.PostprocessFailed).rawText)
        assertTrue(capture() is SttApiException.ServerUnavailable)
    }

    @Test
    fun `models keep extv1 details`() = runTest {
        server.enqueue(
            MockResponse.Builder().body(
                """{"object":"list","data":[
                  {"id":"ggml-whisper-large-v3","object":"model","extv1":{"name":"Whisper Large v3","alias_of":null,
                   "loaded":true,"default":true,"languages":["ru","en"],"capabilities":{"streaming":false,"prompt":true}}},
                  {"id":"whisper-1","object":"model","extv1":{"name":"Whisper Large v3","alias_of":"ggml-whisper-large-v3","loaded":true}}
                ]}""",
            ).build(),
        )
        val models = client.models(endpoint)
        assertEquals(listOf("ggml-whisper-large-v3", "whisper-1"), models.map { it.id })
        val whisper = models[0]
        assertEquals("Whisper Large v3", whisper.name)
        assertTrue(whisper.loaded && whisper.isDefault && !whisper.isAlias)
        assertEquals(false, whisper.streaming)
        assertTrue(models[1].isAlias)
        assertEquals("/api/transcribe/v1/models", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `presets come with the key rights`() = runTest {
        server.enqueue(
            MockResponse.Builder().body(
                """{"object":"list","data":[{"id":"cmw1","object":"extv1.preset","name":"cleanup","default":true,"on_error":"raw"}],
                   "extv1":{"can_choose":true,"can_custom_prompt":false,"default_preset":"cleanup"}}""",
            ).build(),
        )
        val list = client.presets(endpoint)
        assertEquals(listOf("cleanup"), list.presets.map { it.name })
        assertTrue(list.presets[0].isDefault)
        assertTrue(list.canChoose)
        assertFalse(list.canCustomPrompt)
        assertEquals("cleanup", list.defaultPreset)
        assertEquals("/api/transcribe/v1/extv1/presets", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `health reports a degraded server instead of failing`() = runTest {
        server.enqueue(MockResponse.Builder().code(503).body("""{"status":"degraded","worker":"unavailable"}""").build())
        val health = client.health(endpoint.baseUrl)
        assertEquals("degraded", health.status)
        assertEquals("unavailable", health.worker)
        assertNull(server.takeRequest().headers["Authorization"])
    }

    @Test(expected = SttApiException.Network::class)
    fun `unreachable server is a network error`() = runTest {
        server.close()
        client.health(endpoint.baseUrl)
    }
}
