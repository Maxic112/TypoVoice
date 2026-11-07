package ru.maximv.typovoice.core.postprocess

import ru.maximv.typovoice.core.api.TranscriptionRequest
import ru.maximv.typovoice.core.servers.ServerConfig
import ru.maximv.typovoice.R
import ru.maximv.typovoice.str

/**
 * Quick post-processing choice that overrides the per-server preset for all dictations.
 * Values: null — as configured on the server; [NONE] — no LLM; [CUSTOM] — the server's own prompt;
 * otherwise a preset name from `GET /extv1/presets`.
 */
object Postprocess {
    const val NONE = TranscriptionRequest.PRESET_NONE

    /** Leading colon: can't clash with a preset name typed in the server panel. */
    const val CUSTOM = ":custom"

    /** Builds the request for [server] with the quick [choice] applied on top of its settings. */
    fun request(server: ServerConfig, choice: String?): TranscriptionRequest {
        val base = TranscriptionRequest(
            model = server.model,
            language = server.language,
            prompt = server.initialPrompt,
            vad = server.vad,
            stream = server.stream,
            preset = server.preset,
            customPrompt = server.customPrompt,
        )
        return when (choice) {
            // Without a custom prompt on this server CUSTOM falls back to its own setting.
            null, CUSTOM -> base
            NONE -> base.copy(preset = NONE, customPrompt = null)
            else -> base.copy(preset = choice, customPrompt = null)
        }
    }

    /** Whether the choice turns LLM post-processing on (for the bubble badge). */
    fun isActive(choice: String?) = choice != null && choice != NONE

    fun label(choice: String?): String = when (choice) {
        null -> str(R.string.pp_server_default)
        NONE -> str(R.string.pp_none)
        CUSTOM -> str(R.string.pp_custom)
        else -> choice
    }

    /** Menu entries: fixed ones plus presets the server lets the key choose. */
    fun choices(presets: List<String>, hasCustomPrompt: Boolean): List<String?> =
        listOf<String?>(null, NONE) +
            presets.filter { it != NONE && it != CUSTOM } +
            listOfNotNull(CUSTOM.takeIf { hasCustomPrompt })
}
