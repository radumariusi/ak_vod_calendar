package com.ak.vodcalendar.today

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ak.vodcalendar.AkApplication
import com.ak.vodcalendar.core.model.AppSettings
import com.ak.vodcalendar.core.model.NotificationMode
import com.ak.vodcalendar.core.time.RomaniaTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Background work:
 * - "refresh" every 3 hours: Simkl / WeTrakr sync (when stale) and the "Azi apare" TV channel;
 * - "daily" once a day at the hour set in Settings (Romania time): the notifications,
 *   plus the same refresh.
 */
class TodayWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as AkApplication).container
        val notify = inputData.getBoolean(KEY_NOTIFY, false)
        return try {
            container.tracking.sync(force = false, maxAgeMillis = 2 * 60 * 60 * 1000L)
            val settings = container.settingsRepository.settings.first()
            val digest = TodayLoader(container).load(settings)
            TodayChannel.update(applicationContext, digest, settings.tvChannel)
            if (notify && digest != null && settings.notifications.mode != NotificationMode.OFF) {
                val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val today = digest.date.toString()
                // Once a day, even if the work runs twice (retry, hour changed in Settings).
                if (prefs.getString(KEY_LAST_NOTIFIED, null) != today) {
                    TodayNotifier.post(applicationContext, digest, settings.notifications.mode)
                    prefs.edit().putString(KEY_LAST_NOTIFIED, today).apply()
                }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("TodayWorker", "failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val KEY_NOTIFY = "notify"
        private const val PREFS = "today_work"
        private const val KEY_LAST_NOTIFIED = "last_notified"
        private const val KEY_SCHEDULED_HOUR = "scheduled_hour"
        private const val WORK_REFRESH = "today-refresh"
        private const val WORK_REFRESH_NOW = "today-refresh-now"
        private const val WORK_DAILY = "today-daily"

        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Sets up (or updates) the periodic work for the current settings. Cheap to call often. */
        fun schedule(context: Context, settings: AppSettings) {
            val work = WorkManager.getInstance(context)
            work.enqueueUniquePeriodicWork(
                WORK_REFRESH,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<TodayWorker>(3, TimeUnit.HOURS).setConstraints(network).build(),
            )

            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (settings.notifications.mode == NotificationMode.OFF) {
                work.cancelUniqueWork(WORK_DAILY)
                prefs.edit().remove(KEY_SCHEDULED_HOUR).apply()
                return
            }
            val hour = settings.notifications.hour
            val changed = prefs.getInt(KEY_SCHEDULED_HOUR, -1) != hour
            val request = PeriodicWorkRequestBuilder<TodayWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayUntil(hour).toMillis(), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_NOTIFY to true))
                .setConstraints(network)
                .build()
            work.enqueueUniquePeriodicWork(
                WORK_DAILY,
                if (changed) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
            prefs.edit().putInt(KEY_SCHEDULED_HOUR, hour).apply()
        }

        /** Refreshes the TV channel (and Simkl, if stale) now, e.g. when the app opens. */
        fun refreshNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_REFRESH_NOW,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<TodayWorker>().setConstraints(network).build(),
            )
        }

        /** Time until the next [hour]:00 in Romania. */
        internal fun delayUntil(hour: Int): Duration {
            val now = RomaniaTime.now()
            var next = now.toLocalDate().atTime(hour, 0).atZone(RomaniaTime.ZONE)
            if (!next.isAfter(now)) next = next.plusDays(1)
            return Duration.between(now, next)
        }
    }
}
