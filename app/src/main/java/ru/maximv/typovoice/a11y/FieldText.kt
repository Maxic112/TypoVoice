package ru.maximv.typovoice.a11y

/**
 * Many apps report the placeholder as the field text when the field is empty
 * (Telegram's "Message"), so the hint must not be treated as content.
 */
object FieldText {
    fun realText(text: String?, hint: String?, showingHint: Boolean): String {
        if (showingHint || text == null) return ""
        if (!hint.isNullOrEmpty() && text == hint) return ""
        return text
    }

    /**
     * Placeholders seen per field. Telegram reports the hint only while the field has text and
     * passes the placeholder off as text when it is empty, so remember it from the first case.
     */
    class Hints {
        private val known = HashMap<String, String>()

        fun remember(node: android.view.accessibility.AccessibilityNodeInfo) {
            val hint = node.hintText?.toString()
            if (!hint.isNullOrEmpty()) known[key(node)] = hint
        }

        fun recall(node: android.view.accessibility.AccessibilityNodeInfo): String? = known[key(node)]

        private fun key(node: android.view.accessibility.AccessibilityNodeInfo) =
            "${node.packageName}/${node.viewIdResourceName ?: node.className}"
    }
}
