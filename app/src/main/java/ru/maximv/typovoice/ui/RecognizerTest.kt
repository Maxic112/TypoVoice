package ru.maximv.typovoice.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.maximv.typovoice.entry.TypoVoiceRecognitionService
import ru.maximv.typovoice.R
import ru.maximv.typovoice.str

/**
 * Exercises TypoVoiceRecognitionService exactly like a third-party app would (through SpeechRecognizer),
 * without making it the system default.
 */
@Composable
fun RecognizerTestButton() {
    val context = LocalContext.current
    var status by remember { mutableStateOf<String?>(null) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    DisposableEffect(Unit) { onDispose { recognizer?.destroy() } }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(onClick = {
            if (recognizer != null) {
                recognizer?.stopListening()
                return@OutlinedButton
            }
            val r = create(context) { update, done ->
                status = update
                if (done) {
                    recognizer?.destroy()
                    recognizer = null
                }
            }
            recognizer = r
            r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH))
            status = str(R.string.rt_connecting)
        }) { Text(if (recognizer == null) str(R.string.rt_test_service) else str(R.string.common_done)) }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

private fun create(context: Context, onUpdate: (String, Boolean) -> Unit): SpeechRecognizer {
    val component = ComponentName(context, TypoVoiceRecognitionService::class.java)
    return SpeechRecognizer.createSpeechRecognizer(context, component).apply {
        setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onUpdate(str(R.string.rt_speak), false)
            override fun onBeginningOfSpeech() = onUpdate(str(R.string.rt_listening), false)
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = onUpdate(str(R.string.common_recognizing), false)
            override fun onError(error: Int) = onUpdate(str(R.string.rt_error, errorName(error)), true)
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                onUpdate(str(R.string.rt_result, text), true)
            }
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
    }
}

private fun errorName(code: Int) = when (code) {
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> str(R.string.rt_err_server_unreachable)
    SpeechRecognizer.ERROR_SERVER -> str(R.string.rt_err_server)
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> str(R.string.rt_err_no_speech)
    SpeechRecognizer.ERROR_NO_MATCH -> str(R.string.rt_err_no_match)
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> str(R.string.rt_err_busy)
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> str(R.string.rt_err_permission)
    SpeechRecognizer.ERROR_AUDIO -> str(R.string.rt_err_audio)
    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> str(R.string.rt_err_too_many)
    else -> str(R.string.rt_err_code, code)
}
