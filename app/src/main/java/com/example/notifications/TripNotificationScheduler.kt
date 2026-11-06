package com.example.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.R
import com.example.data.AppDatabase
import com.example.data.SettingsRepository
import com.example.data.Trip
import com.example.data.TripFilter
import java.time.LocalDateTime
import java.time.ZoneId

object TripNotificationScheduler {
    const val CHANNEL_ID = "travel_track_departure_alerts"
    const val EXTRA_TRIP_ID = "extra_trip_id"
    const val EXTRA_ROUTE_TITLE = "extra_route_title"
    const val EXTRA_DEPARTURE_TIME = "extra_departure_time"
    const val EXTRA_ALERT_TYPE = "extra_alert_type"

    const val ALERT_DAY_BEFORE = "DAY_BEFORE"
    const val ALERT_TWO_HOURS_BEFORE = "TWO_HOURS_BEFORE"

    fun ensureNotificationChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_trip_alerts_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_trip_alerts_desc)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    suspend fun syncAllTripAlerts(context: Context) {
        ensureNotificationChannel(context)
        val settings = SettingsRepository(context).getSettingsSnapshot()
        val trips = AppDatabase.getInstance(context).tripDao().getAllTripsSnapshot()
        val now = LocalDateTime.now()

        for (trip in trips) {
            cancelAlertsForTrip(context, trip.id)
            scheduleAlertsForTrip(
                context = context,
                trip = trip,
                notifyDayBefore = settings.notifyDayBefore,
                notifyTwoHoursBefore = settings.notifyTwoHoursBefore,
                now = now
            )
        }
    }

    fun scheduleAlertsForTrip(
        context: Context,
        trip: Trip,
        notifyDayBefore: Boolean,
        notifyTwoHoursBefore: Boolean,
        now: LocalDateTime = LocalDateTime.now()
    ) {
        if (trip.id <= 0L) return
        val departure = TripFilter.parseIsoDateTime(trip.departureDateTime) ?: return
        if (!departure.isAfter(now)) return

        val zoneId = ZoneId.systemDefault()

        if (notifyDayBefore) {
            val dayBeforeTime = departure.minusHours(24)
            if (dayBeforeTime.isAfter(now)) {
                val triggerAtMillis = dayBeforeTime.atZone(zoneId).toInstant().toEpochMilli()
                scheduleExactOrFallbackAlarm(
                    context = context,
                    requestCode = requestCodeFor(trip.id, isDayBefore = true),
                    triggerAtMillis = triggerAtMillis,
                    trip = trip,
                    alertType = ALERT_DAY_BEFORE
                )
            }
        }

        if (notifyTwoHoursBefore) {
            val twoHoursBeforeTime = departure.minusHours(2)
            if (twoHoursBeforeTime.isAfter(now)) {
                val triggerAtMillis = twoHoursBeforeTime.atZone(zoneId).toInstant().toEpochMilli()
                scheduleExactOrFallbackAlarm(
                    context = context,
                    requestCode = requestCodeFor(trip.id, isDayBefore = false),
                    triggerAtMillis = triggerAtMillis,
                    trip = trip,
                    alertType = ALERT_TWO_HOURS_BEFORE
                )
            }
        }
    }

    fun cancelAlertsForTrip(context: Context, tripId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        listOf(true, false).forEach { isDayBefore ->
            val intent = Intent(context, TripAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCodeFor(tripId, isDayBefore),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    private fun scheduleExactOrFallbackAlarm(
        context: Context,
        requestCode: Int,
        triggerAtMillis: Long,
        trip: Trip,
        alertType: String
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, TripAlarmReceiver::class.java).apply {
            putExtra(EXTRA_TRIP_ID, trip.id)
            putExtra(EXTRA_ROUTE_TITLE, trip.title)
            putExtra(EXTRA_DEPARTURE_TIME, "${trip.departureDate} ${trip.departureTime}")
            putExtra(EXTRA_ALERT_TYPE, alertType)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        try {
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                // Fallback when exact alarms are denied
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    private fun requestCodeFor(tripId: Long, isDayBefore: Boolean): Int {
        val base = (tripId % 1_000_000L).toInt() * 10
        return if (isDayBefore) base + 1 else base + 2
    }
}
