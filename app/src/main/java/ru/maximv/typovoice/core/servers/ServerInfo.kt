package ru.maximv.typovoice.core.servers

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.maximv.typovoice.core.api.SttApiClient
import ru.maximv.typovoice.core.api.SttModel

/** Server state as last seen by `/extv1/health`. */
sealed interface ServerHealth {
    data object Online : ServerHealth
    data class Degraded(val worker: String) : ServerHealth
    data object Offline : ServerHealth
}

/** Models a server offers to the key; [failed] = the last refresh didn't get through (the list is the previous one). */
data class ServerModels(val models: List<SttModel>, val failed: Boolean = false)

/**
 * Process-wide cache of server health and model lists. Screens show what is known at once and
 * refresh in the background, so nothing flickers back to "checking" when a screen is re-created.
 */
class ServerInfo(private val api: SttApiClient) {
    private val _health = MutableStateFlow<Map<String, ServerHealth>>(emptyMap())

    /** By base URL. */
    val health: StateFlow<Map<String, ServerHealth>> = _health.asStateFlow()

    private val _models = MutableStateFlow<Map<String, ServerModels>>(emptyMap())

    /** By server id. */
    val models: StateFlow<Map<String, ServerModels>> = _models.asStateFlow()

    suspend fun refreshHealth(baseUrl: String) {
        val health = runCatching { api.health(baseUrl) }.fold(
            onSuccess = { if (it.status == "ok") ServerHealth.Online else ServerHealth.Degraded(it.worker) },
            onFailure = { ServerHealth.Offline },
        )
        _health.update { it + (baseUrl to health) }
    }

    suspend fun refreshModels(server: ServerConfig) {
        if (server.apiKey.isBlank()) return
        runCatching { api.models(server.endpoint) }
            .onSuccess { list -> _models.update { it + (server.id to ServerModels(list)) } }
            .onFailure {
                _models.update { cache ->
                    cache + (server.id to (cache[server.id]?.copy(failed = true) ?: ServerModels(emptyList(), failed = true)))
                }
            }
    }
}
