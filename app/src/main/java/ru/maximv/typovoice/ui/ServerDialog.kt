package ru.maximv.typovoice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import ru.maximv.typovoice.core.api.PresetList
import ru.maximv.typovoice.core.api.ServerEndpoint
import ru.maximv.typovoice.core.api.SttApiClient
import ru.maximv.typovoice.core.api.SttModel
import ru.maximv.typovoice.core.api.TranscriptionRequest
import ru.maximv.typovoice.core.servers.ServerConfig
import java.util.UUID
import ru.maximv.typovoice.R
import ru.maximv.typovoice.str

/**
 * Add/edit a transcribe-text-server. "Check" calls `/extv1/health`, `/models` and `/extv1/presets`
 * and offers what the key may use as choices.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServerDialog(
    initial: ServerConfig?,
    api: SttApiClient,
    onDismiss: () -> Unit,
    onSave: (ServerConfig) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var url by remember { mutableStateOf(initial?.baseUrl ?: "https://") }
    var key by remember { mutableStateOf(initial?.apiKey.orEmpty()) }
    var model by remember { mutableStateOf(initial?.model) }
    var language by remember { mutableStateOf(initial?.language.orEmpty()) }
    var initialPrompt by remember { mutableStateOf(initial?.initialPrompt.orEmpty()) }
    var vad by remember { mutableStateOf(initial?.vad ?: false) }
    var stream by remember { mutableStateOf(initial?.stream ?: false) }
    var preset by remember { mutableStateOf(initial?.preset) }
    var customPrompt by remember { mutableStateOf(initial?.customPrompt.orEmpty()) }
    var models by remember { mutableStateOf<List<SttModel>>(emptyList()) }
    var presets by remember { mutableStateOf<PresetList?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun check() {
        status = str(R.string.sd_checking)
        status = try {
            val health = api.health(url)
            val endpoint = ServerEndpoint(url, key)
            models = api.models(endpoint)
            presets = api.presets(endpoint)
            val current = models.firstOrNull { it.aliasOf == null && it.loaded } ?: models.firstOrNull { it.isDefault && !it.isAlias }
            buildString {
                append(if (health.status == "ok") str(R.string.sd_ok) else str(R.string.sd_degraded))
                append(str(R.string.sd_engine, workerLabel(health.worker)))
                current?.let { append(str(R.string.sd_current_model, it.name)) }
            }
        } catch (e: Exception) {
            e.message ?: str(R.string.common_error)
        }
    }

    // An existing server: show its models and presets right away.
    LaunchedEffect(Unit) {
        if (initial != null && initial.apiKey.isNotBlank()) check()
    }

    // Full-screen editor: a fixed-size window doesn't re-measure (and jump) while typing,
    // and the button row stays above the keyboard.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Text(
                    if (initial == null) str(R.string.sd_new) else str(R.string.common_server),
                    Modifier.padding(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(str(R.string.sd_name)) }, singleLine = true)
                    OutlinedTextField(
                        url,
                        { url = it },
                        Modifier.fillMaxWidth(),
                        label = { Text(str(R.string.sd_url)) },
                        supportingText = { Text(str(R.string.sd_url_hint, SttApiClient.API_PATH)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        key,
                        { key = it },
                        Modifier.fillMaxWidth(),
                        label = { Text(str(R.string.sd_key)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    OutlinedButton(onClick = { scope.launch { check() } }) { Text(str(R.string.sd_check)) }
                    status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

                    Section(str(R.string.sd_recognition))
                    ModelChoice(models, model) { model = it }
                    LanguageField(language) { language = it }
                    OutlinedTextField(
                        initialPrompt,
                        { initialPrompt = it },
                        Modifier.fillMaxWidth(),
                        label = { Text(str(R.string.sd_prompt)) },
                        placeholder = { Text(str(R.string.sd_prompt_hint)) },
                        minLines = 2,
                        maxLines = 5,
                    )
                    Hint(str(R.string.sd_params_note))
                    SwitchRow(
                        str(R.string.sd_vad),
                        str(R.string.sd_vad_hint),
                        vad,
                    ) { vad = it }
                    val selected = models.firstOrNull { it.id == model }
                        ?: models.firstOrNull { it.aliasOf == null && it.loaded }
                    SwitchRow(
                        str(R.string.sd_stream),
                        when (selected?.streaming) {
                            true -> str(R.string.sd_stream_yes)
                            false -> str(R.string.sd_stream_no)
                            null -> str(R.string.sd_stream_hint)
                        },
                        stream,
                    ) { stream = it }

                    Section(str(R.string.common_postprocess))
                    PresetChoice(presets, preset) { preset = it }
                    CustomPromptField(customPrompt) { customPrompt = it }
                    if (presets?.canCustomPrompt == false && customPrompt.isNotBlank()) {
                        Warning(str(R.string.sd_custom_forbidden))
                    }
                    if (preset == TranscriptionRequest.PRESET_NONE && customPrompt.isNotBlank()) {
                        Warning(str(R.string.sd_custom_needs_preset))
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text(str(R.string.common_cancel)) }
                    onDelete?.let {
                        TextButton(
                            onClick = it,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text(str(R.string.common_delete)) }
                    }
                    TextButton(
                        enabled = url.isNotBlank() && key.isNotBlank(),
                        onClick = {
                            val baseUrl = SttApiClient.normalizeBaseUrl(url)
                            onSave(
                                ServerConfig(
                                    id = initial?.id ?: UUID.randomUUID().toString(),
                                    name = name.ifBlank { baseUrl },
                                    baseUrl = baseUrl,
                                    apiKey = key.trim(),
                                    enabled = initial?.enabled ?: true,
                                    model = model,
                                    preset = preset,
                                    customPrompt = customPrompt.trim().ifEmpty { null },
                                    language = language.trim().lowercase().ifEmpty { null },
                                    initialPrompt = initialPrompt.trim().ifEmpty { null },
                                    vad = vad,
                                    stream = stream,
                                ),
                            )
                        },
                    ) { Text(str(R.string.common_save)) }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun Hint(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Warning(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)

@Composable
private fun SwitchRow(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Hint(summary)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** null = whatever model the server has now (alias `default`); otherwise a real model id. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelChoice(models: List<SttModel>, selected: String?, onSelect: (String?) -> Unit) {
    val real = models.filter { !it.isAlias }
    val saved = selected?.takeIf { id -> real.none { it.id == id } }
    Text(str(R.string.common_model), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text(str(R.string.model_server_current)) })
        real.forEach { model ->
            FilterChip(
                selected = selected == model.id,
                onClick = { onSelect(model.id) },
                label = { Text(if (model.loaded) str(R.string.model_loaded, model.name) else model.name) },
            )
        }
        saved?.let { FilterChip(selected = true, onClick = {}, label = { Text(it) }) }
    }
    if (models.isEmpty()) Hint(str(R.string.sd_models_hint))
    val chosen = real.firstOrNull { it.id == selected }
    if (chosen != null && !chosen.loaded) {
        Hint(str(R.string.sd_model_switch_note))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageField(value: String, onChange: (String) -> Unit) {
    Text(str(R.string.common_language), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("" to str(R.string.sd_lang_auto), "ru" to "Русский", "en" to "English").forEach { (code, label) ->
            FilterChip(selected = value.trim() == code, onClick = { onChange(code) }, label = { Text(label) })
        }
    }
    OutlinedTextField(
        value,
        { onChange(it.trim().lowercase()) },
        Modifier.fillMaxWidth(),
        label = { Text(str(R.string.sd_lang_code)) },
        placeholder = { Text(str(R.string.sd_lang_auto_lower)) },
        supportingText = { Text(str(R.string.sd_lang_hint)) },
        isError = value.isNotBlank() && !LANGUAGE_CODE.matches(value.trim()),
        singleLine = true,
    )
}

/** null = the key's default preset; [TranscriptionRequest.PRESET_NONE] = off; otherwise a preset name. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetChoice(list: PresetList?, selected: String?, onSelect: (String?) -> Unit) {
    val names = list?.takeIf { it.canChoose }?.presets?.map { it.name }.orEmpty()
    val saved = selected?.takeIf { it != TranscriptionRequest.PRESET_NONE && it !in names }
    val defaultLabel = list?.let { str(R.string.sd_preset_default, it.defaultPreset ?: str(R.string.common_none)) } ?: str(R.string.sd_preset_key_default)
    Text(str(R.string.sd_preset), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text(defaultLabel) })
        FilterChip(
            selected = selected == TranscriptionRequest.PRESET_NONE,
            onClick = { onSelect(TranscriptionRequest.PRESET_NONE) },
            label = { Text(str(R.string.pp_none)) },
        )
        names.forEach { name ->
            FilterChip(selected = selected == name, onClick = { onSelect(name) }, label = { Text(name) })
        }
        saved?.let { FilterChip(selected = true, onClick = {}, label = { Text(it) }) }
    }
    if (list != null && !list.canChoose) {
        Hint(str(R.string.sd_preset_forbidden))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomPromptField(value: String, onChange: (String) -> Unit) {
    val tooltip = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    // TextFieldValue keeps the cursor, so the placeholder chip inserts where the user is typing.
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(str(R.string.pp_custom), style = MaterialTheme.typography.labelLarge)
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = {
                RichTooltip(title = { Text(str(R.string.sd_custom_prompt)) }) {
                    Text(
                        str(R.string.sd_custom_help, PLACEHOLDER),
                    )
                }
            },
            state = tooltip,
        ) {
            IconButton(onClick = { scope.launch { tooltip.show() } }) {
                Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = str(R.string.sd_help))
            }
        }
        Spacer(Modifier.weight(1f))
        // Looks like the template variable itself; tap inserts it at the cursor.
        SuggestionChip(
            onClick = {
                val start = minOf(field.selection.start, field.selection.end)
                val end = maxOf(field.selection.start, field.selection.end)
                val text = field.text.replaceRange(start, end, PLACEHOLDER)
                field = TextFieldValue(text, TextRange(start + PLACEHOLDER.length))
                onChange(text)
            },
            label = { Text(PLACEHOLDER, fontFamily = FontFamily.Monospace) },
            modifier = Modifier.semantics { contentDescription = str(R.string.sd_insert_placeholder, PLACEHOLDER) },
        )
    }
    OutlinedTextField(
        value = field,
        onValueChange = {
            field = it
            onChange(it.text)
        },
        placeholder = { Text(str(R.string.sd_prompt_template, PLACEHOLDER)) },
        minLines = 3,
        maxLines = 8,
        modifier = Modifier.fillMaxWidth(),
    )
}

private const val PLACEHOLDER = ServerConfig.TEXT_PLACEHOLDER
private val LANGUAGE_CODE = Regex("[a-z]{2,3}")
