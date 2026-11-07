package ru.maximv.typovoice.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import ru.maximv.typovoice.AppContainer
import ru.maximv.typovoice.ui.collectSettings
import ru.maximv.typovoice.core.settings.AppSettings
import ru.maximv.typovoice.core.settings.AutoStopMode
import ru.maximv.typovoice.ui.GroupHeader
import ru.maximv.typovoice.ui.Note
import ru.maximv.typovoice.ui.RadioItem
import ru.maximv.typovoice.ui.SectionScreen
import ru.maximv.typovoice.ui.SelectItem
import ru.maximv.typovoice.ui.SelectOption
import ru.maximv.typovoice.plural
import ru.maximv.typovoice.R
import ru.maximv.typovoice.str

@Composable
fun RecordingScreen(container: AppContainer, onBack: () -> Unit) {
    val s = container.settings.collectSettings()
    val scope = rememberCoroutineScope()

    SectionScreen(str(R.string.recording_title), onBack) {
        item {
            GroupHeader(str(R.string.recording_limit))
            SelectItem(
                str(R.string.recording_limit_select),
                (RECORDING_LIMITS.map { SelectOption(it, minutesLabel(it)) } + SelectOption(0, str(R.string.recording_no_limit))),
                s.maxRecordingMinutes,
                summary = str(R.string.recording_limit_summary),
            ) { scope.launch { container.settings.setMaxRecordingMinutes(it) } }
        }
        item {
            GroupHeader(str(R.string.recording_autostop))
            val set: (AutoStopMode) -> Unit = { scope.launch { container.settings.setAutoStop(it) } }
            RadioItem(str(R.string.autostop_assistant), null, s.autoStop == AutoStopMode.ASSISTANT_ONLY) { set(AutoStopMode.ASSISTANT_ONLY) }
            RadioItem(str(R.string.autostop_all), str(R.string.autostop_all_detail), s.autoStop == AutoStopMode.ALL_TAPS) {
                set(AutoStopMode.ALL_TAPS)
            }
            RadioItem(str(R.string.common_off_f), str(R.string.autostop_off_detail), s.autoStop == AutoStopMode.OFF) { set(AutoStopMode.OFF) }
            Note(str(R.string.recording_hold_note))
        }
        item {
            GroupHeader(str(R.string.recording_pause))
            SelectItem(
                str(R.string.recording_pause_select),
                listOf(1_000L, 1_500L, 2_000L, 3_000L).map { SelectOption(it, seconds(it)) },
                s.recognitionSilenceMs,
                summary = str(R.string.recording_pause_note),
            ) { scope.launch { container.settings.setRecognitionSilence(it) } }
        }
    }
}

private val RECORDING_LIMITS = listOf(1, 5, 10, 15, 20, 30, 45, 60, 90, 120)

/** "5 minutes", "60 minutes (1 hour)" in the current language. */
internal fun minutesLabel(minutes: Int): String {
    val hours = if (minutes >= 60 && minutes % 60 == 0) " (" + plural(R.plurals.hours, minutes / 60) + ")" else ""
    return plural(R.plurals.minutes, minutes) + hours
}
