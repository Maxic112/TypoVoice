package ru.maximv.typovoice.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Small list of choices (post-processing preset, model) shown next to the bubble. Like the bubble it never
 * takes focus, so the text field keeps the keyboard; a touch outside closes it.
 */
class PromptMenu(private val service: AccessibilityService) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val density = service.resources.displayMetrics.density
    private var root: LinearLayout? = null

    val isShown get() = root != null

    /**
     * [anchorX]/[anchorY]/[anchorSize] describe the bubble on screen; the menu opens towards
     * the middle of the screen so it's never cut off at the edge.
     */
    fun show(
        anchorX: Int,
        anchorY: Int,
        anchorSize: Int,
        title: String,
        choices: List<Pair<String?, String>>,
        selected: String?,
        onSelect: (String?) -> Unit,
    ) {
        dismiss()
        val list = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = rounded(Color.argb(240, 0x26, 0x32, 0x38), 16)
            elevation = dp(6).toFloat()
        }
        list.addView(
            TextView(service).apply {
                text = title
                setTextColor(Color.argb(170, 255, 255, 255))
                textSize = 12f
                setPadding(dp(12), dp(6), dp(12), dp(4))
            },
        )
        choices.forEach { (choice, label) ->
            list.addView(
                TextView(service).apply {
                    text = label
                    textSize = 15f
                    setTextColor(Color.WHITE)
                    if (choice == selected) {
                        setTypeface(typeface, Typeface.BOLD)
                        background = rounded(Color.argb(90, 0x42, 0xA5, 0xF5), 10)
                    }
                    setPadding(dp(12), dp(10), dp(16), dp(10))
                    setOnClickListener {
                        dismiss()
                        onSelect(choice)
                        Toast.makeText(service, "$title: $label", Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }

        val metrics = service.resources.displayMetrics
        list.measure(0, 0)
        val width = list.measuredWidth
        val height = list.measuredHeight
        val onLeftHalf = anchorX + anchorSize / 2 < metrics.widthPixels / 2
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (onLeftHalf) anchorX + anchorSize + dp(8) else anchorX - width - dp(8)
            y = (anchorY + anchorSize / 2 - height / 2).coerceIn(dp(24), metrics.heightPixels - height - dp(24))
        }
        list.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) dismiss()
            false
        }
        windowManager.addView(list, params)
        root = list
    }

    fun dismiss() {
        root?.let { windowManager.removeView(it) }
        root = null
    }

    private fun dp(value: Int) = (value * density).toInt()

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * density
    }
}
