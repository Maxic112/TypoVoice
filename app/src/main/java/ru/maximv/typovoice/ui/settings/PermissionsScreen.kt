package ru.maximv.typovoice.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import ru.maximv.typovoice.ui.InfoItem
import ru.maximv.typovoice.ui.Note
import ru.maximv.typovoice.ui.Permissions
import ru.maximv.typovoice.ui.SectionScreen
import ru.maximv.typovoice.R
import ru.maximv.typovoice.str

@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val appSettings = { context.startActivity(Permissions.appDetailsIntent(context)) }

    // Read `refresh` so the checks re-run after returning from system settings.
    val mic = refresh.let { Permissions.hasMicrophone(context) }
    val notifications = refresh.let { Permissions.hasNotifications(context) }
    val lan = refresh.let { Permissions.hasLocalNetwork(context) }
    val a11y = refresh.let { Permissions.isAccessibilityEnabled(context) }

    @Composable
    fun PermissionRow(title: String, summary: String, granted: Boolean, request: () -> Unit) = InfoItem(
        title,
        summary,
        trailing = {
            if (granted) Text(str(R.string.perm_granted)) else TextButton(onClick = request) { Text(str(R.string.perm_grant)) }
        },
    )

    SectionScreen(str(R.string.perm_title), onBack) {
        item {
            PermissionRow(str(R.string.icon_mic), str(R.string.perm_mic_summary), mic) { launcher.launch(Manifest.permission.RECORD_AUDIO) }
            PermissionRow(str(R.string.perm_notifications), str(R.string.perm_notifications_summary), notifications) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            PermissionRow(str(R.string.perm_local_network), str(R.string.perm_local_network_summary), lan) {
                launcher.launch(Permissions.LOCAL_NETWORK)
            }
            PermissionRow(str(R.string.perm_a11y), str(R.string.perm_a11y_summary), a11y) {
                context.startActivity(Permissions.accessibilitySettingsIntent())
            }
            if (!a11y && Permissions.isFileInstall(context)) Note(str(R.string.a11y_restricted_hint))
            Note(str(R.string.perm_note))
            TextButton(onClick = appSettings) { Text(str(R.string.perm_open_settings)) }
        }
    }
}
