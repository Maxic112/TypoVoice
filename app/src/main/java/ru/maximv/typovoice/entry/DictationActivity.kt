package ru.maximv.typovoice.entry

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.maximv.typovoice.TypoVoiceApp
import ru.maximv.typovoice.core.dictation.DictationSource
import ru.maximv.typovoice.core.dictation.DictationState

/**
 * Invisible trampoline that toggles dictation: used by the side key (via the "Dictation" launcher
 * alias), the app shortcut and the widget. Being a (briefly) visible activity is what allows
 * starting the microphone foreground service; it closes as soon as recording has started,
 * so the app underneath gets its focus back and receives the text.
 */
class DictationActivity : Activity() {
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private var job: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val controller = TypoVoiceApp.container.dictation
        val wasBusy = controller.state.value.isBusy
        controller.toggle(sourceOf(intent))
        if (wasBusy) {
            finish()
            return
        }
        job = scope.launch {
            // Stay until the microphone is actually open (or the start failed), but never hang around.
            val deadline = System.currentTimeMillis() + MAX_WAIT_MS
            while (controller.state.value is DictationState.Starting && System.currentTimeMillis() < deadline) {
                delay(50)
            }
            finish()
        }
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    private fun sourceOf(intent: Intent): DictationSource =
        intent.getStringExtra(EXTRA_SOURCE)?.let { runCatching { DictationSource.valueOf(it) }.getOrNull() }
            ?: DictationSource.SIDE_KEY

    companion object {
        const val EXTRA_SOURCE = "source"
        private const val MAX_WAIT_MS = 2_000L
        private const val ALIAS = "ru.maximv.typovoice.entry.DictationLauncher"

        fun intent(context: Context, source: DictationSource) = Intent(context, DictationActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            .putExtra(EXTRA_SOURCE, source.name)

        /** The separate "Dictation" app icon, needed to bind dictation to the side key. Optional. */
        fun setLauncherIconEnabled(context: Context, enabled: Boolean) {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context.packageName, ALIAS),
                if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
