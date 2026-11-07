package ru.maximv.typovoice.tile

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.maximv.typovoice.TypoVoiceApp
import ru.maximv.typovoice.R
import ru.maximv.typovoice.core.dictation.DictationSource
import ru.maximv.typovoice.core.dictation.DictationState

/** Quick Settings tile: tap to start dictation, tap again to stop and insert into the focused field. */
class DictationTileService : TileService() {
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private var job: Job? = null

    // The system calls these only for a tile placed in Quick Settings: remember it for the settings screen.
    override fun onTileAdded() = markInPanel(true)

    override fun onTileRemoved() = markInPanel(false)

    private fun markInPanel(inPanel: Boolean) {
        val settings = TypoVoiceApp.container.settings
        scope.launch { if (settings.current().tileInPanel != inPanel) settings.setTileInPanel(inPanel) }
    }

    override fun onStartListening() {
        markInPanel(true)
        job = scope.launch {
            TypoVoiceApp.container.dictation.state.collect(::render)
        }
    }

    override fun onStopListening() {
        job?.cancel()
        job = null
    }

    override fun onClick() {
        if (!TypoVoiceApp.container.appSwitch.isEnabled) return
        TypoVoiceApp.container.dictation.toggle(DictationSource.TILE)
    }

    private fun render(state: DictationState) {
        val tile = qsTile ?: return
        if (!TypoVoiceApp.container.appSwitch.isEnabled) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.subtitle = getString(R.string.tile_off)
            tile.updateTile()
            return
        }
        tile.state = if (state.isBusy) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = when (state) {
            is DictationState.Starting, is DictationState.Recording -> getString(R.string.tile_recording)
            is DictationState.Transcribing -> getString(R.string.tile_transcribing)
            is DictationState.Failed -> getString(R.string.tile_failed)
            else -> null
        }
        tile.updateTile()
    }

    companion object {
        fun component(context: Context) = ComponentName(context, DictationTileService::class.java)

        /** The tile is optional: disabling the component removes it from the Quick Settings editor. */
        fun setEnabled(context: Context, enabled: Boolean) {
            context.packageManager.setComponentEnabledSetting(
                component(context),
                if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }

        fun refresh(context: Context) = requestListeningState(context, component(context))
    }
}
