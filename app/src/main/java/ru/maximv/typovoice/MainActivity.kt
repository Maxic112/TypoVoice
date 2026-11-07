package ru.maximv.typovoice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import ru.maximv.typovoice.ui.TypoVoiceTheme
import ru.maximv.typovoice.ui.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openHistory = intent.getBooleanExtra(EXTRA_OPEN_HISTORY, false)
        setContent {
            TypoVoiceTheme {
                AppRoot(TypoVoiceApp.container, openHistory) { openHistory = false }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_HISTORY, false)) openHistory = true
    }

    private var openHistory by androidx.compose.runtime.mutableStateOf(false)

    companion object {
        const val EXTRA_OPEN_HISTORY = "open_history"
    }
}
