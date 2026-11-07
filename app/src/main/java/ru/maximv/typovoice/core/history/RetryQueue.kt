package ru.maximv.typovoice.core.history

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.app.Notification
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import ru.maximv.typovoice.TypoVoiceApp
import ru.maximv.typovoice.MainActivity
import ru.maximv.typovoice.R
import ru.maximv.typovoice.core.servers.AllServersFailedException
import ru.maximv.typovoice.core.settings.SettingsRepository
import java.util.concurrent.TimeUnit

/**
 * Background re-sending of failed dictations. Each queued history entry gets its own
 * WorkManager job that waits for a network connection; the schedule, attempt count and
 * status live in the entry itself so the history screen can show the queue.
 */
class RetryQueue(
    private val context: Context,
    private val history: HistoryRepository,
    private val settings: SettingsRepository,
) {
    /** Puts a failed entry in the queue if auto-retry is on and the error may go away by itself. */
    suspend fun enqueueIfEnabled(entry: HistoryEntry, retryable: Boolean) {
        val current = settings.current()
        if (!current.autoRetry || !entry.canRetry) return
        if (!retryable || current.retryAttempts <= 0) {
            history.replace(entry.copy(retryStatus = RetryStatus.EXHAUSTED))
            return
        }
        schedule(entry, attempts = 0)
    }

    /** Runs the next attempt right away (still respecting the queue bookkeeping). */
    fun runNow(entry: HistoryEntry) = enqueueWork(entry.id, delayMs = 0)

    suspend fun remove(entry: HistoryEntry) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(entry.id))
        history.replace(entry.copy(retryStatus = null, nextRetryAt = null))
    }

    /** Called by the worker after a failed attempt. */
    internal suspend fun onAttemptFailed(entry: HistoryEntry, retryable: Boolean) {
        val attempts = entry.retryAttempts + 1
        val max = settings.current().retryAttempts
        if (!retryable || attempts >= max) {
            history.replace(entry.copy(retryAttempts = attempts, retryStatus = RetryStatus.EXHAUSTED, nextRetryAt = null))
        } else {
            schedule(entry, attempts)
        }
    }

    private suspend fun schedule(entry: HistoryEntry, attempts: Int) {
        val delay = DELAYS_MS[attempts.coerceAtMost(DELAYS_MS.lastIndex)]
        history.replace(
            entry.copy(
                retryStatus = RetryStatus.QUEUED,
                retryAttempts = attempts,
                nextRetryAt = System.currentTimeMillis() + delay,
            ),
        )
        enqueueWork(entry.id, delay)
    }

    private fun enqueueWork(id: String, delayMs: Long) {
        val request = OneTimeWorkRequestBuilder<RetryWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(KEY_ID to id))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(id), ExistingWorkPolicy.REPLACE, request)
    }

    internal fun notifySuccess(text: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_retry), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_HISTORY, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(context.getString(R.string.notification_retry_done))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(text.hashCode(), notification)
    }

    companion object {
        internal const val KEY_ID = "entry_id"
        private const val CHANNEL_ID = "retry"

        /** Pause before attempt N+1 (the last value repeats). */
        val DELAYS_MS = listOf(30_000L, 60_000L, 120_000L, 300_000L, 600_000L, 1_800_000L, 3_600_000L)

        private fun workName(id: String) = "retry-$id"
    }
}

class RetryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = TypoVoiceApp.container
        val id = inputData.getString(RetryQueue.KEY_ID) ?: return Result.success()
        val entry = container.history.get(id) ?: return Result.success()
        if (entry.retryStatus != RetryStatus.QUEUED || !entry.canRetry) return Result.success()
        if (!container.settings.current().enabled) {
            // Switched off: keep the entry queued and look again later.
            return Result.retry()
        }
        val outcome = container.dictation.retry(entry, copyToClipboard = false)
        outcome.onSuccess { text -> container.retryQueue.notifySuccess(text) }
        outcome.onFailure { error ->
            val latest = container.history.get(id) ?: return Result.success()
            container.retryQueue.onAttemptFailed(latest, (error as? AllServersFailedException)?.retryable ?: true)
        }
        return Result.success()
    }
}
