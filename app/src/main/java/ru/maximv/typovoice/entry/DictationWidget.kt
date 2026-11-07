package ru.maximv.typovoice.entry

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import ru.maximv.typovoice.TypoVoiceApp
import ru.maximv.typovoice.R
import ru.maximv.typovoice.core.dictation.DictationSource
import ru.maximv.typovoice.core.dictation.DictationState

/** 1×1 home-screen mic button. The result goes to the clipboard (there's no text field on the home screen). */
class DictationWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        manager.updateAppWidget(ids, views(context, TypoVoiceApp.container.dictation.state.value))
    }

    companion object {
        fun refresh(context: Context, state: DictationState) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DictationWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, views(context, state))
        }

        private fun views(context: Context, state: DictationState) = RemoteViews(context.packageName, R.layout.widget_dictation).apply {
            val background = when (state) {
                is DictationState.Starting, is DictationState.Recording -> R.drawable.widget_bg_recording
                is DictationState.Transcribing -> R.drawable.widget_bg_busy
                is DictationState.Failed -> R.drawable.widget_bg_error
                else -> R.drawable.widget_bg_idle
            }
            setInt(R.id.widget_button, "setBackgroundResource", background)
            setOnClickPendingIntent(
                R.id.widget_button,
                PendingIntent.getActivity(
                    context,
                    0,
                    DictationActivity.intent(context, DictationSource.WIDGET),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        }
    }
}
