package ru.maximv.typovoice.core.servers

import kotlinx.serialization.Serializable
import ru.maximv.typovoice.core.api.SttApiException
import ru.maximv.typovoice.core.api.ServerEndpoint
import ru.maximv.typovoice.core.api.TranscriptionRequest
import ru.maximv.typovoice.R
import ru.maximv.typovoice.str

@Serializable
data class ServerConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val enabled: Boolean = true,
    /** STT model id from `GET /models`; null = the server's current model. */
    val model: String? = null,
    /** LLM preset (`extv1.preset`): name, [TranscriptionRequest.PRESET_NONE] or null = key default. */
    val preset: String? = null,
    /** Own LLM prompt (`extv1.prompt`); `{text}` marks where the transcription goes. Needs a preset. */
    val customPrompt: String? = null,
    /** ISO-639-1 language; null = auto-detect. */
    val language: String? = null,
    /** Initial prompt for the recognizer: names, terms, style. */
    val initialPrompt: String? = null,
    /** Cut silence on the server (`chunking_strategy=auto`). */
    val vad: Boolean = false,
    /** Streamed response: show text while the server recognizes. */
    val stream: Boolean = false,
) {
    val endpoint get() = ServerEndpoint(baseUrl, apiKey)

    companion object {
        const val TEXT_PLACEHOLDER = "{text}"
    }
}

@Serializable
enum class SelectionPolicy {
    /** Always the server chosen by the user, no failover. */
    FIXED,

    /** Servers in list order; the first one that answers wins. */
    FAILOVER,

    /** Starts from the server that succeeded last time, then the rest in list order. */
    LAST_SUCCESSFUL,

    /** Next server in a circle on every request, failing over along the circle. */
    ROUND_ROBIN,

    /** Random order on every request. */
    RANDOM,
}

/** Mutable bookkeeping the pool needs between requests. */
data class PoolState(
    val lastSuccessfulId: String? = null,
    val roundRobinCursor: Int = 0,
)

class AllServersFailedException(val failures: List<Pair<ServerConfig, Throwable>>) :
    Exception(failures.lastOrNull()?.second?.message ?: str(R.string.no_enabled_servers)) {
    /** Whether trying again later may help (network, overload, server errors), unlike bad audio. */
    val retryable get() = failures.any { (_, e) ->
        e is SttApiException.Network || e is SttApiException.RateLimited ||
            e is SttApiException.ServerUnavailable || e is SttApiException.PostprocessFailed ||
            e is SttApiException.MalformedResponse || (e is SttApiException.Http && e.status >= 500)
    }
}

/** Orders servers according to a [SelectionPolicy] and fails over between them. */
class ServerPool(
    private val random: kotlin.random.Random = kotlin.random.Random.Default,
    private val sleep: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) {

    fun order(
        servers: List<ServerConfig>,
        policy: SelectionPolicy,
        fixedId: String?,
        state: PoolState,
    ): List<ServerConfig> {
        val enabled = servers.filter { it.enabled }
        if (enabled.isEmpty()) return emptyList()
        return when (policy) {
            SelectionPolicy.FIXED -> listOfNotNull(enabled.find { it.id == fixedId } ?: enabled.first())
            SelectionPolicy.FAILOVER -> enabled
            SelectionPolicy.LAST_SUCCESSFUL -> {
                val last = enabled.find { it.id == state.lastSuccessfulId }
                if (last == null) enabled else listOf(last) + (enabled - last)
            }
            SelectionPolicy.ROUND_ROBIN -> {
                val start = state.roundRobinCursor.mod(enabled.size)
                enabled.drop(start) + enabled.take(start)
            }
            SelectionPolicy.RANDOM -> enabled.shuffled(random)
        }
    }

    /**
     * Runs [block] against servers in policy order until one succeeds.
     * Errors that another server can't fix (bad audio, too large) stop the loop immediately.
     */
    suspend fun <T> execute(
        servers: List<ServerConfig>,
        policy: SelectionPolicy,
        fixedId: String?,
        state: PoolState,
        quickRetry: Boolean = false,
        block: suspend (ServerConfig) -> T,
    ): Pair<T, PoolState> {
        val ordered = order(servers, policy, fixedId, state)
        val failures = mutableListOf<Pair<ServerConfig, Throwable>>()
        for (server in ordered) {
            var retried = false
            while (true) {
                try {
                    val result = block(server)
                    return result to nextState(servers, policy, state, server)
                } catch (e: SttApiException) {
                    // A transient overload is worth one more try on the same server before moving on.
                    val wait = if (quickRetry && !retried) quickRetryDelay(e) else null
                    if (wait != null) {
                        retried = true
                        sleep(wait)
                        continue
                    }
                    failures += server to e
                    break
                }
            }
            if (!(failures.last().second as SttApiException).tryAnotherServer) break
        }
        throw AllServersFailedException(failures)
    }

    private fun quickRetryDelay(e: SttApiException): Long? = when (e) {
        is SttApiException.RateLimited -> (e.retryAfterSeconds ?: 1).takeIf { it <= MAX_QUICK_WAIT_S }?.times(1000L)
        is SttApiException.PostprocessFailed -> POSTPROCESS_RETRY_MS
        else -> null
    }

    private fun nextState(
        servers: List<ServerConfig>,
        policy: SelectionPolicy,
        state: PoolState,
        used: ServerConfig,
    ): PoolState {
        val enabled = servers.filter { it.enabled }
        val cursor = if (policy == SelectionPolicy.ROUND_ROBIN) enabled.indexOf(used) + 1 else state.roundRobinCursor
        return PoolState(lastSuccessfulId = used.id, roundRobinCursor = cursor)
    }

    private companion object {
        const val MAX_QUICK_WAIT_S = 10
        const val POSTPROCESS_RETRY_MS = 2_000L
    }
}
