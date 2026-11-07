package ru.maximv.typovoice.core.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import ru.maximv.typovoice.R
import ru.maximv.typovoice.str

/** Connection parameters of one transcribe-text-server instance. */
data class ServerEndpoint(
    val baseUrl: String,
    val apiKey: String,
)

/**
 * One `POST /audio/transcriptions` request. Null fields are not sent, so the server applies
 * the model's settings and the key's defaults.
 */
data class TranscriptionRequest(
    /** Model id from `GET /models`; null = the server's current model (alias `default`). */
    val model: String? = null,
    /** ISO-639-1 code; null = auto-detect. Applied only with the key right "Custom parameters". */
    val language: String? = null,
    /** Initial prompt / context for the recognizer. Same right as [language]. */
    val prompt: String? = null,
    /** `chunking_strategy=auto`: the server cuts silence with VAD before recognition. */
    val vad: Boolean = false,
    /** `stream=true`: text arrives as SSE events while the server recognizes. */
    val stream: Boolean = false,
    /** `extv1.preset`: preset name or id, [PRESET_NONE] = no post-processing, null = key default. */
    val preset: String? = null,
    /** `extv1.prompt`: own LLM prompt, `{text}` marks where the transcription goes. */
    val customPrompt: String? = null,
) {
    companion object {
        const val DEFAULT_MODEL = "default"
        const val PRESET_NONE = "none"
    }
}

data class TranscriptionResult(
    /** Final text: after LLM post-processing if it was applied. */
    val text: String,
    /** Transcription before post-processing; null when no post-processing changed it. */
    val rawText: String?,
    /** Applied LLM preset (`extv1.preset`). */
    val preset: String? = null,
    /** The text went through the client's own prompt (`extv1.custom_prompt`). */
    val customPrompt: Boolean = false,
    /** Model that actually recognized the audio. */
    val model: String? = null,
    val languageCode: String? = null,
    val durationSeconds: Double? = null,
    /** Request fields the server did not apply because the key lacks the right. */
    val ignoredParams: List<String> = emptyList(),
    /** LLM error when the preset falls back to the raw text (`on_error=raw`). */
    val postprocessError: String? = null,
    /** `native` or `compat` for streamed responses, null otherwise. */
    val streamMode: String? = null,
)

/** One entry of `GET /models`. */
data class SttModel(
    val id: String,
    val name: String,
    /** For aliases (`whisper-1`, `default` …): the model they point to now. */
    val aliasOf: String?,
    val loaded: Boolean,
    val isDefault: Boolean,
    val languages: List<String>,
    /** Real-time recognition with `stream=true`; null = unknown. */
    val streaming: Boolean?,
    /** Whether `prompt` is honoured; null = unknown. */
    val prompt: Boolean?,
) {
    val isAlias get() = aliasOf != null
}

data class Preset(
    val id: String,
    val name: String,
    val isDefault: Boolean,
    /** `raw` — the raw text plus `postprocess_error` on LLM failure; `error` — HTTP 502. */
    val onError: String?,
)

/** `GET /extv1/presets`: presets the key may use and its post-processing rights. */
data class PresetList(
    val presets: List<Preset>,
    val canChoose: Boolean,
    val canCustomPrompt: Boolean,
    val defaultPreset: String?,
)

data class ServerHealth(
    /** `ok` | `degraded`. */
    val status: String,
    /** unloaded | loading | ready | busy | error | unavailable. */
    val worker: String,
)

/**
 * Client for the transcribe-text-server API: OpenAI Audio API plus extensions in `extv1`
 * (spec: transcribe-text-server/docs/API.md).
 */
