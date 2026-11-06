package com.example.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

class TripAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        TripNotificationScheduler.ensureNotificationChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val tripId = intent.getLongExtra(TripNotificationScheduler.EXTRA_TRIP_ID, 0L)
        val routeTitle = intent.getStringExtra(TripNotificationScheduler.EXTRA_ROUTE_TITLE)
            ?: context.getString(R.string.app_name)
        val departureTime = intent.getStringExtra(TripNotificationScheduler.EXTRA_DEPARTURE_TIME)
            .orEmpty()
        val alertType = intent.getStringExtra(TripNotificationScheduler.EXTRA_ALERT_TYPE)
            ?: TripNotificationScheduler.ALERT_TWO_HOURS_BEFORE

        val heading = if (alertType == TripNotificationScheduler.ALERT_DAY_BEFORE) {
            "DEPARTURE TOMORROW // $routeTitle"
        } else {
            "DEPARTING IN 2 HOURS // $routeTitle"
        }
        val body = "Scheduled departure: $departureTime"

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("traveltrack://trip/$tripId")
            putExtra(MainActivity.EXTRA_TRIP_ID, tripId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            tripId.toInt(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, TripNotificationScheduler.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(heading)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify((tripId.toInt() * 10) + alertType.hashCode(), notification)
        }
    }
}
