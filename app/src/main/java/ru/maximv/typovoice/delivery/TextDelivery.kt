package ru.maximv.typovoice.delivery

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import ru.maximv.typovoice.R
import ru.maximv.typovoice.a11y.TypoVoiceAccessibilityService
import ru.maximv.typovoice.core.dictation.DictationSource

/** Puts recognized text where the user expects it: the focused field, or the clipboard as a fallback. */
class TextDelivery(private val context: Context) {

    /** SHOWN: the app's own test button only displays the text. */
    enum class Outcome { INSERTED, CLIPBOARD, SHOWN, EMPTY }

    fun deliver(text: String, source: DictationSource): Outcome {
        if (text.isBlank()) return Outcome.EMPTY
        if (source == DictationSource.APP) return Outcome.SHOWN
        val insertIntoField = source != DictationSource.APP && source != DictationSource.WIDGET
        if (insertIntoField && TypoVoiceAccessibilityService.instance?.insertText(text) == true) {
            return Outcome.INSERTED
        }
        copyToClipboard(text)
        Toast.makeText(context, R.string.toast_copied, Toast.LENGTH_SHORT).show()
        return Outcome.CLIPBOARD
    }

    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("TypoVoice", text))
    }
}