class SttApiClient(
    private val http: OkHttpClient = defaultHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** `GET /extv1/health`, no key. 503 still carries a body: report it as a health state, not an error. */
    suspend fun health(baseUrl: String): ServerHealth = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url(baseUrl, "extv1/health")).get().build()
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw SttApiException.Network(e)
        }
        response.use {
            val body = it.body.string()
            val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            val status = obj?.string("status")
            if (status == null) {
                throw SttApiException.fromResponse(it.code, body, it.header("Retry-After"))
            }
            ServerHealth(status, obj.string("worker") ?: "unknown")
        }
    }

    suspend fun models(endpoint: ServerEndpoint): List<SttModel> = withContext(Dispatchers.IO) {
        val request = authorized(endpoint, Request.Builder().url(url(endpoint.baseUrl, "models")).get())
        execute(request) { _, body -> parseModels(body) }
    }

    suspend fun presets(endpoint: ServerEndpoint): PresetList = withContext(Dispatchers.IO) {
        val request = authorized(endpoint, Request.Builder().url(url(endpoint.baseUrl, "extv1/presets")).get())
        execute(request) { _, body -> parsePresets(body) }
    }

    /**
     * Transcribes [audio]. With [TranscriptionRequest.stream] the text deltas go to [onDelta] as they
     * arrive (the result still holds the whole text).
     */
    suspend fun transcribe(
        endpoint: ServerEndpoint,
        audio: File,
        request: TranscriptionRequest = TranscriptionRequest(),
        onDelta: ((String) -> Unit)? = null,
    ): TranscriptionResult = withContext(Dispatchers.IO) {
        val form = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", audio.name, audio.asRequestBody(audioMediaType(audio)))
        formFields(request).forEach { (name, value) -> form.addFormDataPart(name, value) }

        val builder = Request.Builder().url(url(endpoint.baseUrl, "audio/transcriptions")).post(form.build())
        if (request.stream) builder.header("Accept", "text/event-stream")
        val httpRequest = authorized(endpoint, builder)
        if (request.stream) {
            executeStream(httpRequest, onDelta)
        } else {
            execute(httpRequest) { _, body -> parseTranscription(json.parseToJsonElement(body).jsonObject) }
        }
    }

    /** Multipart fields besides `file`, in sending order. */
    internal fun formFields(request: TranscriptionRequest): List<Pair<String, String>> = buildList {
        add("model" to (request.model?.takeIf { it.isNotBlank() } ?: TranscriptionRequest.DEFAULT_MODEL))
        add("response_format" to "json")
        request.language?.takeIf { it.isNotBlank() }?.let { add("language" to it.trim()) }
        request.prompt?.takeIf { it.isNotBlank() }?.let { add("prompt" to it.trim()) }
        if (request.vad) add("chunking_strategy" to "auto")
        if (request.stream) add("stream" to "true")
        val ext = buildJsonObject {
            request.preset?.takeIf { it.isNotBlank() }?.let { put("preset", it) }
            request.customPrompt?.takeIf { it.isNotBlank() }?.let { put("prompt", it) }
        }
        if (ext.isNotEmpty()) add("extv1" to ext.toString())
    }

    internal fun parseTranscription(obj: JsonObject): TranscriptionResult {
        val ext = obj["extv1"] as? JsonObject
        val text = obj.string("text").orEmpty().trim()
        val raw = ext?.string("raw_text")?.trim()
        return TranscriptionResult(
            text = text,
            rawText = raw?.takeIf { it != text },
            preset = ext?.string("preset"),
            customPrompt = ext?.bool("custom_prompt") ?: false,
            model = ext?.string("model"),
            languageCode = ext?.string("language_code"),
            durationSeconds = (obj["usage"] as? JsonObject)?.get("seconds")?.primitiveOrNull?.doubleOrNull,
            ignoredParams = (ext?.get("ignored_params") as? JsonArray)?.mapNotNull { it.primitiveOrNull?.contentOrNull }.orEmpty(),
            postprocessError = ext?.string("postprocess_error"),
            streamMode = ext?.string("stream"),
        )
    }

    internal fun parseModels(body: String): List<SttModel> =
        json.parseToJsonElement(body).jsonObject["data"]!!.jsonArray.map { item ->
            val obj = item.jsonObject
            val ext = obj["extv1"] as? JsonObject
            val caps = ext?.get("capabilities") as? JsonObject
            val id = obj.string("id")!!
            SttModel(
                id = id,
                name = ext?.string("name") ?: id,
                aliasOf = ext?.string("alias_of"),
                loaded = ext?.bool("loaded") ?: false,
                isDefault = ext?.bool("default") ?: false,
                languages = (ext?.get("languages") as? JsonArray)?.mapNotNull { it.primitiveOrNull?.contentOrNull }.orEmpty(),
                streaming = caps?.bool("streaming"),
                prompt = caps?.bool("prompt"),
            )
        }

    internal fun parsePresets(body: String): PresetList {
        val obj = json.parseToJsonElement(body).jsonObject
        val ext = obj["extv1"] as? JsonObject
        return PresetList(
            presets = obj["data"]!!.jsonArray.map { item ->
                val p = item.jsonObject
                Preset(
                    id = p.string("id")!!,
                    name = p.string("name") ?: p.string("id")!!,
                    isDefault = p.bool("default") ?: false,
                    onError = p.string("on_error"),
                )
            },
            canChoose = ext?.bool("can_choose") ?: false,
            canCustomPrompt = ext?.bool("can_custom_prompt") ?: false,
            defaultPreset = ext?.string("default_preset"),
        )
    }

    /**
     * Reads `text/event-stream`: `transcript.text.delta` events, then `transcript.text.done` with the
     * final text, or an `error` event once the status line has already said 200.
     */
    private fun executeStream(request: Request, onDelta: ((String) -> Unit)?): TranscriptionResult {
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw SttApiException.Network(e)
        }
        response.use {
            if (!it.isSuccessful) {
                throw SttApiException.fromResponse(it.code, it.body.string(), it.header("Retry-After"))
            }
            val source = it.body.source()
            val streamed = StringBuilder()
            try {
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isEmpty() || data == "[DONE]") continue
                    val event = json.parseToJsonElement(data).jsonObject
                    when (event.string("type")) {
                        "transcript.text.delta" -> event.string("delta")?.let { delta ->
                            streamed.append(delta)
                            onDelta?.invoke(delta)
                        }
                        "transcript.text.done" -> return parseTranscription(event)
                        "error" -> throw SttApiException.fromError(null, event["error"] as? JsonObject, null)
                    }
                }
            } catch (e: SttApiException) {
                throw e
            } catch (e: IOException) {
                throw SttApiException.Network(e)
            } catch (e: Exception) {
                throw SttApiException.MalformedResponse(e)
            }
            throw SttApiException.MalformedResponse(IllegalStateException(str(R.string.err_stream_no_result)))
        }
    }

    private fun authorized(endpoint: ServerEndpoint, builder: Request.Builder): Request =
        builder.header("Authorization", "Bearer ${endpoint.apiKey.trim()}").build()

    private inline fun <T> execute(request: Request, parse: (Response, String) -> T): T {
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw SttApiException.Network(e)
        }
        response.use {
            val body = it.body.string()
            if (!it.isSuccessful) throw SttApiException.fromResponse(it.code, body, it.header("Retry-After"))
            return try {
                parse(it, body)
            } catch (e: Exception) {
                throw SttApiException.MalformedResponse(e)
            }
        }
    }

    companion object {
        /** Where the API lives on a server given by its site address. */
        const val API_PATH = "/api/transcribe/v1"

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            // Long recordings with LLM post-processing and the server queue can take minutes.
            .readTimeout(600, TimeUnit.SECONDS)
            .build()

        /**
         * Normalizes what the user typed into the API base URL (ends with `/v1`): the site root
         * (`https://stt.example.ru`) gets [API_PATH], `.../api/transcribe` gets `/v1`.
         */
        fun normalizeBaseUrl(input: String): String {
            val base = input.trim().trimEnd('/')
            return when {
                base.endsWith("/v1") -> base
                base.endsWith("/api/transcribe") -> "$base/v1"
                else -> base + API_PATH
            }
        }

        internal fun url(baseUrl: String, path: String) = "${normalizeBaseUrl(baseUrl)}/$path"

        private fun audioMediaType(file: File) = when (file.extension.lowercase()) {
            "ogg", "opus" -> "audio/ogg"
            "m4a", "mp4", "aac" -> "audio/mp4"
            "wav" -> "audio/wav"
            "mp3" -> "audio/mpeg"
            "webm" -> "audio/webm"
            "flac" -> "audio/flac"
            else -> "application/octet-stream"
        }.toMediaType()

        private val JsonElement.primitiveOrNull get() = this as? JsonPrimitive

        private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull

        private fun JsonObject.bool(key: String): Boolean? = (get(key) as? JsonPrimitive)?.booleanOrNull
    }
}
