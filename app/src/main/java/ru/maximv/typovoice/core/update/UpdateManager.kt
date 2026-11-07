package ru.maximv.typovoice.core.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import androidx.core.content.IntentCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.maximv.typovoice.BuildConfig
import ru.maximv.typovoice.R
import ru.maximv.typovoice.TypoVoiceApp
import ru.maximv.typovoice.str
import java.io.File
import java.util.concurrent.TimeUnit

private val Context.updateStore by preferencesDataStore("updates")

/** The newest GitHub release. */
@Serializable
data class Release(
    val version: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String?,
    val apkSize: Long,
)

data class UpdateState(
    val autoCheck: Boolean = true,
    /** Last successful check, epoch millis; 0 = never. */
    val checkedAt: Long = 0,
    val latest: Release? = null,
) {
    /** The latest release when it is newer than the installed version. */
    val available: Release? get() = latest?.takeIf { UpdateManager.isNewer(it.version, UpdateManager.currentVersion) }
}

sealed interface DownloadState {
    data object Idle : DownloadState
    /** [progress] is 0..1, or null while the size is unknown. */
    data class Downloading(val progress: Float?) : DownloadState
    data object Installing : DownloadState
    data class Failed(val message: String) : DownloadState
}

/**
 * Updates from GitHub Releases of [BuildConfig.UPDATE_REPO]: a daily background check (the
 * result drives the orange dots in the UI), then download of the release APK and installation
 * through the system installer, which asks the user to confirm.
 */
class UpdateManager(private val context: Context) {
    private val http = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()
    private val downloadHttp = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }

    /** Releases come from this GitHub repository ("owner/name"); empty until the project is published. */
    val repo: String = BuildConfig.UPDATE_REPO
    val configured: Boolean get() = repo.isNotBlank()

    val state: StateFlow<UpdateState> = context.updateStore.data
        .map { prefs ->
            UpdateState(
                autoCheck = prefs[Keys.AUTO_CHECK] ?: true,
                checkedAt = prefs[Keys.CHECKED_AT] ?: 0,
                latest = prefs[Keys.LATEST]?.let { runCatching { json.decodeFromString<Release>(it) }.getOrNull() },
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, UpdateState())

    private val _download = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val download: StateFlow<DownloadState> = _download.asStateFlow()

    /** Asks GitHub for the latest release and stores it; returns the release when it is newer. */
    suspend fun check(): Release? = withContext(Dispatchers.IO) {
        check(configured) { str(R.string.update_not_configured) }
        val request = Request.Builder()
            .url("https://api.github.com/repos/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        val body = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error(str(R.string.update_check_failed_code, response.code))
            response.body.string()
        }
        val gh = json.decodeFromString<GhRelease>(body)
        val apk = gh.assets.firstOrNull { it.name.endsWith(".apk") }
        val release = Release(
            version = gh.tagName.removePrefix("v"),
            notes = gh.body.orEmpty().trim(),
            pageUrl = gh.htmlUrl,
            apkUrl = apk?.url,
            apkSize = apk?.size ?: 0,
        )
        context.updateStore.edit {
            it[Keys.LATEST] = json.encodeToString(Release.serializer(), release)
            it[Keys.CHECKED_AT] = System.currentTimeMillis()
        }
        release.takeIf { isNewer(it.version, currentVersion) }
    }

    suspend fun setAutoCheck(enabled: Boolean) {
        context.updateStore.edit { it[Keys.AUTO_CHECK] = enabled }
        scheduleNow(enabled)
    }

    /** Daily background check while auto-check is on; called on every app start. */
    fun schedule(enabled: Boolean? = null) {
        scope.launch { scheduleNow(enabled) }
    }

    private suspend fun scheduleNow(enabled: Boolean?) {
        val on = (enabled ?: context.updateStore.data.first()[Keys.AUTO_CHECK] ?: true) && configured
        val work = WorkManager.getInstance(context)
        if (on) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        } else {
            work.cancelUniqueWork(WORK_NAME)
        }
    }

    /** Android asks once per installer app whether it may install APKs. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /** Downloads the release APK and hands it to the system installer. */
    suspend fun downloadAndInstall(release: Release) {
        val url = release.apkUrl ?: run {
            _download.value = DownloadState.Failed(str(R.string.update_no_apk))
            return
        }
        try {
            _download.value = DownloadState.Downloading(null)
            val file = withContext(Dispatchers.IO) { downloadApk(url, release) }
            _download.value = DownloadState.Installing
            withContext(Dispatchers.IO) { install(file) }
        } catch (e: Exception) {
            _download.value = DownloadState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun downloadApk(url: String, release: Release): File {
        val dir = File(context.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val file = File(dir, "TypoVoice-${release.version}.apk")
        downloadHttp.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) error(str(R.string.update_download_failed_code, response.code))
            val body = response.body
            val total = body.contentLength().takeIf { it > 0 } ?: release.apkSize.takeIf { it > 0 }
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        _download.value = DownloadState.Downloading(total?.let { (done.toFloat() / it).coerceAtMost(1f) })
                    }
                }
            }
        }
        return file
    }

    private fun install(file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            file.inputStream().use { input ->
                session.openWrite("base.apk", 0, file.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val callback = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(callback.intentSender)
        }
    }

    internal fun onInstallResult(status: Int, message: String?) {
        _download.value = when (status) {
            PackageInstaller.STATUS_SUCCESS -> DownloadState.Idle
            PackageInstaller.STATUS_FAILURE_ABORTED -> DownloadState.Idle
            else -> DownloadState.Failed(message ?: str(R.string.update_install_failed))
        }
    }

    private object Keys {
        val AUTO_CHECK = booleanPreferencesKey("auto_check")
        val CHECKED_AT = longPreferencesKey("checked_at")
        val LATEST = stringPreferencesKey("latest_release")
    }

    companion object {
        private const val WORK_NAME = "update-check"

        /** Installed version without the build suffix ("0.1.0-debug" → "0.1.0"). */
        val currentVersion: String get() = BuildConfig.VERSION_NAME.substringBefore('-')

        /** Compares dotted numeric versions: "0.10.0" is newer than "0.9.3". */
        fun isNewer(candidate: String, current: String): Boolean {
            val a = candidate.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val b = current.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}

@Serializable
private data class GhRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
    val body: String? = null,
    val assets: List<GhAsset> = emptyList(),
)

@Serializable
private data class GhAsset(
    val name: String,
    @SerialName("browser_download_url") val url: String,
    val size: Long = 0,
)

/** Daily background check; the stored result lights the update dots. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        runCatching { TypoVoiceApp.container.updates.check() }.fold({ Result.success() }, { Result.retry() })
}

/** Status of the install session: shows the system confirmation, reports failures. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)?.let {
                context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return
        }
        TypoVoiceApp.container.updates.onInstallResult(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
    }
}
