package ru.maximv.typovoice.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import ru.maximv.typovoice.AppContainer
import ru.maximv.typovoice.ui.collectSettings
import ru.maximv.typovoice.core.settings.AppSettings
import ru.maximv.typovoice.core.settings.InsertMethod
import ru.maximv.typovoice.ui.GroupHeader
import ru.maximv.typovoice.ui.RadioItem
import ru.maximv.typovoice.ui.SectionScreen
import ru.maximv.typovoice.ui.SwitchItem
import ru.maximv.typovoice.R
import ru.maximv.typovoice.str

@Composable
fun InsertScreen(container: AppContainer, onBack: () -> Unit) {
    val s = container.settings.collectSettings()
    val scope = rememberCoroutineScope()

    SectionScreen(str(R.string.insert_title), onBack) {
        item {
            GroupHeader(str(R.string.insert_method))
            RadioItem(
                str(R.string.insert_clipboard),
                str(R.string.insert_clipboard_summary),
                s.insertMethod == InsertMethod.PASTE,
            ) { scope.launch { container.settings.setInsertMethod(InsertMethod.PASTE) } }
            RadioItem(
                str(R.string.insert_set_text),
                str(R.string.insert_set_text_summary),
                s.insertMethod == InsertMethod.SET_TEXT,
            ) { scope.launch { container.settings.setInsertMethod(InsertMethod.SET_TEXT) } }
        }
        item {
            GroupHeader(str(R.string.insert_spaces))
            SwitchItem(str(R.string.insert_space_before), str(R.string.insert_space_before_summary), s.spacing.before) {
                scope.launch { container.settings.setSpacing(s.spacing.copy(before = it)) }
            }
            SwitchItem(str(R.string.insert_space_after), str(R.string.insert_space_after_summary), s.spacing.after) {
                scope.launch { container.settings.setSpacing(s.spacing.copy(after = it)) }
            }
        }
    }
}
