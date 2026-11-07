package ru.maximv.typovoice.core.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import ru.maximv.typovoice.R
import ru.maximv.typovoice.str

/**
 * Errors of the transcribe-text-server API, mapped from the error table in its spec.
 * [code] is the machine code from `error.code`; [tryAnotherServer] tells the server pool whether
 * failing over to the next server (another key, another config) makes sense.
 */
sealed class SttApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    open val tryAnotherServer: Boolean = false
    open val code: String? = null

    class Network(cause: Throwable) : SttApiException(str(R.string.err_server_unreachable, cause.message), cause) {
        override val tryAnotherServer = true
    }

    /** 401: no key, wrong, disabled or expired. */
    class InvalidKey(message: String) : SttApiException(message) {
        override val tryAnotherServer = true
    }

    /** 403: the key lacks a right (switch model, custom prompt). */
    class Forbidden(message: String, override val code: String?) : SttApiException(message) {
        override val tryAnotherServer = true
    }

    /** 404: model or preset from this server's settings does not exist there. */
    class NotFound(message: String, override val code: String?) : SttApiException(message) {
        override val tryAnotherServer = true
    }

    class RateLimited(message: String, val retryAfterSeconds: Int?, override val code: String? = null) : SttApiException(message) {
        override val tryAnotherServer = true
    }

    class TooLarge(message: String) : SttApiException(message)

    /** 400. Bad audio or values won't get better elsewhere; a missing preset is this server's setting. */
    class BadRequest(message: String, override val code: String? = null) : SttApiException(message) {
        override val tryAnotherServer = code == "preset_required"
    }

    /** 503 (worker down, no model, queue timeout), 502 transcription_failed, 500. */
    class ServerUnavailable(message: String, override val code: String? = null) : SttApiException(message) {
        override val tryAnotherServer = true
    }

    /** 502 postprocess_failed: the LLM failed; [rawText] is what was recognized. */
    class PostprocessFailed(message: String, val rawText: String?) : SttApiException(message) {
        override val tryAnotherServer = true
        override val code = "postprocess_failed"
    }

    class Http(val status: Int, message: String) : SttApiException(message) {
        override val tryAnotherServer = status >= 500
    }

    class MalformedResponse(cause: Throwable) : SttApiException(str(R.string.err_bad_response), cause)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromResponse(status: Int, body: String, retryAfter: String?): SttApiException {
            val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            val error = root?.get("error") as? JsonObject
            val rawText = ((root?.get("extv1") as? JsonObject)?.get("raw_text") as? JsonPrimitive)?.contentOrNull
            return fromError(status, error, retryAfter, rawText)
        }

        /** [status] is null for an `error` event inside a stream: the type decides then. */
        fun fromError(status: Int?, error: JsonObject?, retryAfter: String?, rawText: String? = null): SttApiException {
            val code = error?.text("code")
            val type = error?.text("type")
            val serverMessage = error?.text("message")
            val text = message(code, serverMessage, status)
            val httpStatus = status ?: when (type) {
                "rate_limit_error" -> 429
                "server_error" -> if (code == "postprocess_failed" || code == "transcription_failed") 502 else 503
                "permission_error" -> 403
                else -> 400
            }
            return when (httpStatus) {
                401 -> InvalidKey(text)
                403 -> Forbidden(text, code)
                404 -> NotFound(text, code)
                413 -> TooLarge(text)
                429 -> RateLimited(text, retryAfter?.trim()?.toIntOrNull(), code)
                400 -> BadRequest(text, code)
                502 -> if (code == "postprocess_failed") PostprocessFailed(text, rawText) else ServerUnavailable(text, code)
                500, 503, 504 -> ServerUnavailable(text, code)
                else -> Http(httpStatus, text)
            }
        }

        /** Localized text for known codes; the server's English message is kept as a detail. */
        internal fun message(code: String?, serverMessage: String?, status: Int?): String {
            val known = when (code) {
                "invalid_api_key" -> str(R.string.err_invalid_key)
                "model_switch_forbidden" -> str(R.string.err_model_switch_forbidden)
                "custom_prompt_forbidden" -> str(R.string.err_custom_prompt_forbidden)
                "model_not_found" -> str(R.string.err_model_not_found)
                "preset_not_found" -> str(R.string.err_preset_not_found)
                "preset_required" -> str(R.string.err_preset_required)
                "invalid_audio" -> str(R.string.err_invalid_audio)
                "file_too_large" -> str(R.string.err_file_too_large)
                "rate_limit_exceeded" -> str(R.string.err_rate_limit)
                "queue_full" -> str(R.string.err_queue_full)
                "queue_timeout" -> str(R.string.err_queue_timeout)
                "worker_unavailable" -> str(R.string.err_worker_unavailable)
                "no_model" -> str(R.string.err_no_model)
                "transcription_failed" -> str(R.string.err_transcription_failed)
                "postprocess_failed" -> str(R.string.err_postprocess_failed)
                else -> null
            }
            return when {
                known != null && serverMessage != null -> "$known ($serverMessage)"
                known != null -> known
                serverMessage != null -> serverMessage
                status != null -> "HTTP $status"
                else -> str(R.string.err_server)
            }
        }

        private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull
    }
}
