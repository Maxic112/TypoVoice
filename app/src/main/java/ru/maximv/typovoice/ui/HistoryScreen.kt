package ru.maximv.typovoice.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.maximv.typovoice.AppContainer
import ru.maximv.typovoice.core.history.HistoryEntry
import ru.maximv.typovoice.core.history.RetryStatus
import androidx.compose.material3.FilterChip
import ru.maximv.typovoice.core.settings.AppSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import ru.maximv.typovoice.R
import ru.maximv.typovoice.str

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(container: AppContainer, onBack: (() -> Unit)?, startWithQueue: Boolean = false) {
    val entries by container.history.entries.collectAsState()
    val settings = container.settings.collectSettings()
    var confirmClear by remember { mutableStateOf(false) }
    var onlyQueue by remember(startWithQueue) { mutableStateOf(startWithQueue) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(str(R.string.history_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = str(R.string.common_back)) }
                    }
                },
                actions = {
                    if (entries.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = str(R.string.history_clear))
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!settings.historyEnabled) {
                item { Text(str(R.string.history_saving_off), style = MaterialTheme.typography.bodySmall) }
            }
            val queue = entries.filter { it.retryStatus != null && it.canRetry }
            if (queue.isNotEmpty() || onlyQueue) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !onlyQueue, onClick = { onlyQueue = false }, label = { Text(str(R.string.history_all)) })
                        FilterChip(
                            selected = onlyQueue,
                            onClick = { onlyQueue = true },
                            label = { Text(str(R.string.history_queue_count, queue.size)) },
                        )
                    }
                }
            }
            val shown = if (onlyQueue) queue else entries
            if (shown.isEmpty()) {
                item { Text(if (onlyQueue) str(R.string.history_queue_empty) else str(R.string.common_empty), style = MaterialTheme.typography.bodyLarge) }
            }
            items(shown, key = { it.id }) { entry -> EntryCard(container, entry, settings.retryAttempts) }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(str(R.string.history_clear_confirm)) },
            text = { Text(str(R.string.history_clear_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { container.history.clear() }
                        confirmClear = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(str(R.string.common_clear)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(str(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun EntryCard(container: AppContainer, entry: HistoryEntry, maxAttempts: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var retrying by remember { mutableStateOf(false) }
    var showRaw by remember { mutableStateOf(false) }

    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Field(str(R.string.history_date), DATE_FORMAT.format(Date(entry.createdAt)))
            Field(str(R.string.history_source), sourceLabel(entry.source))
            entry.durationMs?.let { Field(str(R.string.history_duration), str(R.string.history_seconds_format).format(Locale.forLanguageTag("ru"), it / 1000.0)) }
            entry.serverName?.let { Field(str(R.string.common_server), it) }
            if (entry.text != null) {
                FieldLabel(str(R.string.common_result), Modifier.padding(top = 6.dp))
                SelectionContainer { Text(entry.text, style = MaterialTheme.typography.bodyLarge) }
                entry.note?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
                if (entry.rawText != null && entry.rawText != entry.text) {
                    TextButton(onClick = { showRaw = !showRaw }, contentPadding = PaddingValues(0.dp)) {
                        Text(if (showRaw) str(R.string.history_hide_raw) else str(R.string.history_show_raw))
                    }
                    if (showRaw) {
                        SelectionContainer { Text(entry.rawText, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            } else {
                retryStatusText(entry, maxAttempts)?.let { Field(str(R.string.history_auto_retry), it) }
                FieldLabel(str(R.string.common_result), Modifier.padding(top = 6.dp))
                Text(str(R.string.history_not_recognized, entry.error), color = MaterialTheme.colorScheme.error)
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (entry.text != null) {
                    TextButton(onClick = { container.delivery.copyToClipboard(entry.text) }) { Text(str(R.string.common_copy)) }
                    TextButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, entry.text)
                        context.startActivity(Intent.createChooser(send, null))
                    }) { Text(str(R.string.common_share)) }
                }
                if (entry.canRetry) {
                    if (retrying) {
                        CircularProgressIndicator(Modifier.size(20.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(onClick = {
                            retrying = true
                            scope.launch {
                                // A manual retry takes the entry out of the background queue first.
                                if (entry.retryStatus != null) container.retryQueue.remove(entry)
                                container.dictation.retry(container.history.get(entry.id) ?: entry)
                                retrying = false
                            }
                        }) { Text(str(R.string.common_retry)) }
                    }
                    if (entry.retryStatus == RetryStatus.QUEUED && !retrying) {
                        TextButton(onClick = { scope.launch { container.retryQueue.remove(entry) } }) { Text(str(R.string.history_dequeue)) }
                    }
                }
                TextButton(
                    onClick = { scope.launch { container.history.delete(entry.id) } },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(str(R.string.common_delete)) }
            }
        }
    }
}

/** One "label: value" line of the entry card; labels share a column so values line up. */
@Composable
private fun Field(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        FieldLabel(label, Modifier.width(128.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun retryStatusText(entry: HistoryEntry, maxAttempts: Int): String? = when (entry.retryStatus) {
    RetryStatus.QUEUED -> buildString {
        append(str(R.string.history_retry_queued, entry.retryAttempts, maxAttempts))
        entry.nextRetryAt?.let { append(str(R.string.history_retry_next, TIME_FORMAT.format(Date(it)))) }
    }
    RetryStatus.EXHAUSTED -> if (entry.retryAttempts > 0) {
        str(R.string.history_retry_exhausted, entry.retryAttempts, maxAttempts)
    } else {
        str(R.string.history_retry_pointless)
    }
    null -> null
}

private fun sourceLabel(source: String) = when (source) {
    "APP" -> str(R.string.source_app)
    "OVERLAY" -> str(R.string.entry_bubble)
    "TILE" -> str(R.string.entry_tile)
    "VOLUME_KEY" -> str(R.string.source_volume)
    "WIDGET" -> str(R.string.source_widget)
    "SHORTCUT" -> str(R.string.source_shortcut)
    "SIDE_KEY" -> str(R.string.entry_launcher)
    "ASSISTANT" -> str(R.string.source_assistant)
    "SHARE" -> str(R.string.source_share)
    "SYSTEM" -> str(R.string.source_system)
    else -> source.lowercase()
}

internal val DATE_FORMAT = SimpleDateFormat("d MMM, HH:mm", Locale.forLanguageTag("ru"))
private val TIME_FORMAT = SimpleDateFormat("HH:mm", Locale.forLanguageTag("ru"))
