package ru.maximv.typovoice.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import ru.maximv.typovoice.AppContainer
import ru.maximv.typovoice.ui.collectSettings
import ru.maximv.typovoice.core.history.RetryStatus
import ru.maximv.typovoice.core.settings.AppSettings
import ru.maximv.typovoice.ui.GroupHeader
import ru.maximv.typovoice.ui.NavItem
import ru.maximv.typovoice.ui.Note
import ru.maximv.typovoice.ui.SectionScreen
import ru.maximv.typovoice.ui.SelectItem
import ru.maximv.typovoice.ui.SelectOption
import ru.maximv.typovoice.ui.SwitchItem
import ru.maximv.typovoice.plural
import ru.maximv.typovoice.R
import ru.maximv.typovoice.str

@Composable
fun ReliabilityScreen(container: AppContainer, onBack: () -> Unit, onOpenQueue: () -> Unit) {
    val s = container.settings.collectSettings()
    val entries by container.history.entries.collectAsState()
    val scope = rememberCoroutineScope()
    val queued = entries.count { it.retryStatus == RetryStatus.QUEUED && it.canRetry }

    SectionScreen(str(R.string.reliability_title), onBack) {
        item {
            GroupHeader(str(R.string.history_auto_retry))
            SwitchItem(
                str(R.string.reliability_retry),
                str(R.string.reliability_retry_summary),
                s.autoRetry,
            ) { scope.launch { container.settings.setAutoRetry(it) } }
            if (s.autoRetry) {
                SelectItem(
                    str(R.string.reliability_attempts),
                    listOf(1, 3, 5, 10).map { SelectOption(it, plural(R.plurals.attempts, it)) },
                    s.retryAttempts,
                    summary = str(R.string.reliability_backoff),
                ) { scope.launch { container.settings.setRetryAttempts(it) } }
            }
            NavItem(str(R.string.reliability_queue), if (queued == 0) str(R.string.reliability_queue_empty) else str(R.string.reliability_queued, queued), onClick = onOpenQueue)
        }
        item {
            GroupHeader(str(R.string.history_title))
            SwitchItem(
                str(R.string.reliability_history),
                str(R.string.reliability_history_summary),
                s.historyEnabled,
            ) { scope.launch { container.settings.setHistoryEnabled(it) } }
            if (!s.historyEnabled) Note(str(R.string.reliability_history_note))
        }
    }
}
