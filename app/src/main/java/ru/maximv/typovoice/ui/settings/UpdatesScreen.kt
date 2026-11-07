package ru.maximv.typovoice.ui.settings

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import ru.maximv.typovoice.AppContainer
import ru.maximv.typovoice.R
import ru.maximv.typovoice.core.update.DownloadState
import ru.maximv.typovoice.core.update.Release
import ru.maximv.typovoice.core.update.UpdateManager
import ru.maximv.typovoice.str
import ru.maximv.typovoice.ui.GroupHeader
import ru.maximv.typovoice.ui.InfoItem
import ru.maximv.typovoice.ui.Note
import ru.maximv.typovoice.ui.SectionScreen
import ru.maximv.typovoice.ui.SwitchItem

/** Installed version, the newest GitHub release and the way to install it. */
@Composable
fun UpdatesScreen(container: AppContainer, onBack: () -> Unit) {
    val updates = container.updates
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by updates.state.collectAsState()
    val download by updates.download.collectAsState()
    var checking by remember { mutableStateOf(false) }
    var checkError by remember { mutableStateOf<String?>(null) }
    // Set when the user went to grant the install permission; the download starts on return.
    var waitingForPermission by rememberSaveable { mutableStateOf(false) }

    fun install(release: Release) {
        if (updates.canInstall()) {
            scope.launch { updates.downloadAndInstall(release) }
        } else {
            waitingForPermission = true
            context.startActivity(updates.installPermissionIntent())
        }
    }

    LifecycleResumeEffect(Unit) {
        val release = state.available
        if (waitingForPermission && release != null && updates.canInstall()) {
            waitingForPermission = false
            scope.launch { updates.downloadAndInstall(release) }
        }
        onPauseOrDispose { }
    }

    SectionScreen(str(R.string.update_title), onBack) {
        item {
            InfoItem(str(R.string.update_installed), UpdateManager.currentVersion)
            if (!updates.configured) {
                Note(str(R.string.update_not_configured_note))
                return@item
            }
        }
        if (!updates.configured) return@SectionScreen

        val available = state.available
        if (available != null) {
            item {
                GroupHeader(str(R.string.update_available))
                InfoItem(
                    str(R.string.update_version, available.version),
                    available.apkSize.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) },
                )
                if (available.notes.isNotBlank()) {
                    Text(
                        available.notes,
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                InstallControls(download, onInstall = { install(available) })
                TextButton(
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(available.pageUrl))) },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) { Text(str(R.string.update_release_page)) }
            }
        }

        item {
            GroupHeader(str(R.string.update_check_header))
            val status = when {
                state.checkedAt == 0L -> str(R.string.update_never_checked)
                available == null -> str(R.string.update_up_to_date)
                else -> null
            }
            val checkedAt = state.checkedAt.takeIf { it > 0 }?.let {
                str(R.string.update_checked_at, DateUtils.getRelativeTimeSpanString(it).toString())
            }
            InfoItem(status ?: str(R.string.update_available), checkedAt)
            checkError?.let {
                Text(
                    it,
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            OutlinedButton(
                onClick = {
                    checking = true
                    checkError = null
                    scope.launch {
                        runCatching { updates.check() }.onFailure { checkError = it.message ?: it.javaClass.simpleName }
                        checking = false
                    }
                },
                enabled = !checking,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                if (checking) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(str(R.string.update_checking), Modifier.padding(start = 8.dp))
                } else {
                    Text(str(R.string.update_check))
                }
            }
            SwitchItem(str(R.string.update_auto), str(R.string.update_auto_summary), state.autoCheck) {
                scope.launch { updates.setAutoCheck(it) }
            }
            Note(str(R.string.update_install_note))
        }
    }
}

@Composable
private fun InstallControls(download: DownloadState, onInstall: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (download) {
            is DownloadState.Downloading -> {
                val progress = download.progress
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth())
                    Text(str(R.string.update_downloading_percent, (progress * 100).toInt()), style = MaterialTheme.typography.bodySmall)
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(str(R.string.update_downloading), style = MaterialTheme.typography.bodySmall)
                }
            }
            DownloadState.Installing -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(str(R.string.update_installing), Modifier.padding(start = 8.dp))
            }
            is DownloadState.Failed, DownloadState.Idle -> {
                if (download is DownloadState.Failed) {
                    Text(download.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = onInstall) { Text(str(R.string.update_install)) }
            }
        }
    }
}
