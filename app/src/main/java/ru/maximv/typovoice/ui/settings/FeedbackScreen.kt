package ru.maximv.typovoice.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import ru.maximv.typovoice.AppContainer
import ru.maximv.typovoice.ui.collectSettings
import ru.maximv.typovoice.core.settings.AppSettings
import ru.maximv.typovoice.ui.SectionScreen
import ru.maximv.typovoice.ui.SwitchItem
import ru.maximv.typovoice.R
import ru.maximv.typovoice.str

@Composable
fun FeedbackScreen(container: AppContainer, onBack: () -> Unit) {
    val s = container.settings.collectSettings()
    val scope = rememberCoroutineScope()

    SectionScreen(str(R.string.feedback_title), onBack) {
        item {
            SwitchItem(str(R.string.feedback_vibration), str(R.string.feedback_vibration_summary), s.vibrationFeedback) {
                scope.launch { container.settings.setVibrationFeedback(it) }
            }
            SwitchItem(str(R.string.feedback_sound), str(R.string.feedback_sound_summary), s.soundFeedback) {
                scope.launch { container.settings.setSoundFeedback(it) }
            }
        }
    }
}
