package ru.maximv.typovoice.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import ru.maximv.typovoice.AppContainer
import ru.maximv.typovoice.ui.collectSettings
import ru.maximv.typovoice.core.settings.AppSettings
import ru.maximv.typovoice.ui.GroupHeader
import ru.maximv.typovoice.ui.InfoItem
import ru.maximv.typovoice.ui.Note
import ru.maximv.typovoice.ui.Permissions
import ru.maximv.typovoice.ui.RecognizerTestButton
import ru.maximv.typovoice.ui.SectionScreen
import ru.maximv.typovoice.ui.SwitchItem
import ru.maximv.typovoice.R
import ru.maximv.typovoice.str

@Composable
fun AssistantScreen(container: AppContainer, onBack: () -> Unit) {
    val s = container.settings.collectSettings()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val isAssistant = refresh.let { Permissions.isAssistant(context) }
    val isDefault = refresh.let { Permissions.isDefaultVoiceInput(context) }

    SectionScreen(str(R.string.assistant_title), onBack) {
        item {
            InfoItem(
                when {
                    isAssistant -> str(R.string.assistant_is_assistant)
                    isDefault == true -> str(R.string.assistant_is_default)
                    else -> str(R.string.assistant_other)
                },
                if (isAssistant) str(R.string.assistant_summary) else null,
            )
            FilledTonalButton(
                onClick = { context.startActivity(Permissions.voiceInputSettingsIntent(context)) },
                modifier = Modifier.padding(start = 16.dp),
            ) { Text(str(R.string.assistant_choose)) }
            Note(
                str(R.string.assistant_note),
            )
        }
        item {
            GroupHeader(str(R.string.assistant_test))
            Column(Modifier.padding(horizontal = 16.dp)) { RecognizerTestButton() }
            Note(str(R.string.assistant_test_note))
        }
        item {
            GroupHeader(str(R.string.common_advanced))
            SwitchItem(
                str(R.string.assistant_popup),
                str(R.string.assistant_popup_summary),
                s.voiceInputPopup,
            ) { scope.launch { container.settings.setVoiceInputPopup(it) } }
        }
    }
}
