package ru.maximv.typovoice.ui.settings

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ru.maximv.typovoice.R
import ru.maximv.typovoice.str
import ru.maximv.typovoice.ui.Note
import ru.maximv.typovoice.ui.RadioItem
import ru.maximv.typovoice.ui.SectionScreen

/**
 * App language on top of the system one (Android per-app languages). By default the app follows
 * the system: Russian on a Russian system, English otherwise.
 */
object AppLanguage {
    /** Language tags the app is translated to, with their own names. */
    val choices = listOf("en" to "English", "ru" to "Русский")

    private val supported get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** Chosen language tag, or "" when the app follows the system. */
    fun current(context: Context): String {
        if (!supported) return ""
        val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
        return if (locales.isEmpty) "" else locales[0].language
    }

    /** Applies the language; Android recreates the activities in it. */
    fun set(context: Context, tag: String) {
        if (!supported) return
        context.getSystemService(LocaleManager::class.java).applicationLocales =
            if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
    }

    fun label(context: Context): String =
        choices.firstOrNull { it.first == current(context) }?.second ?: str(R.string.language_system)

    val isSupported: Boolean get() = supported
}

@Composable
fun LanguageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var current by remember { mutableStateOf(AppLanguage.current(context)) }
    fun choose(tag: String) {
        current = tag
        AppLanguage.set(context, tag)
    }

    SectionScreen(str(R.string.language_title), onBack) {
        item {
            if (!AppLanguage.isSupported) Note(str(R.string.language_unsupported))
            RadioItem(str(R.string.language_system), str(R.string.language_system_summary), current.isEmpty()) { choose("") }
            AppLanguage.choices.forEach { (tag, name) ->
                RadioItem(name, selected = current == tag) { choose(tag) }
            }
        }
    }
}
