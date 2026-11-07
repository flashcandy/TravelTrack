package com.example.sms

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.SettingsRepository
import com.example.data.Trip
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

        // Respect Settings: "Auto check status" toggle (default off)
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
            if (settings.autoSendSms) {
                PnrStatusSender.dispatchCheck(
                    context = applicationContext,
                    trip = trip,
                    autoSendRequested = true,
                    isManualTrigger = false
                )
            } else {
                // Post high-priority notification to let user tap and send prefilled SMS
                postPrefilledPromptNotification(trip)
            }
        }

        return Result.success()
    }

    private fun postPrefilledPromptNotification(trip: Trip) {
        val intent = PnrStatusSender.buildPrefilledIntent(trip.cleanPnr())
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            (trip.id + 8000).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        PnrReplyListenerService.ensurePnrNotificationChannel(applicationContext)

        val pnrMasked = "••••${trip.maskedReferenceLast4()}"
        val title = "CHECK PNR STATUS // ${trip.title}"
        val message = "Tap to send 'PNR ${trip.cleanPnr()}' to 139 ($pnrMasked)."

        val notification = NotificationCompat.Builder(
            applicationContext,
            PnrReplyListenerService.CHANNEL_PNR_STATUS
        )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(applicationContext)
                .notify((trip.id + 8000).toInt(), notification)
        }
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
