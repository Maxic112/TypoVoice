package ru.maximv.typovoice.assist

import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import ru.maximv.typovoice.TypoVoiceApp
import ru.maximv.typovoice.core.dictation.DictationSource
import ru.maximv.typovoice.entry.DictationActivity

class TypoVoiceSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = TypoVoiceSession(this)
}

/**
 * The assistant gesture toggles dictation. No UI and no screen content is requested:
 * the session only launches the dictation trampoline and hides itself.
 */
class TypoVoiceSession(context: Context) : VoiceInteractionSession(context) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        if (TypoVoiceApp.container.appSwitch.isEnabled) {
            startAssistantActivity(DictationActivity.intent(context, DictationSource.ASSISTANT))
        }
        hide()
    }
}
