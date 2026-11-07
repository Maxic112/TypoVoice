package ru.maximv.typovoice

import android.app.Application
import android.content.res.Configuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import ru.maximv.typovoice.core.dictation.DictationSource
import ru.maximv.typovoice.entry.DictationActivity
import ru.maximv.typovoice.entry.DictationWidget
import ru.maximv.typovoice.entry.VoiceInputActivity
import ru.maximv.typovoice.tile.DictationTileService

class TypoVoiceApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        Strings.context = this
        container = AppContainer(this)
        scope.launch {
            container.settings.seedDevServerIfEmpty()
        }
        scope.launch { container.history.load() }
        container.feedback.observe(scope, container.dictation.state)
        container.updates.schedule()
        scope.launch {
            container.settings.settings.map { it.enabled }.distinctUntilChanged().collect { enabled ->
                container.appSwitch.apply(enabled)
                if (!enabled) container.dictation.cancel()
                DictationTileService.refresh(this@TypoVoiceApp)
            }
        }
        scope.launch {
            container.settings.settings.map { it.tileEnabled }.distinctUntilChanged().collect {
                DictationTileService.setEnabled(this@TypoVoiceApp, it)
            }
        }
        scope.launch {
            container.dictation.state.collect {
                DictationTileService.refresh(this@TypoVoiceApp)
                DictationWidget.refresh(this@TypoVoiceApp, it)
            }
        }
        scope.launch {
            container.settings.settings.map { it.launcherIcon }.distinctUntilChanged().collect {
                DictationActivity.setLauncherIconEnabled(this@TypoVoiceApp, it)
            }
        }
        scope.launch {
            container.settings.settings.map { it.voiceInputPopup }.distinctUntilChanged().collect {
                VoiceInputActivity.setEnabled(this@TypoVoiceApp, it)
            }
        }
        publishShortcut()
    }

    /** The shortcut label is text too: republish it in the new language. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        publishShortcut()
    }

    /** Long-press on the app icon → "Dictate"; can also be pinned to the home screen. */
    private fun publishShortcut() {
        val shortcut = ShortcutInfo.Builder(this, "dictate")
            .setShortLabel(getString(R.string.shortcut_dictate))
            .setIcon(Icon.createWithResource(this, R.drawable.ic_launcher))
            .setIntent(DictationActivity.intent(this, DictationSource.SHORTCUT).setAction(Intent.ACTION_VIEW))
            .build()
        getSystemService(ShortcutManager::class.java).dynamicShortcuts = listOf(shortcut)
    }

    companion object {
        lateinit var container: AppContainer
            private set
    }
}
