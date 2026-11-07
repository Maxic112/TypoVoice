package ru.maximv.typovoice.entry

import android.app.Activity
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Bundle
import ru.maximv.typovoice.R
import ru.maximv.typovoice.core.dictation.DictationSource

/**
 * Answers the system "pick a shortcut" request (ACTION_CREATE_SHORTCUT): launchers, Tasker,
 * routines and similar apps list "Dictate" among TypoVoice shortcuts and can launch it.
 */
class CreateShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action == Intent.ACTION_CREATE_SHORTCUT) {
            val shortcut = ShortcutInfo.Builder(this, "dictate-pinned")
                .setShortLabel(getString(R.string.shortcut_dictate))
                .setIcon(Icon.createWithResource(this, R.drawable.ic_launcher))
                .setIntent(DictationActivity.intent(this, DictationSource.SHORTCUT).setAction(Intent.ACTION_VIEW))
                .build()
            val result = getSystemService(ShortcutManager::class.java).createShortcutResultIntent(shortcut)
            setResult(RESULT_OK, result)
        } else {
            setResult(RESULT_CANCELED)
        }
        finish()
    }
}
