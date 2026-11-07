package ru.maximv.typovoice.core.dictation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import ru.maximv.typovoice.TypoVoiceApp
import ru.maximv.typovoice.R

/**
 * Foreground service of type `microphone`. It exists only while recording/transcribing:
 * Android requires it to use the microphone outside of a visible activity.
 */
class DictationService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val controller = TypoVoiceApp.container.dictation
        when (intent?.action) {
            ACTION_STOP -> {
                controller.stop()
                return START_NOT_STICKY
            }
            ACTION_CANCEL -> {
                controller.cancel()
                return START_NOT_STICKY
            }
        }
        try {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } catch (e: Exception) {
            controller.onServiceFailed(e)
            stopSelf()
            return START_NOT_STICKY
        }
        controller.onServiceReady()
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_dictation), NotificationManager.IMPORTANCE_LOW),
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(getString(R.string.notification_recording))
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.action_stop), actionIntent(ACTION_STOP)).build())
            .addAction(Notification.Action.Builder(null, getString(R.string.action_cancel), actionIntent(ACTION_CANCEL)).build())
            .build()
    }

    private fun actionIntent(action: String): PendingIntent = PendingIntent.getService(
        this,
        action.hashCode(),
        Intent(this, DictationService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val CHANNEL_ID = "dictation"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "stop"
        private const val ACTION_CANCEL = "cancel"

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, DictationService::class.java))
            } catch (e: Exception) {
                TypoVoiceApp.container.dictation.onServiceFailed(e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DictationService::class.java))
        }
    }
}
