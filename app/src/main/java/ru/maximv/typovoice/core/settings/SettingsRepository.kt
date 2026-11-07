package ru.maximv.typovoice.core.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import ru.maximv.typovoice.BuildConfig
import ru.maximv.typovoice.core.postprocess.Postprocess
import ru.maximv.typovoice.core.servers.PoolState
import ru.maximv.typovoice.core.servers.SelectionPolicy
import ru.maximv.typovoice.core.servers.ServerConfig
import ru.maximv.typovoice.delivery.Spacing
import java.util.UUID

enum class RecordingMode {
    /** Hold the button while speaking. A quick tap still toggles. */
    PUSH_TO_TALK,

    /** Tap to start, tap again to stop. */
    TOGGLE,
}

enum class OverlayMode { OFF, ALWAYS, ON_TEXT_FOCUS }

enum class VolumeKeyMode { OFF, VOLUME_DOWN, VOLUME_UP }

/** What a gesture on the floating button does. */
enum class BubbleAction {
    /** Tap starts recording, the next tap sends it. */
    TOGGLE_RECORDING,

    /** Records while the finger is down; only for the hold gesture. */
    PUSH_TO_TALK,
    POSTPROCESS_MENU,
    MODEL_MENU,
    OPEN_APP,
    NONE,
}

/** Icon drawn on the floating button; [WAVE] and [PULSE] follow the voice, so they are only for recording. */
enum class BubbleIcon {
    MIC,
    VOICE,
    HEARING,
    /** TypoVoice mark in brand colors. */
    MARK_COLOR,
    /** TypoVoice mark in white with translucent bars. */
    MARK_WHITE,
    WAVE,
    PULSE,
    ;

    val dynamic get() = this == WAVE || this == PULSE
}

/** How the chosen volume key starts and stops a recording. */
enum class VolumeTrigger {
    /** Every press starts or stops; the key no longer changes the volume. */
    PRESS,

    /** A double press starts or stops; a single press still changes the volume. */
    DOUBLE_PRESS,

    /** Records while held, sends on release; a short press changes the volume. */
    HOLD,

    /** One hold starts, the next hold stops and sends; a short press changes the volume. */
    HOLD_TOGGLE,
}

data class BubbleSettings(
    val tap: BubbleAction = BubbleAction.TOGGLE_RECORDING,
    val doubleTap: BubbleAction = BubbleAction.POSTPROCESS_MENU,
    val hold: BubbleAction = BubbleAction.PUSH_TO_TALK,
    val idleIcon: BubbleIcon = BubbleIcon.MARK_COLOR,
    val recordingIcon: BubbleIcon = BubbleIcon.WAVE,
)

/** Which dictations stop by themselves after a pause. Held (push-to-talk) recordings never do. */
enum class AutoStopMode { OFF, ASSISTANT_ONLY, ALL_TAPS }

enum class InsertMethod {
    /** Clipboard + the field's own "Paste": native insertion at the cursor; the text stays in the clipboard. */
    PASTE,

    /** Rewrite the whole field content; keeps the clipboard but trips over placeholders in some apps. */
    SET_TEXT,
}

data class AppSettings(
    /** Master switch: when false the app does nothing at all. */
    val enabled: Boolean = true,
    val servers: List<ServerConfig> = emptyList(),
    val policy: SelectionPolicy = SelectionPolicy.FIXED,
    val fixedServerId: String? = null,
    val recordingMode: RecordingMode = RecordingMode.PUSH_TO_TALK,
    val overlayMode: OverlayMode = OverlayMode.ON_TEXT_FOCUS,
    val bubble: BubbleSettings = BubbleSettings(),
    val insertMethod: InsertMethod = InsertMethod.PASTE,
    val spacing: Spacing = Spacing(),
    val volumeKey: VolumeKeyMode = VolumeKeyMode.OFF,
    val volumeTrigger: VolumeTrigger = VolumeTrigger.HOLD,
    /** Recording stops by itself after this many minutes; 0 = no limit. Guards against a forgotten recording. */
    val maxRecordingMinutes: Int = 10,
    /** Separate "Dictation" launcher icon, used to bind the side key. */
    val launcherIcon: Boolean = false,
    /** Pause that ends a phrase: system voice input and auto-stopping dictation. */
    val recognitionSilenceMs: Long = 1_500,
    val autoStop: AutoStopMode = AutoStopMode.ASSISTANT_ONLY,
    val historyEnabled: Boolean = true,
    /** Quick retry of 429/502 during dictation and the background retry queue for failed recordings. */
    val autoRetry: Boolean = true,
    /** Background attempts per failed recording. */
    val retryAttempts: Int = 3,
    val soundFeedback: Boolean = false,
    val vibrationFeedback: Boolean = true,
    /** Quick post-processing override, see [ru.maximv.typovoice.core.postprocess.Postprocess]. */
    val postprocessChoice: String? = null,
    /** Preset names the first enabled server lets the key choose, for the quick menu. */
    val cachedPresets: List<String> = emptyList(),
    /** Handle the "Speak…" popup (RecognizerIntent) too. */
    val voiceInputPopup: Boolean = false,
    val tileEnabled: Boolean = true,
    /** Whether the tile is placed in Quick Settings, as last reported by the system to the tile service. */
    val tileInPanel: Boolean = false,
    val poolState: PoolState = PoolState(),
)

