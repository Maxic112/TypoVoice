package ru.maximv.typovoice.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import ru.maximv.typovoice.a11y.TypoVoiceAccessibilityService

object Permissions {
    fun hasMicrophone(context: Context) = granted(context, Manifest.permission.RECORD_AUDIO)

    fun hasNotifications(context: Context) = granted(context, Manifest.permission.POST_NOTIFICATIONS)

    /** Android 17 gates LAN access behind a runtime permission. */
    const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

    fun hasLocalNetwork(context: Context) = granted(context, LOCAL_NETWORK)

    fun isAccessibilityEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val component = ComponentName(context, TypoVoiceAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
    }

    fun accessibilitySettingsIntent() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun appDetailsIntent(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    /**
     * Android 13+ blocks accessibility services of apps installed from an APK file (browser download, file
     * manager) until the user taps "Allow restricted settings" in App info. Store and adb installs are not
     * affected, so the hint is shown only for file installs.
     */
    fun isFileInstall(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val source = runCatching { context.packageManager.getInstallSourceInfo(context.packageName).packageSource }
            .getOrNull() ?: return false
        return source == PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE || source == PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE
    }

    /**
     * Whether TypoVoice is the default speech recognizer; null if the system doesn't let us read it.
     * The setting key is not public API, so this is best effort.
     */
    fun isDefaultVoiceInput(context: Context): Boolean? = runCatching {
        val current = Settings.Secure.getString(context.contentResolver, "voice_recognition_service") ?: return@runCatching null
        ComponentName.unflattenFromString(current)?.packageName == context.packageName
    }.getOrNull()

    fun isAssistant(context: Context): Boolean =
        context.getSystemService(android.app.role.RoleManager::class.java).isRoleHeld(android.app.role.RoleManager.ROLE_ASSISTANT)

    fun voiceInputSettingsIntent(context: Context): Intent {
        val voiceInput = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
        return if (voiceInput.resolveActivity(context.packageManager) != null) {
            voiceInput
        } else {
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        }
    }

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
