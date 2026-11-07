package ru.maximv.typovoice.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.maximv.typovoice.AppContainer
import ru.maximv.typovoice.ui.collectSettings
import ru.maximv.typovoice.core.servers.SelectionPolicy
import ru.maximv.typovoice.core.api.TranscriptionRequest
import ru.maximv.typovoice.core.servers.ServerConfig
import ru.maximv.typovoice.core.settings.AppSettings
import ru.maximv.typovoice.ui.GroupHeader
import ru.maximv.typovoice.ui.InfoItem
import ru.maximv.typovoice.ui.NavItem
import ru.maximv.typovoice.ui.Note
import ru.maximv.typovoice.ui.SelectItem
import ru.maximv.typovoice.ui.SelectOption
import ru.maximv.typovoice.ui.SectionScreen
import ru.maximv.typovoice.ui.ServerDialog
import ru.maximv.typovoice.R
import ru.maximv.typovoice.str

@Composable
fun ServersScreen(container: AppContainer, onBack: () -> Unit) {
    val s = container.settings.collectSettings()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<ServerConfig?>(null) }
    var creating by remember { mutableStateOf(false) }

    // Model names per server, so the cards say the same as the home screen's select.
    val modelCache by container.serverInfo.models.collectAsState()
    LaunchedEffect(s.servers.map { it.id to it.baseUrl }) {
        s.servers.forEach { server -> launch { container.serverInfo.refreshModels(server) } }
    }

    SectionScreen(str(R.string.servers_title), onBack) {
        item { GroupHeader(str(R.string.servers_list)) }
        s.servers.forEach { server ->
            item(key = server.id) {
                ServerRow(
                    server,
                    modelName = server.model?.let { id -> modelCache[server.id]?.models?.firstOrNull { it.id == id }?.name },
                    onToggle = { scope.launch { container.settings.upsertServer(server.copy(enabled = it)) } },
                    onEdit = { editing = server },
                )
            }
        }
        item {
            if (s.servers.isEmpty()) InfoItem(str(R.string.servers_empty))
            NavItem(str(R.string.servers_add), icon = Icons.Default.Add) { creating = true }
        }
        item {
            HorizontalDivider(Modifier.padding(top = 12.dp))
            GroupHeader(str(R.string.servers_policy))
            val fixedId = s.fixedServerId?.takeIf { id -> s.servers.any { it.id == id } } ?: s.servers.firstOrNull()?.id
            SelectItem(
                str(R.string.servers_policy_select),
                SelectionPolicy.entries.map { SelectOption(it, policyLabel(it), policyHint(it)) },
                s.policy,
            ) { scope.launch { container.settings.setPolicy(it, fixedId) } }
            if (s.policy == SelectionPolicy.FIXED && s.servers.isNotEmpty()) {
                SelectItem(
                    str(R.string.common_server),
                    s.servers.map { SelectOption<String?>(it.id, it.name, it.baseUrl) },
                    fixedId,
                ) { scope.launch { container.settings.setPolicy(SelectionPolicy.FIXED, it) } }
            }
            Note(
                str(R.string.servers_policy_note),
            )
        }
    }

    if (editing != null || creating) {
        val dialogServer = editing
        ServerDialog(
            initial = dialogServer,
            api = container.api,
            onDismiss = { editing = null; creating = false },
            onSave = { server ->
                scope.launch { container.settings.upsertServer(server) }
                editing = null
                creating = false
            },
            onDelete = dialogServer?.let { server ->
                {
                    scope.launch { container.settings.deleteServer(server.id) }
                    editing = null
                }
            },
        )
    }
}

/** A server as a card: name, address and switch on top, its recognition and post-processing settings below. */
@Composable
private fun ServerRow(server: ServerConfig, modelName: String?, onToggle: (Boolean) -> Unit, onEdit: () -> Unit) {
    val recognition = buildList {
        add(modelName ?: server.model ?: str(R.string.servers_current_model))
        server.language?.let { add(str(R.string.servers_language, it)) }
        if (server.vad) add("VAD")
        if (server.stream) add(str(R.string.servers_stream))
    }.joinToString(" · ")
    val postprocess = when (server.preset) {
        null -> str(R.string.servers_key_default)
        TranscriptionRequest.PRESET_NONE -> str(R.string.servers_no_processing)
        else -> server.preset
    } + if (server.customPrompt.isNullOrBlank()) "" else str(R.string.servers_custom_prompt)

    OutlinedCard(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).alpha(if (server.enabled) 1f else 0.6f),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(server.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        server.baseUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(checked = server.enabled, onCheckedChange = onToggle)
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            ServerDetail(Icons.Default.GraphicEq, str(R.string.sd_recognition), recognition)
            ServerDetail(Icons.Default.AutoFixHigh, str(R.string.common_postprocess), postprocess)
        }
    }
}

@Composable
private fun ServerDetail(icon: ImageVector, label: String, value: String) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.padding(start = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

internal fun policyLabel(policy: SelectionPolicy) = when (policy) {
    SelectionPolicy.FIXED -> str(R.string.policy_fixed)
    SelectionPolicy.FAILOVER -> str(R.string.policy_failover)
    SelectionPolicy.LAST_SUCCESSFUL -> str(R.string.policy_sticky)
    SelectionPolicy.ROUND_ROBIN -> str(R.string.policy_round_robin)
    SelectionPolicy.RANDOM -> str(R.string.policy_random)
}

private fun policyHint(policy: SelectionPolicy) = when (policy) {
    SelectionPolicy.FIXED -> str(R.string.policy_fixed_hint)
    SelectionPolicy.FAILOVER -> str(R.string.policy_failover_hint)
    SelectionPolicy.LAST_SUCCESSFUL -> str(R.string.policy_sticky_hint)
    SelectionPolicy.ROUND_ROBIN -> str(R.string.policy_round_robin_hint)
    SelectionPolicy.RANDOM -> str(R.string.policy_random_hint)
}
