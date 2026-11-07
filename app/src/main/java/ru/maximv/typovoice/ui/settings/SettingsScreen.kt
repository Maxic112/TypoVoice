package ru.maximv.typovoice.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import ru.maximv.typovoice.AppContainer
import ru.maximv.typovoice.ui.collectSettings
import ru.maximv.typovoice.BuildConfig
import ru.maximv.typovoice.core.servers.SelectionPolicy
import ru.maximv.typovoice.core.update.Release
import ru.maximv.typovoice.core.update.UpdateManager
import ru.maximv.typovoice.core.settings.AppSettings
import ru.maximv.typovoice.core.settings.AutoStopMode
import ru.maximv.typovoice.core.settings.InsertMethod
import ru.maximv.typovoice.core.settings.OverlayMode
import ru.maximv.typovoice.core.settings.VolumeKeyMode
import ru.maximv.typovoice.ui.NavItem
import ru.maximv.typovoice.ui.Permissions
import ru.maximv.typovoice.ui.Routes
import ru.maximv.typovoice.ui.SectionScreen
import ru.maximv.typovoice.plural
import ru.maximv.typovoice.R
import ru.maximv.typovoice.str

/** List of sections; each row summarizes the current values. */
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit, navigate: (String) -> Unit) {
    val s = container.settings.collectSettings()
    val context = LocalContext.current
    val updates = container.updates
    val update = updates.state.collectAsState().value.available
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val permissionsOk = refresh.let {
        Permissions.hasMicrophone(context) && Permissions.hasNotifications(context) &&
            Permissions.hasLocalNetwork(context) && Permissions.isAccessibilityEnabled(context)
    }

    SectionScreen(str(R.string.settings_title), onBack) {
        item {
            NavItem(str(R.string.servers_title), serversSummary(s), Icons.Default.Dns) { navigate(Routes.SERVERS) }
            NavItem(str(R.string.launch_title), launchSummary(s), Icons.Default.TouchApp) { navigate(Routes.LAUNCH) }
            NavItem(str(R.string.recording_title), recordingSummary(s), Icons.Default.Mic) { navigate(Routes.RECORDING) }
            NavItem(str(R.string.insert_title), insertSummary(s), Icons.Default.TextFields) { navigate(Routes.INSERT) }
            NavItem(str(R.string.reliability_title), reliabilitySummary(s), Icons.Default.Refresh) { navigate(Routes.RELIABILITY) }
            NavItem(str(R.string.feedback_title), feedbackSummary(s), Icons.Default.NotificationsActive) { navigate(Routes.FEEDBACK) }
            NavItem(str(R.string.perm_title), if (permissionsOk) str(R.string.perm_all_granted) else str(R.string.perm_some_missing), Icons.Default.Security) {
                navigate(Routes.PERMISSIONS)
            }
            NavItem(str(R.string.language_title), AppLanguage.label(context), Icons.Default.Language) { navigate(Routes.LANGUAGE) }
            NavItem(str(R.string.update_title), updatesSummary(updates.configured, update), Icons.Default.SystemUpdate, badge = update != null) {
                navigate(Routes.UPDATES)
            }
            NavItem(str(R.string.about_title), str(R.string.about_version, BuildConfig.VERSION_NAME), Icons.Default.Info) { navigate(Routes.ABOUT) }
        }
    }
}

private fun updatesSummary(configured: Boolean, update: Release?): String = when {
    !configured -> str(R.string.update_installed_version, UpdateManager.currentVersion)
    update != null -> str(R.string.update_available_version, update.version)
    else -> str(R.string.update_installed_version, UpdateManager.currentVersion)
}

private fun serversSummary(s: AppSettings): String {
    val count = s.servers.count { it.enabled }
    val policy = policyLabel(s.policy).lowercase()
    return if (count == 0) str(R.string.no_enabled_servers) else str(R.string.settings_servers_summary, count, policy)
}

private fun launchSummary(s: AppSettings) = buildList {
    if (s.overlayMode != OverlayMode.OFF) add(str(R.string.settings_launch_button))
    if (s.tileEnabled) add(str(R.string.settings_launch_tile))
    if (s.volumeKey != VolumeKeyMode.OFF) add(str(R.string.settings_launch_volume))
    if (s.launcherIcon) add(str(R.string.settings_launch_icon))
}.joinToString(", ").replaceFirstChar { it.uppercase() }.ifEmpty { str(R.string.settings_launch_default) }

private fun recordingSummary(s: AppSettings): String {
    val limit = if (s.maxRecordingMinutes == 0) str(R.string.recording_no_limit) else str(R.string.settings_limit_upto, minutesLabel(s.maxRecordingMinutes))
    val stop = when (s.autoStop) {
        AutoStopMode.OFF -> str(R.string.settings_autostop_off)
        AutoStopMode.ASSISTANT_ONLY -> str(R.string.settings_autostop_assistant)
        AutoStopMode.ALL_TAPS -> str(R.string.settings_autostop_after, seconds(s.recognitionSilenceMs))
    }
    return "$limit · $stop"
}

private fun insertSummary(s: AppSettings): String {
    val method = if (s.insertMethod == InsertMethod.PASTE) str(R.string.settings_insert_paste) else str(R.string.settings_insert_set)
    val spaces = listOfNotNull(str(R.string.settings_space_before).takeIf { s.spacing.before }, str(R.string.settings_space_after).takeIf { s.spacing.after })
    return (listOf(method) + spaces).joinToString(" · ")
}

private fun reliabilitySummary(s: AppSettings): String {
    val retry = if (s.autoRetry) str(R.string.settings_retry_on, plural(R.plurals.attempts, s.retryAttempts)) else str(R.string.settings_retry_off)
    return if (s.historyEnabled) str(R.string.settings_with_history, retry) else str(R.string.settings_without_history, retry)
}

private fun feedbackSummary(s: AppSettings) =
    listOfNotNull(str(R.string.feedback_vibration).takeIf { s.vibrationFeedback }, str(R.string.settings_sound).takeIf { s.soundFeedback })
        .joinToString(", ").replaceFirstChar { it.uppercase() }.ifEmpty { str(R.string.common_off_m) }

/** "1.5 s" with the decimal separator of the current locale. */
internal fun seconds(ms: Long): String = str(R.string.seconds_short, java.text.DecimalFormat("0.#").format(ms / 1000.0))
