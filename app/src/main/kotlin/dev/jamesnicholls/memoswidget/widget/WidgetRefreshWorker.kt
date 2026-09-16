package dev.jamesnicholls.memoswidget.widget

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.jamesnicholls.memoswidget.MemosApp
import java.util.concurrent.TimeUnit

/**
 * Fetches the latest memos and re-renders the widget.
 *
 * Network refresh runs here rather than in the AppWidgetProvider's goAsync()
 * window because aggressive OEM cached-app freezers (OnePlus/Oppo/etc.) suspend
 * the process mid-broadcast, killing in-flight requests. JobScheduler-scheduled
 * WorkManager jobs keep the process unfrozen for the duration of the work.
 */
class WidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as MemosApp).container
        return try {
            container.widgetRefresher.refreshAll()
            Result.success()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "widget refresh worker failed", e)
            // The widget already shows cached notes; retry later.
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val TAG = "MemosWidget"
        private const val UNIQUE_ONE_SHOT = "widget-refresh-once"
        private const val UNIQUE_PERIODIC = "widget-refresh-periodic"
        private const val MAX_ATTEMPTS = 3

        private fun networkConstraints(): Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Immediate refresh (after a send, manual trigger, or widget update). */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                .setConstraints(networkConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_ONE_SHOT, ExistingWorkPolicy.KEEP, request)
        }

        /** 30-minute background refresh; replaces the widget's updatePeriodMillis. */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES)
                .setConstraints(networkConstraints())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
