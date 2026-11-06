package com.example.sms

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.SettingsRepository
import com.example.data.TripFilter
import com.example.data.TripRepository
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class PnrStatusWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val settingsRepository = SettingsRepository(applicationContext)
        val settings = settingsRepository.getSettingsSnapshot()

        // 7. Respect Settings: "Auto check status" toggle (default off)
        if (!settings.autoCheckPnr) {
            return Result.success()
        }

        val database = AppDatabase.getInstance(applicationContext)
        val repository = TripRepository(database.tripDao(), database.pnrStatusCheckDao())
        val allTrips = repository.getAllTripsSnapshot()

        val now = LocalDateTime.now()
        val upcomingTrips = TripFilter.filterUpcomingForWidget(
            trips = allTrips,
            now = now,
            maxDays = 5L,
            maxCount = 20
        )

        for (trip in upcomingTrips) {
            // Stop checking a trip after it departs or after it's marked cancelled
            if (trip.isCancelled()) continue
            if (!trip.isPnrCheckEligible(settings.showCheckForConfirmed)) continue

            val departure = TripFilter.parseIsoDateTime(trip.departureDateTime) ?: continue
            if (!departure.isAfter(now)) continue

            // Determine cooldown window
            val hoursUntilDeparture = Duration.between(now, departure).toHours()
            val isInFinal24Hours = hoursUntilDeparture in 0..24

            val requiredCooldownHours = if (isInFinal24Hours && settings.checkInFinal24Hours) {
                2L
            } else {
                6L
            }

            val latestCheck = repository.getLatestCheckForTrip(trip.id)
            if (latestCheck != null) {
                val elapsedMillis = System.currentTimeMillis() - latestCheck.requestedAt
                val cooldownMillis = TimeUnit.HOURS.toMillis(requiredCooldownHours) - TimeUnit.MINUTES.toMillis(5)
                if (elapsedMillis < cooldownMillis) {
                    // Trip is still within cooldown period
                    continue
                }
            }

            // Dispatch background SMS check
            SmsStatusSender.sendPnrStatusCheck(
                context = applicationContext,
                trip = trip,
                isManualTrigger = false
            )
        }

        return Result.success()
    }
}

object PnrStatusScheduler {
    const val PERIODIC_WORK_NAME = "travel_track_pnr_status_worker"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        // Runs periodically every 2 hours so final 24h trips are checked every 2 hours
        // and 5-day trips respect their 6-hour cooldown seamlessly.
        val request = PeriodicWorkRequestBuilder<PnrStatusWorker>(2, TimeUnit.HOURS)
            .setConstraints(constraints)
            .addTag(PERIODIC_WORK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
    }
}
