package ru.maximv.typovoice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import ru.maximv.typovoice.core.api.SttApiClient
import ru.maximv.typovoice.core.dictation.DictationController
import ru.maximv.typovoice.core.history.HistoryRepository
import ru.maximv.typovoice.core.history.RetryQueue
import ru.maximv.typovoice.core.feedback.Feedback
import ru.maximv.typovoice.core.power.AppSwitch
import ru.maximv.typovoice.core.servers.ServerInfo
import ru.maximv.typovoice.core.servers.ServerPool
import ru.maximv.typovoice.core.settings.SettingsRepository
import ru.maximv.typovoice.core.update.UpdateManager
import ru.maximv.typovoice.delivery.TextDelivery

/** Manual dependency container: one instance per process, created in [TypoVoiceApp]. */
class AppContainer(private val context: Context) {
    val settings = SettingsRepository(context)
    val api = SttApiClient()
    val pool = ServerPool()
    val serverInfo = ServerInfo(api)
    val delivery = TextDelivery(context)
    val appSwitch = AppSwitch()
    val history = HistoryRepository(context.filesDir)
    val retryQueue = RetryQueue(context, history, settings)
    val feedback = Feedback(context, settings)
    val updates = UpdateManager(context)
    val dictation = DictationController(context, settings, api, pool, delivery, appSwitch, history, retryQueue)

    /** Refreshes the quick-menu presets from the first enabled server; keeps the old list on failure. */
    suspend fun refreshPresets() {
        val server = settings.current().servers.firstOrNull { it.enabled } ?: return
        runCatching { api.presets(server.endpoint) }
            .onSuccess { list -> settings.setCachedPresets(if (list.canChoose) list.presets.map { it.name } else emptyList()) }
    }

    /**
     * Restarts the whole app: drops a running dictation, kills the process and reopens the home
     * screen in a fresh one. All in-memory state (caches, the accessibility service, the bubble) starts
     * over; the system rebinds the accessibility service on its own. Saved settings are kept.
     */
    fun restartApp() {
        dictation.cancel()
        val intent = Intent.makeRestartActivityTask(ComponentName(context, MainActivity::class.java))
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}
