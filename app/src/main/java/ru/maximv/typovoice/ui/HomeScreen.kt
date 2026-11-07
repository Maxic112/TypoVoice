package ru.maximv.typovoice.ui

import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import ru.maximv.typovoice.overlay.BubbleView
import ru.maximv.typovoice.core.settings.BubbleSettings
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import ru.maximv.typovoice.AppContainer
import ru.maximv.typovoice.R
import ru.maximv.typovoice.core.api.SttModel
import ru.maximv.typovoice.core.dictation.DictationSource
import ru.maximv.typovoice.core.dictation.DictationState
import ru.maximv.typovoice.core.history.HistoryEntry
import ru.maximv.typovoice.core.postprocess.Postprocess
import ru.maximv.typovoice.core.servers.ServerConfig
import ru.maximv.typovoice.core.servers.ServerHealth
import ru.maximv.typovoice.core.settings.AppSettings
import ru.maximv.typovoice.core.settings.BubbleAction
import ru.maximv.typovoice.core.settings.OverlayMode
import ru.maximv.typovoice.core.settings.VolumeKeyMode
import ru.maximv.typovoice.ui.settings.volumeSummary
import java.util.Date
import ru.maximv.typovoice.str

/** Everyday screen: on/off and status, missing permissions, a test button, quick choices, recent history. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(container: AppContainer, navigate: (String) -> Unit) {
    val settings = container.settings.collectSettings()
    val state by container.dictation.state.collectAsState()
    val level by container.dictation.level.collectAsState()
    val history by container.history.entries.collectAsState()
    val scope = rememberCoroutineScope()

    // The test result stays on screen until the next test or until the screen is opened anew.
    var testing by rememberSaveable { mutableStateOf(false) }
    var testResult by rememberSaveable { mutableStateOf<String?>(null) }
    var testFailed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (!testing) return@LaunchedEffect
        when (val s = state) {
            is DictationState.Done -> {
                testResult = s.text.ifBlank { str(R.string.vc_not_recognized) }
                testFailed = s.text.isBlank()
                testing = false
            }
            is DictationState.Failed -> {
                testResult = s.message
                testFailed = true
                testing = false
            }
            else -> Unit
        }
    }

    // Refresh once per visit, at screen level: lazy items are re-created while scrolling and must not refetch.
    val server = settings.servers.firstOrNull { it.enabled }
    LaunchedEffect(server?.baseUrl, server?.apiKey) {
        if (server == null) return@LaunchedEffect
        launch { container.serverInfo.refreshHealth(server.baseUrl) }
        launch { container.serverInfo.refreshModels(server) }
        launch { container.refreshPresets() }
    }
    // After a dictation the server may have loaded another model.
    LaunchedEffect(state is DictationState.Done) {
        if (state is DictationState.Done && server != null) container.serverInfo.refreshModels(server)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // The TypoVoice mark (same as the tile); tinted, so it reads on light and dark themes.
                        Icon(
                            painterResource(R.drawable.ic_tile),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(stringResource(R.string.app_name), Modifier.padding(start = 12.dp))
                    }
                },
                actions = {
                    val update by container.updates.state.collectAsState()
                    IconButton(onClick = { navigate(Routes.SETTINGS) }) {
                        BadgedBox(badge = { if (update.available != null) Badge(containerColor = UPDATE_DOT_COLOR) }) {
                            Icon(Icons.Default.Settings, contentDescription = str(R.string.settings_title))
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { StatusCard(container, settings) { scope.launch { container.settings.setEnabled(it) } } }
            item { Warnings(navigate) }
            item {
                TestCard(
                    state,
                    level,
                    settings.bubble,
                    testResult,
                    testFailed,
                    onCopy = { testResult?.let { container.delivery.copyToClipboard(it) } },
                    onClear = { testResult = null; testFailed = false },
                ) {
                    if (!state.isBusy) {
                        testing = true
                        testResult = null
                    }
                    container.dictation.toggle(DictationSource.APP)
                }
            }
            item {
                QuickChoiceCard(
                    container,
                    settings,
                    onModel = { server, model -> scope.launch { container.settings.upsertServer(server.copy(model = model)) } },
                    onPostprocess = { scope.launch { container.settings.setPostprocessChoice(it) } },
                )
            }
            item { EntryPointsCard(container, settings, navigate) }
            item { HistoryCard(history.take(3)) { navigate(Routes.HISTORY) } }
        }
    }
}

@Composable
private fun StatusCard(container: AppContainer, settings: AppSettings, onToggle: (Boolean) -> Unit) {
    val server = settings.servers.firstOrNull { it.enabled }
    // null = not checked yet.
    val health by container.serverInfo.health.collectAsState()
    val status = server?.let { health[it.baseUrl] }
    Card(
        colors = if (settings.enabled) CardDefaults.cardColors() else CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (settings.enabled) str(R.string.home_on) else str(R.string.home_off),
                    style = MaterialTheme.typography.titleLarge,
                )
                when {
                    !settings.enabled -> Text(str(R.string.home_off_detail), style = MaterialTheme.typography.bodyMedium)
                    server == null -> Text(str(R.string.no_enabled_servers), style = MaterialTheme.typography.bodyMedium)
                    else -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(str(R.string.home_server, server.name), style = MaterialTheme.typography.bodyMedium)
                        val (color, description) = when (status) {
                            null -> STATUS_CHECKING to str(R.string.status_checking)
                            ServerHealth.Online -> STATUS_ONLINE to str(R.string.status_online)
                            ServerHealth.Offline -> MaterialTheme.colorScheme.error to str(R.string.status_offline)
                            is ServerHealth.Degraded -> STATUS_CHECKING to str(R.string.status_engine, workerLabel(status.worker))
                        }
                        Box(
                            Modifier.padding(start = 8.dp).size(10.dp).clip(CircleShape).background(color)
                                .semantics { contentDescription = str(R.string.status_a11y, description) },
                        )
                        // Online and offline are said by the dot; an engine problem needs words.
                        if (status is ServerHealth.Degraded) {
                            Text(
                                description,
                                Modifier.padding(start = 6.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Switch(checked = settings.enabled, onCheckedChange = onToggle)
        }
        TextButton(
            onClick = container::restartApp,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        ) {
            Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(str(R.string.home_restart), Modifier.padding(start = 8.dp))
        }
    }
}

/** Shows only what is missing, each with a way to fix it. */
@Composable
private fun Warnings(navigate: (String) -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    val runtimeMissing = refresh.let {
        !Permissions.hasMicrophone(context) || !Permissions.hasNotifications(context) || !Permissions.hasLocalNetwork(context)
    }
    val a11yMissing = refresh.let { !Permissions.isAccessibilityEnabled(context) }
    if (!runtimeMissing && !a11yMissing) return

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Warning, contentDescription = null)
                Text(str(R.string.home_permissions_needed), style = MaterialTheme.typography.titleSmall)
            }
            if (runtimeMissing) {
                TextButton(onClick = {
                    launcher.launch(
                        arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS, Permissions.LOCAL_NETWORK),
                    )
                }) { Text(str(R.string.home_permissions_grant)) }
            }
            if (a11yMissing) {
                TextButton(onClick = { context.startActivity(Permissions.accessibilitySettingsIntent()) }) {
                    Text(str(R.string.home_enable_a11y))
                }
            }
            TextButton(onClick = { navigate(Routes.PERMISSIONS) }) { Text(str(R.string.common_more)) }
        }
    }
}