private val Context.dataStore by preferencesDataStore("settings")

class SettingsRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /*
     * One shared copy, read off the main thread: reading decrypts API keys through Android Keystore,
     * which is too slow to repeat for every screen and service that listens to the settings.
     */
    private val shared: StateFlow<AppSettings?> = context.dataStore.data
        .map(::read)
        .stateIn(scope, SharingStarted.Eagerly, null)

    val settings: Flow<AppSettings> = shared.filterNotNull()

    /** Latest settings for the UI, available at once on every screen; null only until the first read. */
    val state: StateFlow<AppSettings?> = shared

    /** Fresh from storage, for read-modify-write updates. */
    suspend fun current(): AppSettings = withContext(Dispatchers.Default) { read(context.dataStore.data.first()) }

    suspend fun saveServers(servers: List<ServerConfig>) = context.dataStore.edit {
        val stored = servers.map { it.copy(apiKey = SecretCipher.encrypt(it.apiKey)) }
        it[Keys.SERVERS] = json.encodeToString(stored)
    }

    suspend fun upsertServer(server: ServerConfig) {
        val servers = current().servers
        val updated = if (servers.any { it.id == server.id }) {
            servers.map { if (it.id == server.id) server else it }
        } else {
            servers + server
        }
        saveServers(updated)
    }

    suspend fun deleteServer(id: String) = saveServers(current().servers.filterNot { it.id == id })

    suspend fun setPolicy(policy: SelectionPolicy, fixedServerId: String?) = context.dataStore.edit {
        it[Keys.POLICY] = policy.name
        if (fixedServerId != null) it[Keys.FIXED_SERVER] = fixedServerId else it.remove(Keys.FIXED_SERVER)
    }

    suspend fun setRecordingMode(mode: RecordingMode) = context.dataStore.edit { it[Keys.RECORDING_MODE] = mode.name }

    suspend fun setOverlayMode(mode: OverlayMode) = context.dataStore.edit { it[Keys.OVERLAY_MODE] = mode.name }

    suspend fun setBubble(bubble: BubbleSettings) = context.dataStore.edit {
        it[Keys.BUBBLE_TAP] = bubble.tap.name
        it[Keys.BUBBLE_DOUBLE_TAP] = bubble.doubleTap.name
        it[Keys.BUBBLE_HOLD] = bubble.hold.name
        it[Keys.BUBBLE_IDLE_ICON] = bubble.idleIcon.name
        it[Keys.BUBBLE_RECORDING_ICON] = bubble.recordingIcon.name
    }

    suspend fun setInsertMethod(method: InsertMethod) = context.dataStore.edit { it[Keys.INSERT_METHOD] = method.name }

    suspend fun setSpacing(spacing: Spacing) = context.dataStore.edit {
        it[Keys.SPACE_BEFORE] = spacing.before
        it[Keys.SPACE_AFTER] = spacing.after
    }

    suspend fun setEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.ENABLED] = enabled }

    suspend fun setVolumeKey(mode: VolumeKeyMode) = context.dataStore.edit { it[Keys.VOLUME_KEY] = mode.name }

    suspend fun setVolumeTrigger(trigger: VolumeTrigger) = context.dataStore.edit { it[Keys.VOLUME_TRIGGER] = trigger.name }

    suspend fun setMaxRecordingMinutes(minutes: Int) = context.dataStore.edit { it[Keys.MAX_RECORDING_MINUTES] = minutes }

    suspend fun setLauncherIcon(enabled: Boolean) = context.dataStore.edit { it[Keys.LAUNCHER_ICON] = enabled }

    suspend fun setRecognitionSilence(ms: Long) = context.dataStore.edit { it[Keys.RECOGNITION_SILENCE] = ms }

    suspend fun setAutoStop(mode: AutoStopMode) = context.dataStore.edit { it[Keys.AUTO_STOP] = mode.name }

    suspend fun setPostprocessChoice(choice: String?) = context.dataStore.edit {
        if (choice != null) it[Keys.POSTPROCESS] = choice else it.remove(Keys.POSTPROCESS)
    }

    suspend fun setCachedPresets(presets: List<String>) = context.dataStore.edit { it[Keys.PRESETS] = presets.joinToString("\n") }

    suspend fun setAutoRetry(enabled: Boolean) = context.dataStore.edit { it[Keys.AUTO_RETRY] = enabled }

    suspend fun setRetryAttempts(count: Int) = context.dataStore.edit { it[Keys.RETRY_ATTEMPTS] = count }

    suspend fun setSoundFeedback(enabled: Boolean) = context.dataStore.edit { it[Keys.SOUND] = enabled }

    suspend fun setVibrationFeedback(enabled: Boolean) = context.dataStore.edit { it[Keys.VIBRATION] = enabled }

    suspend fun setHistoryEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.HISTORY] = enabled }

    suspend fun setVoiceInputPopup(enabled: Boolean) = context.dataStore.edit { it[Keys.VOICE_INPUT_POPUP] = enabled }

    suspend fun setTileEnabled(enabled: Boolean) = context.dataStore.edit {
        it[Keys.TILE_ENABLED] = enabled
        // Disabling the tile component removes it from the panel.
        if (!enabled) it[Keys.TILE_IN_PANEL] = false
    }

    suspend fun setTileInPanel(inPanel: Boolean) = context.dataStore.edit { it[Keys.TILE_IN_PANEL] = inPanel }

    suspend fun setPoolState(state: PoolState) = context.dataStore.edit {
        state.lastSuccessfulId?.let { id -> it[Keys.LAST_SUCCESSFUL] = id }
        it[Keys.RR_CURSOR] = state.roundRobinCursor
    }

    /** Debug builds come with the dev server from local.properties so the app works right after install. */
    suspend fun seedDevServerIfEmpty() {
        if (BuildConfig.DEV_SERVER_URL.isBlank()) return
        if (context.dataStore.data.first()[Keys.SERVERS] != null) return
        saveServers(
            listOf(
                ServerConfig(
                    id = UUID.randomUUID().toString(),
                    name = BuildConfig.DEV_SERVER_NAME.ifBlank { "Dev" },
                    baseUrl = BuildConfig.DEV_SERVER_URL,
                    apiKey = BuildConfig.DEV_SERVER_KEY,
                ),
            ),
        )
    }

    private fun read(prefs: Preferences): AppSettings {
        val servers = prefs[Keys.SERVERS]
            ?.let { runCatching { json.decodeFromString<List<ServerConfig>>(it) }.getOrNull() }
            .orEmpty()
            .map { it.copy(apiKey = SecretCipher.decrypt(it.apiKey)) }
        val recordingMode = enumOr(prefs[Keys.RECORDING_MODE], RecordingMode.PUSH_TO_TALK)
        // Before gesture settings the hold followed the shared "button mode".
        val holdDefault = if (recordingMode == RecordingMode.PUSH_TO_TALK) BubbleAction.PUSH_TO_TALK else BubbleAction.TOGGLE_RECORDING
        val defaults = BubbleSettings()
        return AppSettings(
            enabled = prefs[Keys.ENABLED] ?: true,
            servers = servers,
            policy = enumOr(prefs[Keys.POLICY], SelectionPolicy.FIXED),
            fixedServerId = prefs[Keys.FIXED_SERVER],
            recordingMode = recordingMode,
            overlayMode = enumOr(prefs[Keys.OVERLAY_MODE], OverlayMode.ON_TEXT_FOCUS),
            bubble = BubbleSettings(
                tap = enumOr(prefs[Keys.BUBBLE_TAP], defaults.tap),
                doubleTap = enumOr(prefs[Keys.BUBBLE_DOUBLE_TAP], defaults.doubleTap),
                hold = enumOr(prefs[Keys.BUBBLE_HOLD], holdDefault),
                idleIcon = enumOr(prefs[Keys.BUBBLE_IDLE_ICON], defaults.idleIcon).takeUnless { it.dynamic } ?: defaults.idleIcon,
                recordingIcon = enumOr(prefs[Keys.BUBBLE_RECORDING_ICON], defaults.recordingIcon),
            ),
            insertMethod = enumOr(prefs[Keys.INSERT_METHOD], InsertMethod.PASTE),
            volumeKey = enumOr(prefs[Keys.VOLUME_KEY], VolumeKeyMode.OFF),
            volumeTrigger = enumOr(prefs[Keys.VOLUME_TRIGGER], VolumeTrigger.HOLD),
            maxRecordingMinutes = prefs[Keys.MAX_RECORDING_MINUTES] ?: 10,
            launcherIcon = prefs[Keys.LAUNCHER_ICON] ?: false,
            recognitionSilenceMs = prefs[Keys.RECOGNITION_SILENCE] ?: 1_500,
            voiceInputPopup = prefs[Keys.VOICE_INPUT_POPUP] ?: false,
            autoStop = enumOr(prefs[Keys.AUTO_STOP], AutoStopMode.ASSISTANT_ONLY),
            historyEnabled = prefs[Keys.HISTORY] ?: true,
            autoRetry = prefs[Keys.AUTO_RETRY] ?: true,
            retryAttempts = prefs[Keys.RETRY_ATTEMPTS] ?: 3,
            soundFeedback = prefs[Keys.SOUND] ?: false,
            vibrationFeedback = prefs[Keys.VIBRATION] ?: true,
            postprocessChoice = prefs[Keys.POSTPROCESS],
            cachedPresets = prefs[Keys.PRESETS]?.split('\n')?.filter { it.isNotBlank() }.orEmpty(),
            spacing = Spacing(before = prefs[Keys.SPACE_BEFORE] ?: true, after = prefs[Keys.SPACE_AFTER] ?: false),
            tileEnabled = prefs[Keys.TILE_ENABLED] ?: true,
            tileInPanel = prefs[Keys.TILE_IN_PANEL] ?: false,
            poolState = PoolState(prefs[Keys.LAST_SUCCESSFUL], prefs[Keys.RR_CURSOR] ?: 0),
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default

    private object Keys {
        val SERVERS = stringPreferencesKey("servers")
        val POLICY = stringPreferencesKey("policy")
        val FIXED_SERVER = stringPreferencesKey("fixed_server")
        val RECORDING_MODE = stringPreferencesKey("recording_mode")
        val OVERLAY_MODE = stringPreferencesKey("overlay_mode")
        val BUBBLE_TAP = stringPreferencesKey("bubble_tap")
        val BUBBLE_DOUBLE_TAP = stringPreferencesKey("bubble_double_tap")
        val BUBBLE_HOLD = stringPreferencesKey("bubble_hold")
        val BUBBLE_IDLE_ICON = stringPreferencesKey("bubble_idle_icon")
        val BUBBLE_RECORDING_ICON = stringPreferencesKey("bubble_recording_icon")
        val INSERT_METHOD = stringPreferencesKey("insert_method")
        val VOLUME_KEY = stringPreferencesKey("volume_key")
        val VOLUME_TRIGGER = stringPreferencesKey("volume_trigger")
        val MAX_RECORDING_MINUTES = intPreferencesKey("max_recording_minutes")
        val LAUNCHER_ICON = booleanPreferencesKey("launcher_icon")
        val RECOGNITION_SILENCE = longPreferencesKey("recognition_silence_ms")
        val VOICE_INPUT_POPUP = booleanPreferencesKey("voice_input_popup")
        val AUTO_STOP = stringPreferencesKey("auto_stop")
        val HISTORY = booleanPreferencesKey("history_enabled")
        val AUTO_RETRY = booleanPreferencesKey("auto_retry")
        val RETRY_ATTEMPTS = intPreferencesKey("retry_attempts")
        val SOUND = booleanPreferencesKey("sound_feedback")
        val VIBRATION = booleanPreferencesKey("vibration_feedback")
        val POSTPROCESS = stringPreferencesKey("postprocess_choice")
        val PRESETS = stringPreferencesKey("cached_presets")
        val SPACE_BEFORE = booleanPreferencesKey("space_before")
        val SPACE_AFTER = booleanPreferencesKey("space_after")
        val TILE_ENABLED = booleanPreferencesKey("tile_enabled")
        val TILE_IN_PANEL = booleanPreferencesKey("tile_in_panel")
        val ENABLED = booleanPreferencesKey("enabled")
        val LAST_SUCCESSFUL = stringPreferencesKey("last_successful_server")
        val RR_CURSOR = intPreferencesKey("round_robin_cursor")
    }
}
