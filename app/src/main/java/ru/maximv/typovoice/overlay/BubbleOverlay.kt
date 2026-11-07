package ru.maximv.typovoice.overlay

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ru.maximv.typovoice.AppContainer
import ru.maximv.typovoice.MainActivity
import ru.maximv.typovoice.core.dictation.DictationSource
import ru.maximv.typovoice.core.dictation.DictationState
import ru.maximv.typovoice.core.postprocess.Postprocess
import ru.maximv.typovoice.core.settings.OverlayMode
import ru.maximv.typovoice.core.settings.BubbleAction
import ru.maximv.typovoice.core.settings.BubbleSettings
import kotlin.math.abs
import ru.maximv.typovoice.R
import ru.maximv.typovoice.str

/**
 * Floating dictation button. Lives in a TYPE_ACCESSIBILITY_OVERLAY window that never takes focus,
 * so the text field the user is typing into keeps input focus and receives the result.
 */
class BubbleOverlay(
    private val service: AccessibilityService,
    private val scope: CoroutineScope,
    private val container: AppContainer,
    private val typing: StateFlow<Boolean>,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val prefs = service.getSharedPreferences("bubble", Context.MODE_PRIVATE)
    private val density = service.resources.displayMetrics.density
    private val sizePx = (56 * density).toInt()
    private val view = BubbleView(service)
    private val params = WindowManager.LayoutParams(
        sizePx,
        sizePx,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = prefs.getInt(KEY_X, Int.MAX_VALUE)
        y = prefs.getInt(KEY_Y, (service.resources.displayMetrics.heightPixels * 0.35f).toInt())
    }

    private var shown = false
    private var bubble = BubbleSettings()
    private var postprocessChoice: String? = null
    private var cachedPresets: List<String> = emptyList()
    private var hasCustomPrompt = false
    private val menu = PromptMenu(service)
    private var jobs = mutableListOf<Job>()

    fun attach() {
        view.setOnTouchListener(TouchHandler())
        jobs += scope.launch {
            container.dictation.level.collect { view.level = it }
        }
        jobs += scope.launch {
            container.settings.settings.collect {
                bubble = it.bubble
                view.idleIcon = it.bubble.idleIcon
                view.recordingIcon = it.bubble.recordingIcon
                postprocessChoice = it.postprocessChoice
                cachedPresets = it.cachedPresets
                hasCustomPrompt = it.servers.any { server -> server.enabled && !server.customPrompt.isNullOrBlank() }
                view.badge = Postprocess.isActive(it.postprocessChoice)
            }
        }
        jobs += scope.launch {
            combine(
                container.settings.settings.map { if (it.enabled) it.overlayMode else OverlayMode.OFF },
                typing,
                container.dictation.state,
            ) { mode, isTyping, state ->
                when (mode) {
                    OverlayMode.OFF -> false
                    OverlayMode.ALWAYS -> true
                    OverlayMode.ON_TEXT_FOCUS -> isTyping || state !is DictationState.Idle
                }
            }.collect(::setShown)
        }
        jobs += scope.launch {
            container.dictation.state.collect(::render)
        }
    }

    fun detach() {
        menu.dismiss()
        jobs.forEach { it.cancel() }
        setShown(false)
    }

    private fun render(state: DictationState) {
        view.visual = when (state) {
            DictationState.Idle -> BubbleView.Visual.IDLE
            is DictationState.Starting, is DictationState.Recording -> BubbleView.Visual.RECORDING
            is DictationState.Transcribing -> BubbleView.Visual.BUSY
            is DictationState.Done -> BubbleView.Visual.SUCCESS
            is DictationState.Failed -> BubbleView.Visual.ERROR
        }
        if (state is DictationState.Failed) {
            Toast.makeText(service, state.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun setShown(show: Boolean) {
        if (show == shown) return
        shown = show
        if (!show) menu.dismiss()
        if (show) {
            clampToScreen()
            windowManager.addView(view, params)
        } else {
            windowManager.removeView(view)
        }
    }

    private fun clampToScreen() {
        val metrics = service.resources.displayMetrics
        params.x = params.x.coerceIn(0, metrics.widthPixels - sizePx)
        params.y = params.y.coerceIn(0, metrics.heightPixels - sizePx)
    }

    private fun snapToEdge() {
        val width = service.resources.displayMetrics.widthPixels
        params.x = if (params.x + sizePx / 2 < width / 2) 0 else width - sizePx
        windowManager.updateViewLayout(view, params)
        prefs.edit().putInt(KEY_X, params.x).putInt(KEY_Y, params.y).apply()
    }

    private fun showPostprocessMenu() {
        menu.show(
            params.x,
            params.y,
            sizePx,
            str(R.string.common_postprocess),
            Postprocess.choices(cachedPresets, hasCustomPrompt).map { it to Postprocess.label(it) },
            postprocessChoice,
        ) { choice -> scope.launch { container.settings.setPostprocessChoice(choice) } }
        // The list may be stale: refresh it for the next time.
        scope.launch { container.refreshPresets() }
    }

    /** Models of the first enabled server; the choice is saved into that server, as on the home screen. */
    private fun showModelMenu() {
        scope.launch {
            val server = container.settings.current().servers.firstOrNull { it.enabled }
            if (server == null) {
                Toast.makeText(service, str(R.string.no_enabled_servers), Toast.LENGTH_SHORT).show()
                return@launch
            }
            val models = runCatching { container.api.models(server.endpoint) }.getOrElse {
                Toast.makeText(service, str(R.string.bubble_models_failed, it.message), Toast.LENGTH_LONG).show()
                return@launch
            }
            if (!shown) return@launch
            val choices = listOf<Pair<String?, String>>(null to str(R.string.model_server_current)) +
                models.filter { !it.isAlias }.map { it.id to if (it.loaded) str(R.string.model_loaded, it.name) else it.name }
            menu.show(params.x, params.y, sizePx, str(R.string.common_model), choices, server.model) { model ->
                scope.launch {
                    val fresh = container.settings.current().servers.firstOrNull { it.id == server.id } ?: return@launch
                    container.settings.upsertServer(fresh.copy(model = model))
                }
            }
        }
    }

    private fun openApp() {
        service.startActivity(
            Intent(service, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
        )
    }

    /** Runs a gesture's action. Push-to-talk on a tap or double tap behaves like start/stop. */
    private fun perform(action: BubbleAction) {
        when (action) {
            BubbleAction.TOGGLE_RECORDING, BubbleAction.PUSH_TO_TALK -> container.dictation.toggle(DictationSource.OVERLAY)
            BubbleAction.POSTPROCESS_MENU -> {
                container.feedback.tick()
                showPostprocessMenu()
            }
            BubbleAction.MODEL_MENU -> {
                container.feedback.tick()
                showModelMenu()
            }
            BubbleAction.OPEN_APP -> openApp()
            BubbleAction.NONE -> Unit
        }
    }

    /**
     * Tap, double tap and hold run the actions chosen in settings. While a recording is going on any
     * tap stops it. With a double-tap action a single tap waits a moment for the second one.
     * Moving the finger before the hold threshold drags the bubble instead.
     */
    private inner class TouchHandler : android.view.View.OnTouchListener {
        private val slop = ViewConfiguration.get(service).scaledTouchSlop
        private val handler = Handler(Looper.getMainLooper())
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false
        private var holding = false
        private var pushToTalk = false
        private var tapPending = false
        private val doubleTapMs = ViewConfiguration.getDoubleTapTimeout().toLong()
        private val singleTap = Runnable {
            tapPending = false
            perform(bubble.tap)
        }
        private val holdStart = Runnable {
            holding = true
            pushToTalk = bubble.hold == BubbleAction.PUSH_TO_TALK
            if (pushToTalk) container.dictation.start(DictationSource.OVERLAY) else perform(bubble.hold)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: android.view.View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    menu.dismiss()
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    holding = false
                    pushToTalk = false
                    val idle = !container.dictation.state.value.isBusy
                    if (bubble.hold != BubbleAction.NONE && idle && !tapPending) {
                        val delay = if (bubble.hold == BubbleAction.PUSH_TO_TALK) HOLD_MS else LONG_PRESS_MS
                        handler.postDelayed(holdStart, delay)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!holding && !dragging && (abs(dx) > slop || abs(dy) > slop)) {
                        dragging = true
                        handler.removeCallbacks(holdStart)
                    }
                    if (dragging) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        clampToScreen()
                        windowManager.updateViewLayout(view, params)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(holdStart)
                    when {
                        dragging -> snapToEdge()
                        holding -> if (pushToTalk) container.dictation.stop()
                        event.actionMasked != MotionEvent.ACTION_UP -> Unit
                        tapPending -> {
                            handler.removeCallbacks(singleTap)
                            tapPending = false
                            perform(bubble.doubleTap)
                        }
                        container.dictation.state.value.isBusy -> {
                            // Stopping a recording must not wait for a possible double tap.
                            container.dictation.toggle(DictationSource.OVERLAY)
                        }
                        bubble.doubleTap == BubbleAction.NONE -> perform(bubble.tap)
                        else -> {
                            tapPending = true
                            handler.postDelayed(singleTap, doubleTapMs)
                        }
                    }
                }
            }
            return true
        }
    }

    private companion object {
        const val KEY_X = "x"
        const val KEY_Y = "y"
        const val HOLD_MS = 250L
        const val LONG_PRESS_MS = 500L
    }
}
