package ru.maximv.typovoice.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import android.app.StatusBarManager
import android.os.Build
import android.graphics.drawable.Icon
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AppShortcut
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import ru.maximv.typovoice.AppContainer
import ru.maximv.typovoice.ui.collectSettings
import ru.maximv.typovoice.R
import ru.maximv.typovoice.core.settings.AppSettings
import ru.maximv.typovoice.core.settings.BubbleAction
import ru.maximv.typovoice.core.settings.BubbleIcon
import ru.maximv.typovoice.core.settings.BubbleSettings
import ru.maximv.typovoice.core.settings.OverlayMode
import ru.maximv.typovoice.overlay.BubbleView
import ru.maximv.typovoice.core.settings.VolumeKeyMode
import ru.maximv.typovoice.core.settings.VolumeTrigger
import ru.maximv.typovoice.tile.DictationTileService
import ru.maximv.typovoice.ui.Divider
import ru.maximv.typovoice.ui.EntryIcons
import ru.maximv.typovoice.ui.GroupHeader
import ru.maximv.typovoice.ui.InfoItem
import ru.maximv.typovoice.ui.NavItem
import ru.maximv.typovoice.ui.Note
import ru.maximv.typovoice.ui.Permissions
import ru.maximv.typovoice.ui.Routes
import ru.maximv.typovoice.ui.SectionScreen
import ru.maximv.typovoice.ui.SelectItem
import ru.maximv.typovoice.ui.SelectOption
import ru.maximv.typovoice.ui.RadioItem
import ru.maximv.typovoice.ui.SwitchItem
import ru.maximv.typovoice.str

/** Radio options of an enabled entry point line up with its title, past the leading icon. */
internal val OptionIndent = 40.dp

