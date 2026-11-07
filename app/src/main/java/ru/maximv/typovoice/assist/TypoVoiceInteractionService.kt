package ru.maximv.typovoice.assist

import android.service.voice.VoiceInteractionService

/**
 * Makes TypoVoice selectable as the "digital assistant". Nothing happens until the user picks it;
 * then Android also makes [ru.maximv.typovoice.entry.TypoVoiceRecognitionService] the default speech
 * recognizer, and the assistant gesture starts dictation (see [TypoVoiceSession]).
 */
class TypoVoiceInteractionService : VoiceInteractionService()
