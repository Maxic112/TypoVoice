package ru.maximv.typovoice

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * Localized text anywhere in the app, including code without a Context (errors, labels in
 * non-UI helpers). A language change recreates the activities, so screens pick up the new text.
 */
object Strings {
    /** Set by [TypoVoiceApp]; unit tests run without it and get the resource id back. */
    @Volatile
    var context: Context? = null
}

fun str(@StringRes id: Int, vararg args: Any?): String =
    Strings.context?.let { if (args.isEmpty()) it.getString(id) else it.getString(id, *args) } ?: "#$id"

/** "%d" quantity string with [count] as its argument. */
fun plural(@PluralsRes id: Int, count: Int): String =
    Strings.context?.resources?.getQuantityString(id, count, count) ?: "#$id:$count"