/**
 * The floating button itself (same base, icons, wave and spinner as on screen), with the icons chosen
 * for it in settings. The visible circle is 96 dp; the view is larger to leave room for the voice halo.
 */
@Composable
private fun BubbleButton(
    state: DictationState,
    level: Float,
    bubble: BubbleSettings,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { BubbleView(it).apply { translucentIdle = false } },
            modifier = Modifier.requiredSize(124.dp),
            update = { view ->
                view.idleIcon = bubble.idleIcon
                view.recordingIcon = bubble.recordingIcon
                view.visual = when (state) {
                    DictationState.Idle -> BubbleView.Visual.IDLE
                    is DictationState.Starting, is DictationState.Recording -> BubbleView.Visual.RECORDING
                    is DictationState.Transcribing -> BubbleView.Visual.BUSY
                    is DictationState.Done -> BubbleView.Visual.SUCCESS
                    is DictationState.Failed -> BubbleView.Visual.ERROR
                }
                view.level = level
            },
        )
        Box(
            Modifier.matchParentSize().clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { this.contentDescription = contentDescription },
        )
    }
}

@Composable
private fun TestCard(
    state: DictationState,
    level: Float,
    bubble: BubbleSettings,
    result: String?,
    failed: Boolean,
    onCopy: () -> Unit,
    onClear: () -> Unit,
    onToggle: () -> Unit,
) {
    Card {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val recording = state is DictationState.Recording || state is DictationState.Starting
            BubbleButton(
                state,
                level,
                bubble,
                contentDescription = str(if (recording) R.string.home_stop_recording else R.string.home_test),
                onClick = onToggle,
            )
            when {
                state is DictationState.Starting -> Text(str(R.string.home_starting))
                state is DictationState.Recording -> Text(str(R.string.vi_speak_now))
                state is DictationState.Transcribing -> Text(state.partial.ifBlank { str(R.string.common_recognizing) })
                result != null -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (failed) str(R.string.home_test_failed) else str(R.string.common_result),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SelectionContainer {
                        Text(
                            result,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (!failed) {
                            TextButton(onClick = onCopy) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, Modifier.size(18.dp))
                                Text(str(R.string.common_copy), Modifier.padding(start = 8.dp))
                            }
                        }
                        TextButton(onClick = onClear) {
                            Icon(Icons.Default.Clear, contentDescription = null, Modifier.size(18.dp))
                            Text(str(R.string.common_clear), Modifier.padding(start = 8.dp))
                        }
                    }
                }
                else -> Text(str(R.string.home_test_hint), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Two compact selects: the recognition model of the first enabled server (the same setting as in
 * its editor) and the quick post-processing choice for all dictations.
 */
@Composable
private fun QuickChoiceCard(
    container: AppContainer,
    settings: AppSettings,
    onModel: (ServerConfig, String?) -> Unit,
    onPostprocess: (String?) -> Unit,
) {
    val server = settings.servers.firstOrNull { it.enabled }
    // From the shared cache; null = never loaded yet. The home screen refreshes it.
    val cache by container.serverInfo.models.collectAsState()
    val cached = server?.let { cache[it.id] }
    val models = cached?.models
    val failed = cached?.failed == true
    val real = models.orEmpty().filter { !it.isAlias }
    val hasCustom = settings.servers.any { it.enabled && !it.customPrompt.isNullOrBlank() }

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (server != null) {
                val selected = server.model
                val options = listOf(SelectOption<String?>(null, str(R.string.model_server_current))) +
                    real.map { SelectOption<String?>(it.id, it.name, if (it.loaded) str(R.string.model_loaded_short) else null) }
                SelectField(
                    label = str(R.string.home_model),
                    options = options,
                    selected = selected,
                    fallbackText = selected.orEmpty(),
                ) { onModel(server, it) }
                val note = when {
                    models == null -> str(R.string.home_models_loading)
                    failed -> str(R.string.home_models_failed, server.name)
                    real.firstOrNull { it.id == selected }?.loaded == false ->
                        str(R.string.home_model_switch_note)
                    else -> null
                }
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            SelectField(
                label = str(R.string.common_postprocess),
                options = Postprocess.choices(settings.cachedPresets, hasCustom).map { SelectOption(it, Postprocess.label(it)) },
                selected = settings.postprocessChoice,
                fallbackText = Postprocess.label(settings.postprocessChoice),
                onSelect = onPostprocess,
            )
            val b = settings.bubble
            val gesture = when (BubbleAction.POSTPROCESS_MENU) {
                b.doubleTap -> str(R.string.home_gesture_double)
                b.hold -> str(R.string.home_gesture_hold)
                b.tap -> str(R.string.home_gesture_tap)
                else -> null
            }
            if (gesture != null && settings.overlayMode != OverlayMode.OFF) {
                Text(
                    str(R.string.home_postprocess_hint, gesture),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Every way to start a dictation: switch it on or off here, details in its settings screen. */
@Composable
private fun EntryPointsCard(container: AppContainer, settings: AppSettings, navigate: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val isAssistant = refresh.let { Permissions.isAssistant(context) }
    val s = container.settings
    Card {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                str(R.string.launch_title),
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleSmall,
            )
            EntryPointRow(
                EntryIcons.overlay, str(R.string.entry_bubble),
                when (settings.overlayMode) {
                    OverlayMode.OFF -> str(R.string.common_off_f)
                    OverlayMode.ON_TEXT_FOCUS -> str(R.string.overlay_when_typing)
                    OverlayMode.ALWAYS -> str(R.string.overlay_always)
                },
                settings.overlayMode != OverlayMode.OFF,
                onToggle = { on -> scope.launch { s.setOverlayMode(if (on) OverlayMode.ON_TEXT_FOCUS else OverlayMode.OFF) } },
            ) { navigate(Routes.OVERLAY) }
            EntryPointRow(
                EntryIcons.tile, str(R.string.entry_tile),
                if (settings.tileEnabled) str(R.string.home_tile_summary) else str(R.string.common_off_f),
                settings.tileEnabled,
                onToggle = { on -> scope.launch { s.setTileEnabled(on) } },
            ) { navigate(Routes.LAUNCH) }
            EntryPointRow(
                EntryIcons.volume, str(R.string.source_volume),
                volumeSummary(settings.volumeKey, settings.volumeTrigger),
                settings.volumeKey != VolumeKeyMode.OFF,
                onToggle = { on -> scope.launch { s.setVolumeKey(if (on) VolumeKeyMode.VOLUME_DOWN else VolumeKeyMode.OFF) } },
            ) { navigate(Routes.LAUNCH) }
            EntryPointRow(
                EntryIcons.launcher, str(R.string.entry_launcher),
                if (settings.launcherIcon) str(R.string.home_launcher_summary) else str(R.string.common_off_f),
                settings.launcherIcon,
                onToggle = { on -> scope.launch { s.setLauncherIcon(on) } },
            ) { navigate(Routes.LAUNCH) }
            // Choosing the assistant is a system setting: the switch leads to its screen.
            EntryPointRow(
                EntryIcons.assistant, str(R.string.entry_assistant),
                if (isAssistant) str(R.string.home_assistant_summary) else str(R.string.common_not_selected),
                isAssistant,
                onToggle = { navigate(Routes.ASSISTANT) },
            ) { navigate(Routes.ASSISTANT) }
            EntryPointRow(EntryIcons.widget, str(R.string.entry_widget), str(R.string.common_always_available_pl), checked = null) { navigate(Routes.LAUNCH) }
            EntryPointRow(EntryIcons.share, str(R.string.entry_share), str(R.string.common_always_available), checked = null) { navigate(Routes.LAUNCH) }
        }
    }
}

/** One entry point: icon, name and state, an on/off switch (null — can't be turned off) and a settings button. */
@Composable
private fun EntryPointRow(
    icon: ImageVector,
    title: String,
    summary: String,
    checked: Boolean?,
    onToggle: (Boolean) -> Unit = {},
    onSettings: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (checked != null) Switch(checked = checked, onCheckedChange = onToggle)
                IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = str(R.string.home_entry_settings, title)) }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** The three latest dictations, two lines each, and a way into the full history. */
@Composable
private fun HistoryCard(recent: List<HistoryEntry>, onOpen: () -> Unit) {
    Card(onClick = onOpen) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(str(R.string.home_history), style = MaterialTheme.typography.titleSmall)
            if (recent.isEmpty()) {
                Text(str(R.string.common_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            recent.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider()
                Column {
                    Text(
                        DATE_FORMAT.format(Date(entry.createdAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        entry.text ?: str(R.string.history_not_recognized, entry.error),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (entry.text == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            TextButton(onClick = onOpen, Modifier.align(Alignment.End)) { Text(str(R.string.home_all_history)) }
        }
    }
}

/** Human words for the worker state from `GET /extv1/health`. */
internal fun workerLabel(worker: String) = when (worker) {
    "ready" -> str(R.string.worker_ready)
    "busy" -> str(R.string.worker_busy)
    "loading" -> str(R.string.worker_loading)
    "unloaded" -> str(R.string.worker_unloaded)
    "error" -> str(R.string.worker_error)
    "unavailable" -> str(R.string.status_offline)
    else -> worker
}

private val STATUS_ONLINE = Color(0xFF43A047)
private val STATUS_CHECKING = Color(0xFFFFB300)
