package ru.maximv.typovoice.entry

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ru.maximv.typovoice.TypoVoiceApp
import ru.maximv.typovoice.core.recognition.VoiceCapture
import ru.maximv.typovoice.ui.TypoVoiceTheme
import ru.maximv.typovoice.R
import ru.maximv.typovoice.str

/**
 * Handles RecognizerIntent.ACTION_RECOGNIZE_SPEECH ("Speak…" popup) with the speech server.
 * Optional: enabled from settings, because Android asks which app to use when several can handle it.
 */
class VoiceInputActivity : ComponentActivity() {
    private enum class Phase { LISTENING, TRANSCRIBING }

    private var phase by mutableStateOf(Phase.LISTENING)
    private var heard by mutableStateOf(false)
    private var level by mutableFloatStateOf(0f)
    private var error by mutableStateOf<String?>(null)
    private var capture: VoiceCapture? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TypoVoiceTheme { Popup() } }
        if (!TypoVoiceApp.container.appSwitch.isEnabled) {
            error = str(R.string.app_is_off)
            return
        }
        val listener = object : VoiceCapture.Listener {
            override fun onReady() = Unit
            override fun onSpeechStarted() {
                heard = true
            }
            override fun onLevel(level: Float) {
                this@VoiceInputActivity.level = level
            }
            override fun onSpeechEnded() {
                phase = Phase.TRANSCRIBING
            }
            override fun onResult(text: String) {
                setResult(
                    RESULT_OK,
                    Intent()
                        .putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, arrayListOf(text))
                        .putExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES, floatArrayOf(1f)),
                )
                finish()
            }
            override fun onError(code: Int, message: String) {
                error = message
            }
        }
        lifecycleScope.launch {
            val silence = intent.getLongExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 0L)
                .takeIf { it > 0 } ?: TypoVoiceApp.container.settings.current().recognitionSilenceMs
            capture = VoiceCapture(this@VoiceInputActivity, lifecycleScope, listener).also { it.start(silence) }
        }
    }

    override fun onDestroy() {
        capture?.cancel()
        super.onDestroy()
    }

    private fun close() {
        capture?.cancel()
        setResult(RESULT_CANCELED)
        finish()
    }

    @androidx.compose.runtime.Composable
    private fun Popup() {
        val prompt = intent.getStringExtra(RecognizerIntent.EXTRA_PROMPT)
        AlertDialog(
            onDismissRequest = ::close,
            title = { Text(error?.let { str(R.string.common_failed) } ?: prompt ?: str(R.string.vi_speak_now)) },
            text = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when {
                        error != null -> Text(error!!)
                        phase == Phase.TRANSCRIBING -> {
                            CircularProgressIndicator()
                            Text(str(R.string.common_recognizing))
                        }
                        else -> {
                            val scale by animateFloatAsState(1f + level * 0.6f, label = "level")
                            Box(
                                Modifier.size(72.dp).scale(scale).background(Color(0xFFE53935), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White)
                            }
                            Text(if (heard) str(R.string.vi_listening) else str(R.string.vi_speak), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                if (error == null && phase == Phase.LISTENING) {
                    TextButton(onClick = { capture?.stop() }) { Text(str(R.string.common_done)) }
                }
            },
            dismissButton = { TextButton(onClick = ::close) { Text(if (error != null) str(R.string.common_close) else str(R.string.common_cancel)) } },
        )
    }

    companion object {
        fun setEnabled(context: Context, enabled: Boolean) {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, VoiceInputActivity::class.java),
                if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
