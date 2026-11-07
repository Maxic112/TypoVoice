package ru.maximv.typovoice.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import ru.maximv.typovoice.core.settings.AppSettings
import ru.maximv.typovoice.core.settings.SettingsRepository
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.annotation.DrawableRes
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.maximv.typovoice.R
import ru.maximv.typovoice.str

/*
 * Shared building blocks so every settings screen looks the same:
 * a top bar with back, grouped list rows, switches, segmented choices and notes.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionScreen(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = str(R.string.common_back))
                        }
                    }
                },
                actions = { actions() },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
            content = content,
        )
    }
}

@Composable
fun GroupHeader(text: String) {
    Text(
        text,
        Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun Note(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val transparentItem @Composable get() = ListItemDefaults.colors(containerColor = Color.Transparent)

/** Row that opens a sub-screen or runs an action. */
@Composable
fun NavItem(title: String, summary: String? = null, icon: ImageVector? = null, badge: Boolean = false, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title)
                if (badge) UpdateDot(Modifier.padding(start = 8.dp))
            }
        },
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        colors = transparentItem,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Orange dot that marks an available update. */
val UPDATE_DOT_COLOR = Color(0xFFFF8F00)

@Composable
fun UpdateDot(modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(CircleShape).background(UPDATE_DOT_COLOR))
}

/** Static row with optional trailing content (a button, a status). */
@Composable
fun InfoItem(title: String, summary: String? = null, icon: ImageVector? = null, trailing: (@Composable () -> Unit)? = null) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        trailingContent = trailing,
        colors = transparentItem,
    )
}

@Composable
fun SwitchItem(
    title: String,
    summary: String? = null,
    checked: Boolean,
    icon: ImageVector? = null,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = transparentItem,
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}

@Composable
fun RadioItem(title: String, summary: String? = null, selected: Boolean, startPadding: Dp = 0.dp, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        colors = transparentItem,
        modifier = Modifier.selectable(selected, role = Role.RadioButton, onClick = onClick).padding(start = startPadding),
    )
}

/** A settings row with a drop-down select (for more than two options; two options are radio rows). */
@Composable
fun <T> SelectItem(
    label: String,
    options: List<SelectOption<T>>,
    selected: T,
    summary: String? = null,
    startPadding: Dp = 16.dp,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = startPadding, end = 16.dp, top = 8.dp, bottom = 8.dp)) {
        SelectField(label, options, selected, onSelect = onSelect)
        summary?.let {
            Text(
                it,
                Modifier.padding(start = 16.dp, top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun Divider() = HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

/**
 * One option of a [SelectField]: the shown text and an optional second line. [icon] is drawn in the
 * text color; [preview] replaces it with custom content (e.g. the icon on a mini floating button).
 */
data class SelectOption<T>(
    val value: T,
    val label: String,
    val detail: String? = null,
    @DrawableRes val icon: Int? = null,
    val preview: (@Composable () -> Unit)? = null,
) {
    val leading: (@Composable () -> Unit)? get() = preview ?: icon?.let { { Icon(painterResource(it), contentDescription = null) } }
}

/** Compact drop-down select: a read-only field that opens the list of options. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SelectField(
    label: String,
    options: List<SelectOption<T>>,
    selected: T,
    modifier: Modifier = Modifier,
    fallbackText: String = "",
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.value == selected }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = current?.label ?: fallbackText,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            leadingIcon = current?.leading,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(option.label)
                            option.detail?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    // The check sits on the right, so the selected item's text stays in line with the others.
                    leadingIcon = option.leading,
                    trailingIcon = if (option.value == selected) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelect(option.value)
                    },
                    // Extra vertical room: two-line items otherwise run into each other.
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/** Icons of the dictation entry points, shared by the home screen and the launch settings. */
object EntryIcons {
    val overlay get() = Icons.Default.TouchApp
    val tile get() = Icons.Default.GridView
    val volume get() = Icons.AutoMirrored.Filled.VolumeDown
    val launcher get() = Icons.Default.Apps
    val assistant get() = Icons.Default.Assistant
    val widget get() = Icons.Default.Widgets
    val share get() = Icons.Default.Share
}

/** Current settings for a screen: the shared copy, so a screen shows real values from its first frame. */
@Composable
fun SettingsRepository.collectSettings(): AppSettings = state.collectAsState().value ?: AppSettings()