@Composable
fun LaunchScreen(container: AppContainer, onBack: () -> Unit, navigate: (String) -> Unit) {
    val s = container.settings.collectSettings()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val isAssistant = refresh.let { Permissions.isAssistant(context) }
    var tileResult by remember { mutableStateOf<String?>(null) }

    SectionScreen(str(R.string.launch_title), onBack) {
        item {
            GroupHeader(str(R.string.launch_on_screen))
            NavItem(str(R.string.entry_bubble), overlaySummary(s.overlayMode), EntryIcons.overlay) { navigate(Routes.OVERLAY) }
            Divider()
            SwitchItem(str(R.string.entry_tile), str(R.string.home_tile_summary), s.tileEnabled, icon = EntryIcons.tile) {
                tileResult = null
                scope.launch { container.settings.setTileEnabled(it) }
            }
            if (s.tileEnabled) {
                // The switch only makes the tile available; it still has to be placed into the panel.
                Column(Modifier.padding(start = 56.dp, end = 16.dp, bottom = 8.dp)) {
                    if (s.tileInPanel) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(str(R.string.launch_tile_in_panel), Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        Text(
                            str(R.string.launch_tile_missing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // The in-app request exists since Android 13; on 12 the note above is the way.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) FilledTonalButton(
                            onClick = {
                                context.getSystemService(StatusBarManager::class.java).requestAddTileService(
                                    DictationTileService.component(context),
                                    str(R.string.tile_label),
                                    Icon.createWithResource(context, R.drawable.ic_tile),
                                    context.mainExecutor,
                                ) { code ->
                                    tileResult = tileResultText(code)
                                    if (code == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                                        code == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
                                    ) {
                                        scope.launch { container.settings.setTileInPanel(true) }
                                    }
                                }
                            },
                            modifier = Modifier.padding(top = 8.dp),
                        ) { Text(str(R.string.launch_tile_add)) }
                    }
                    tileResult?.let {
                        Text(it, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        item {
            Divider()
            GroupHeader(str(R.string.launch_volume_keys))
            SwitchItem(
                str(R.string.launch_volume_dictation),
                str(R.string.launch_volume_summary),
                s.volumeKey != VolumeKeyMode.OFF,
                icon = EntryIcons.volume,
            ) { on -> scope.launch { container.settings.setVolumeKey(if (on) VolumeKeyMode.VOLUME_DOWN else VolumeKeyMode.OFF) } }
            if (s.volumeKey != VolumeKeyMode.OFF) {
                SelectItem(
                    str(R.string.launch_volume_trigger),
                    listOf(
                        SelectOption(VolumeTrigger.PRESS, str(R.string.gesture_press), str(R.string.trigger_press_detail)),
                        SelectOption(VolumeTrigger.DOUBLE_PRESS, str(R.string.gesture_double), str(R.string.trigger_double_detail)),
                        SelectOption(VolumeTrigger.HOLD, str(R.string.gesture_hold), str(R.string.trigger_hold_detail)),
                        SelectOption(VolumeTrigger.HOLD_TOGGLE, str(R.string.trigger_hold_toggle), str(R.string.trigger_hold_toggle_detail)),
                    ),
                    s.volumeTrigger,
                    summary = volumeTriggerHint(s.volumeTrigger),
                    startPadding = 56.dp,
                ) { scope.launch { container.settings.setVolumeTrigger(it) } }
                val set: (VolumeKeyMode) -> Unit = { scope.launch { container.settings.setVolumeKey(it) } }
                RadioItem(str(R.string.volume_down), null, s.volumeKey == VolumeKeyMode.VOLUME_DOWN, OptionIndent) { set(VolumeKeyMode.VOLUME_DOWN) }
                RadioItem(str(R.string.volume_up), null, s.volumeKey == VolumeKeyMode.VOLUME_UP, OptionIndent) { set(VolumeKeyMode.VOLUME_UP) }
            }
        }
        item {
            Divider()
            GroupHeader(str(R.string.launch_icon))
            SwitchItem(
                str(R.string.entry_launcher),
                str(R.string.launch_launcher_summary),
                s.launcherIcon,
                icon = EntryIcons.launcher,
            ) { scope.launch { container.settings.setLauncherIcon(it) } }
        }
        item {
            Divider()
            GroupHeader(str(R.string.launch_system))
            NavItem(
                str(R.string.launch_assistant),
                if (isAssistant) str(R.string.launch_assistant_selected) else str(R.string.common_not_selected),
                EntryIcons.assistant,
            ) { navigate(Routes.ASSISTANT) }
        }
        item {
            Divider()
            GroupHeader(str(R.string.common_always_available_pl))
            InfoItem(str(R.string.launch_widget), str(R.string.launch_widget_summary), EntryIcons.widget)
            Divider()
            InfoItem(str(R.string.launch_shortcut), str(R.string.launch_shortcut_summary), Icons.Default.AppShortcut)
            Divider()
            InfoItem(str(R.string.launch_share), str(R.string.launch_share_summary), EntryIcons.share)
            Note(str(R.string.launch_clipboard_note))
        }
    }
}

/** Floating button: when it shows, what each gesture does and how it looks. */
@Composable
fun OverlayScreen(container: AppContainer, onBack: () -> Unit) {
    val s = container.settings.collectSettings()
    val scope = rememberCoroutineScope()
    val set: (OverlayMode) -> Unit = { scope.launch { container.settings.setOverlayMode(it) } }
    val b = s.bubble
    val update: (BubbleSettings) -> Unit = { scope.launch { container.settings.setBubble(it) } }

    SectionScreen(str(R.string.entry_bubble), onBack) {
        item {
            SwitchItem(
                str(R.string.entry_bubble),
                str(R.string.overlay_summary),
                s.overlayMode != OverlayMode.OFF,
                icon = EntryIcons.overlay,
            ) { on -> set(if (on) OverlayMode.ON_TEXT_FOCUS else OverlayMode.OFF) }
            Note(str(R.string.overlay_drag_note))
        }
        if (s.overlayMode != OverlayMode.OFF) {
            item {
                Divider()
                GroupHeader(str(R.string.overlay_when))
                RadioItem(str(R.string.overlay_when_typing), str(R.string.overlay_when_typing_detail), s.overlayMode == OverlayMode.ON_TEXT_FOCUS) {
                    set(OverlayMode.ON_TEXT_FOCUS)
                }
                RadioItem(str(R.string.overlay_always_short), str(R.string.overlay_always_detail), s.overlayMode == OverlayMode.ALWAYS) { set(OverlayMode.ALWAYS) }
            }
            item {
                Divider()
                GroupHeader(str(R.string.overlay_actions))
                SelectItem(str(R.string.gesture_press), actionOptions(hold = false), b.tap) { update(b.copy(tap = it)) }
                SelectItem(
                    str(R.string.gesture_double),
                    actionOptions(hold = false),
                    b.doubleTap,
                    summary = str(R.string.overlay_double_note),
                ) { update(b.copy(doubleTap = it)) }
                SelectItem(str(R.string.gesture_hold), actionOptions(hold = true), b.hold) { update(b.copy(hold = it)) }
                Note(str(R.string.overlay_busy_note))
            }
            item {
                Divider()
                GroupHeader(str(R.string.launch_icon))
                SelectItem(str(R.string.overlay_icon_idle), iconOptions(recording = false), b.idleIcon) { update(b.copy(idleIcon = it)) }
                SelectItem(
                    str(R.string.overlay_icon_recording),
                    iconOptions(recording = true),
                    b.recordingIcon,
                    summary = if (b.recordingIcon.dynamic) str(R.string.overlay_icon_dynamic_note) else null,
                ) { update(b.copy(recordingIcon = it)) }
            }
        }
    }
}

private fun actionOptions(hold: Boolean) = buildList {
    add(SelectOption(BubbleAction.TOGGLE_RECORDING, str(R.string.action_toggle), str(R.string.action_toggle_detail)))
    if (hold) add(SelectOption(BubbleAction.PUSH_TO_TALK, str(R.string.action_ptt), str(R.string.action_ptt_detail)))
    add(SelectOption(BubbleAction.POSTPROCESS_MENU, str(R.string.action_postprocess), str(R.string.action_postprocess_detail)))
    add(SelectOption(BubbleAction.MODEL_MENU, str(R.string.action_model), str(R.string.action_model_detail)))
    add(SelectOption(BubbleAction.OPEN_APP, str(R.string.action_open_app)))
    add(SelectOption(BubbleAction.NONE, str(R.string.action_none)))
}

private fun iconOptions(recording: Boolean) = buildList {
    fun option(icon: BubbleIcon, label: Int, detail: String? = null) =
        SelectOption(icon, str(label), detail, preview = { BubbleIconPreview(icon) })
    add(option(BubbleIcon.MARK_COLOR, R.string.icon_mark_color))
    add(option(BubbleIcon.MARK_WHITE, R.string.icon_mark_white))
    add(option(BubbleIcon.MIC, R.string.icon_mic))
    add(option(BubbleIcon.VOICE, R.string.icon_voice))
    add(option(BubbleIcon.HEARING, R.string.icon_hearing))
    if (recording) {
        val dynamic = str(R.string.icon_dynamic)
        add(option(BubbleIcon.WAVE, R.string.icon_wave, dynamic))
        add(option(BubbleIcon.PULSE, R.string.icon_pulse, dynamic))
    }
}

/** The icon on a mini floating button, so white and colored icons read the same as on screen. */
@Composable
private fun BubbleIconPreview(icon: BubbleIcon) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(
            Brush.radialGradient(listOf(Color(BubbleView.DISC_CENTER), Color(BubbleView.DISC_EDGE))),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(BubbleView.iconRes(icon)),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            // The colored mark keeps its own colors; the rest are white like on the button.
            tint = if (icon == BubbleIcon.MARK_COLOR) Color.Unspecified else Color.White,
        )
    }
}

private fun volumeTriggerHint(trigger: VolumeTrigger) = when (trigger) {
    VolumeTrigger.PRESS -> str(R.string.trigger_press_hint)
    VolumeTrigger.DOUBLE_PRESS -> str(R.string.trigger_double_hint)
    VolumeTrigger.HOLD -> str(R.string.action_ptt_detail)
    VolumeTrigger.HOLD_TOGGLE -> str(R.string.trigger_hold_toggle_hint)
}

/** Home screen line for the volume-key entry point. */
internal fun volumeSummary(key: VolumeKeyMode, trigger: VolumeTrigger): String {
    val button = when (key) {
        VolumeKeyMode.OFF -> return str(R.string.common_off_f)
        VolumeKeyMode.VOLUME_DOWN -> "«−»"
        VolumeKeyMode.VOLUME_UP -> "«+»"
    }
    return when (trigger) {
        VolumeTrigger.PRESS -> str(R.string.volume_press, button)
        VolumeTrigger.DOUBLE_PRESS -> str(R.string.volume_double, button)
        VolumeTrigger.HOLD -> str(R.string.volume_hold, button)
        VolumeTrigger.HOLD_TOGGLE -> str(R.string.volume_hold_toggle, button)
    }
}

internal fun overlaySummary(mode: OverlayMode) = when (mode) {
    OverlayMode.OFF -> str(R.string.common_off_f)
    OverlayMode.ON_TEXT_FOCUS -> str(R.string.overlay_when_typing)
    OverlayMode.ALWAYS -> str(R.string.overlay_always)
}

private fun tileResultText(code: Int) = when (code) {
    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> str(R.string.tile_added)
    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> str(R.string.tile_already)
    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> str(R.string.tile_not_added)
    else -> str(R.string.tile_add_failed, code)
}
