package com.example.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class WidgetRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        TravelTrackWidgetUpdater.updateAllWidgets(applicationContext)
        // Ensure midnight roll-over worker stays scheduled for the next midnight
        WidgetRefreshScheduler.scheduleNextMidnightRefresh(applicationContext)
        return Result.success()
    }
}

object WidgetRefreshScheduler {
    const val PERIODIC_WORK_NAME = "travel_track_widget_periodic_6h"
    const val MIDNIGHT_WORK_NAME = "travel_track_widget_midnight_rollover"

    fun scheduleAll(context: Context) {
        schedulePeriodicSixHourRefresh(context)
        scheduleNextMidnightRefresh(context)
    }

    /**
     * Schedules a periodic WorkManager job every 6 hours so the 5-day widget window stays fresh.
     */
    fun schedulePeriodicSixHourRefresh(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(6, TimeUnit.HOURS)
            .addTag(PERIODIC_WORK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Schedules a WorkManager job for 00:00:05 at the next midnight so trips move into/out of
     * the 5-day window cleanly when the date changes.
     */
    fun scheduleNextMidnightRefresh(
        context: Context,
        now: LocalDateTime = LocalDateTime.now()
    ) {
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay().plusSeconds(5)
        val delayMillis = Duration.between(now, nextMidnight).toMillis().coerceAtLeast(60_000L)

        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .addTag(MIDNIGHT_WORK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            MIDNIGHT_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
