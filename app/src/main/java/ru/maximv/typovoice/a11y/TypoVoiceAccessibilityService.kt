package ru.maximv.typovoice.a11y

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.KeyEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import ru.maximv.typovoice.core.settings.VolumeKeyMode
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.maximv.typovoice.TypoVoiceApp
import ru.maximv.typovoice.core.settings.InsertMethod
import ru.maximv.typovoice.delivery.Spacing
import ru.maximv.typovoice.delivery.TextMerge
import ru.maximv.typovoice.overlay.BubbleOverlay

/**
 * Accessibility service: tracks whether the user is typing into a text field, hosts the floating
 * dictation bubble (as an accessibility overlay, so no "draw over apps" permission is needed)
 * and inserts recognized text into the focused field.
 */
class TypoVoiceAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var overlay: BubbleOverlay? = null

    private val _typing = MutableStateFlow(false)
    private var insertMethod = InsertMethod.PASTE
    private var spacing = Spacing()
    private var activeEventTypes = 0
    private val hints = FieldText.Hints()
    private val volumeKeys by lazy { VolumeKeyHandler(this, TypoVoiceApp.container.dictation) }

    /** True while an editable field has input focus and the keyboard is on screen. */
    val typing: StateFlow<Boolean> = _typing.asStateFlow()

    override fun onServiceConnected() {
        instance = this
        activeEventTypes = serviceInfo.eventTypes
        overlay = BubbleOverlay(this, scope, TypoVoiceApp.container, typing).also { it.attach() }
        scope.launch {
            combine(TypoVoiceApp.container.appSwitch.enabled, TypoVoiceApp.container.settings.settings, ::Pair)
                .collect { (enabled, settings) ->
                    insertMethod = settings.insertMethod
                    spacing = settings.spacing
                    volumeKeys.trigger = settings.volumeTrigger
                    volumeKeys.mode = if (enabled) settings.volumeKey else VolumeKeyMode.OFF
                    // While switched off, ask the system to stop sending events altogether;
                    // key events are requested only when the volume-key trigger is on.
                    serviceInfo = serviceInfo.apply {
                        eventTypes = if (enabled) activeEventTypes else 0
                        flags = if (volumeKeys.mode != VolumeKeyMode.OFF) {
                            flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
                        } else {
                            flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv()
                        }
                    }
                    refreshTyping()
                }
        }
        refreshTyping()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
            -> refreshTyping()
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = volumeKeys.onKeyEvent(event)

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        overlay?.detach()
        overlay = null
        scope.cancel()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun refreshTyping() {
        // Dormant while the app is switched off: no window inspection at all.
        if (!TypoVoiceApp.container.appSwitch.isEnabled) {
            _typing.value = false
            return
        }
        val node = focusedEditable()
        val keyboardVisible = windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        _typing.value = node != null && keyboardVisible
        logFocus(keyboardVisible)
    }

    private var lastFocusLog: String? = null

    /** Diagnostics for apps where the bubble doesn't appear: what has input focus (no text is logged). */
    private fun logFocus(keyboardVisible: Boolean) {
        val raw = focusedEditable() ?: findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val line = if (raw == null) {
            "focus=none keyboard=$keyboardVisible active=${rootInActiveWindow?.packageName}"
        } else {
            "focus=${raw.packageName}/${raw.className} editable=${raw.isEditable} focused=${raw.isFocused} " +
                "password=${raw.isPassword} keyboard=$keyboardVisible typing=${_typing.value}"
        }
        if (line != lastFocusLog) {
            lastFocusLog = line
            Log.i(TAG, line)
        }
    }

    private fun focusedEditable(): AccessibilityNodeInfo? {
        val focus = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return null
        // Compose/React Native apps (e.g. Claude) give input focus to a host view; the real text
        // field is a focused editable node inside it, or elsewhere in the active window.
        val node = when {
            focus.isEditable -> focus
            else -> findFocusedEditable(focus) ?: rootInActiveWindow?.let(::findFocusedEditable)
        } ?: return null
        if (node.isPassword || node.packageName == packageName) return null
        hints.remember(node)
        return node
    }

    private fun isInsideWebView(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < MAX_PARENT_DEPTH) {
            if (current.className?.contains("WebView") == true) return true
            current = current.parent
            depth++
        }
        return false
    }

    /** Breadth-first search for a focused editable node, bounded so huge screens stay cheap. */
    private fun findFocusedEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_SEARCH_NODES) {
            val node = queue.removeFirst()
            visited++
            if (node.isEditable && node.isFocused) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::addLast)
        }
        return null
    }

    /** Inserts [text] at the cursor of the focused field. Returns false if there is no suitable field. */
    fun insertText(text: String): Boolean {
        if (!TypoVoiceApp.container.appSwitch.isEnabled) return false
        val node = focusedEditable() ?: return false
        val existing = FieldText.realText(
            text = node.text?.toString(),
            hint = node.hintText?.toString() ?: hints.recall(node),
            showingHint = node.isShowingHintText,
        )
        val selStart = node.textSelectionStart.takeIf { it >= 0 } ?: existing.length
        val selEnd = node.textSelectionEnd.takeIf { it >= 0 } ?: selStart
        val start = minOf(selStart, selEnd)
        val end = maxOf(selStart, selEnd)
        // Web editors expose a copy of their text whose offsets don't match the real document,
        // and rewriting it would rewrite the whole page/note: only a native paste is safe there.
        val web = isInsideWebView(node)
        val method = if (web) InsertMethod.PASTE else insertMethod
        Log.i(
            TAG,
            "insert via $method${if (web) " (web field)" else ""} into ${node.packageName}/${node.className}: " +
                "textLen=${node.text?.length} hint=${node.hintText} showingHint=${node.isShowingHintText} sel=$start..$end",
        )
        val piece = TextMerge.pastePiece(existing, start, text, spacing)
        if (web) return paste(node, piece)
        return if (method == InsertMethod.PASTE) {
            paste(node, piece) || setText(node, existing, start, end, text)
        } else {
            setText(node, existing, start, end, text) || paste(node, piece)
        }
    }

    /**
     * The field inserts the text itself, like a manual paste: respects cursor, selection and placeholders.
     * Fields only advertise "paste" while the clipboard holds text, so fill the clipboard first and
     * then just try: checking the advertised actions beforehand made this fail whenever the clipboard was empty.
     */
    private fun paste(node: AccessibilityNodeInfo, piece: String): Boolean {
        TypoVoiceApp.container.delivery.copyToClipboard(piece)
        node.refresh()
        val pasted = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        if (!pasted) Log.w(TAG, "paste rejected by ${node.packageName}, falling back")
        return pasted
    }

    private fun setText(node: AccessibilityNodeInfo, existing: String, start: Int, end: Int, text: String): Boolean {
        val merged = TextMerge.insert(existing, start, end, text, spacing)
        val setText = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, merged.text)
        }
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setText)) return false
        placeCursor(node, merged.cursor)
        return true
    }

    /**
     * Puts the cursor right after the inserted text. Web-based editors (Obsidian, browsers) apply
     * the new text asynchronously and then jump to the end, overriding an immediate selection —
     * so re-check for a short while and put the cursor back if it moved.
     */
    private fun placeCursor(node: AccessibilityNodeInfo, cursor: Int) {
        val selection = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selection)
        scope.launch {
            for (wait in CURSOR_CHECKS_MS) {
                delay(wait)
                if (!node.refresh()) return@launch
                if (node.textSelectionStart == cursor && node.textSelectionEnd == cursor) continue
                Log.i(TAG, "cursor moved to ${node.textSelectionStart}, restoring $cursor")
                node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selection)
            }
        }
    }

    companion object {
        private const val TAG = "TypoVoiceA11y"
        private const val MAX_SEARCH_NODES = 600
        private const val MAX_PARENT_DEPTH = 40

        /** Pauses between cursor checks after a text replacement (~0.6 s in total). */
        private val CURSOR_CHECKS_MS = listOf(50L, 100L, 150L, 300L)

        @Volatile
        var instance: TypoVoiceAccessibilityService? = null
            private set
    }
}
