package ru.maximv.typovoice.entry

import android.content.Intent
import androidx.core.content.IntentCompat
import android.net.Uri
import android.os.Bundle
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.maximv.typovoice.TypoVoiceApp
import ru.maximv.typovoice.core.history.HistoryEntry
import ru.maximv.typovoice.core.history.historyNote
import ru.maximv.typovoice.ui.TypoVoiceTheme
import java.io.File
import ru.maximv.typovoice.R
import ru.maximv.typovoice.str

/** Receives an audio/video file via "Share", transcribes it and shows the text. */
class ShareActivity : ComponentActivity() {

    private sealed interface UiState {
        data object Working : UiState
        data class Done(val text: String, val server: String) : UiState
        data class Failed(val message: String) : UiState
    }

    private var state by mutableStateOf<UiState>(UiState.Working)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TypoVoiceTheme { Content() } }
        if (savedInstanceState == null) start()
    }

    private fun start() {
        val uri = sharedUri(intent)
        if (uri == null) {
            state = UiState.Failed(str(R.string.share_no_file))
            return
        }
        if (!TypoVoiceApp.container.appSwitch.isEnabled) {
            state = UiState.Failed(str(R.string.app_is_off))
            return
        }
        state = UiState.Working
        lifecycleScope.launch {
            var file: File? = null
            state = try {
                file = copyToCache(uri)
                val (result, server) = TypoVoiceApp.container.dictation.transcribe(file)
                if (result.text.isNotBlank()) {
                    TypoVoiceApp.container.dictation.record(
                        HistoryEntry(source = "SHARE", serverName = server, text = result.text, rawText = result.rawText, note = result.historyNote()),
                    )
                }
                if (result.text.isBlank()) UiState.Failed(str(R.string.vc_not_recognized)) else UiState.Done(result.text, server)
            } catch (e: Exception) {
                UiState.Failed(e.message ?: str(R.string.dict_recognition_error))
            } finally {
                file?.delete()
            }
        }
    }

    @Composable
    private fun Content() {
        when (val s = state) {
            UiState.Working -> AlertDialog(
                onDismissRequest = ::finish,
                title = { Text(str(R.string.common_recognizing)) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator()
                        Text(str(R.string.share_sent))
                    }
                },
                confirmButton = { TextButton(onClick = ::finish) { Text(str(R.string.common_cancel)) } },
            )
            is UiState.Done -> AlertDialog(
                onDismissRequest = ::finish,
                title = { Text(str(R.string.share_title)) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        SelectionContainer { Text(s.text, style = MaterialTheme.typography.bodyLarge) }
                        Text(s.server, Modifier, style = MaterialTheme.typography.labelSmall)
                    }
                },
                // All three actions in one row; compact padding so they fit without wrapping.
                confirmButton = {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = ::finish, contentPadding = compactButton) { Text(str(R.string.common_close)) }
                        TextButton(onClick = { shareText(s.text) }, contentPadding = compactButton) { Text(str(R.string.common_share)) }
                        TextButton(
                            onClick = {
                                TypoVoiceApp.container.delivery.copyToClipboard(s.text)
                                finish()
                            },
                            contentPadding = compactButton,
                        ) { Text(str(R.string.common_copy)) }
                    }
                },
            )
            is UiState.Failed -> AlertDialog(
                onDismissRequest = ::finish,
                title = { Text(str(R.string.common_failed)) },
                text = { Text(s.message) },
                confirmButton = { TextButton(onClick = ::start) { Text(str(R.string.common_retry)) } },
                dismissButton = { TextButton(onClick = ::finish) { Text(str(R.string.common_close)) } },
            )
        }
    }

    private val compactButton = PaddingValues(horizontal = 8.dp, vertical = 8.dp)

    private fun shareText(text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(send, null))
        finish()
    }

    private fun sharedUri(intent: Intent): Uri? = when (intent.action) {
        Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        Intent.ACTION_VIEW -> intent.data
        else -> null
    }

    private suspend fun copyToCache(uri: Uri): File = withContext(Dispatchers.IO) {
        val ext = contentResolver.getType(uri)?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: "bin"
        val dir = File(cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "shared-${System.currentTimeMillis()}.$ext")
        contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        file
    }
}
