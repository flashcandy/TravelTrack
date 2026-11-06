package com.example.sms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import com.example.data.PnrStatusCategory
import com.example.data.Trip
import com.example.data.TripRepository
import com.example.widget.TravelTrackWidgetUpdater
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsStatusReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_PNR_STATUS = "travel_track_pnr_status_channel"

        fun is139Sender(originAddress: String?): Boolean {
            if (originAddress.isNullOrBlank()) return false
            val clean = originAddress.trim()
            return clean == "139" ||
                clean == "+91139" ||
                clean.endsWith("139") ||
                clean.contains("-139", ignoreCase = true)
        }

        fun ensurePnrNotificationChannel(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_PNR_STATUS,
                "PNR Live Status Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when waitlisted tickets are confirmed, moved to RAC, or cancelled"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val firstSender = messages.firstOrNull()?.originatingAddress
        if (!is139Sender(firstSender)) {
            // Strictly ignore all non-139 SMS messages for user privacy
            return
        }

        val fullBodyBuilder = StringBuilder()
        for (sms in messages) {
            fullBodyBuilder.append(sms.displayMessageBody)
        }
        val fullBody = fullBodyBuilder.toString().trim()
        if (fullBody.isEmpty()) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleIncoming139Sms(context, fullBody)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleIncoming139Sms(context: Context, fullBody: String) {
        val parsed = PnrReplyParser.parse(fullBody)
        val pnr = parsed.pnr ?: return

        val database = AppDatabase.getInstance(context)
        val repository = TripRepository(database.tripDao(), database.pnrStatusCheckDao())

        // 3. Only process a reply if a PNR check is pending in the last 3 minutes
        val pendingCheck = repository.findPendingCheck(pnr, maxAgeMillis = 180_000L) ?: return
        val trip = repository.getTripById(pendingCheck.tripId)
            ?: repository.getTripByPnr(pnr)
            ?: return

        val isoNow = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))

        if (parsed.isSuccess && parsed.extractedStatus != null) {
            val oldCategory = trip.statusCategory
            val newCategory = parsed.category
            val newStatusString = parsed.extractedStatus

            // Update trip live status and mark check as successful
            repository.updateTripPnrStatus(
                tripId = trip.id,
                newStatus = newStatusString,
                checkedAtIso = isoNow,
                rawReply = fullBody,
                checkId = pendingCheck.id,
                success = true
            )

            // 6. Widget must update after each status change
            TravelTrackWidgetUpdater.updateAllWidgets(context)

            // 8. Post notification when status changes to CONFIRMED or RAC, or to CANCELLED
            val shouldNotify = (newCategory == PnrStatusCategory.CONFIRMED && oldCategory != PnrStatusCategory.CONFIRMED) ||
                (newCategory == PnrStatusCategory.RAC && oldCategory != PnrStatusCategory.RAC) ||
                (newCategory == PnrStatusCategory.CANCELLED && oldCategory != PnrStatusCategory.CANCELLED)

            if (shouldNotify) {
                postStatusChangeNotification(
                    context = context,
                    trip = trip,
                    newStatus = newStatusString
                )
            }
        } else {
            // 4. If parsing fails, keep the old status and show raw reply to user for manual entry
            repository.updateTripPnrStatus(
                tripId = trip.id,
                newStatus = trip.effectiveStatus, // keep old status
                checkedAtIso = isoNow,
                rawReply = fullBody,
                checkId = pendingCheck.id,
                success = false
            )
            TravelTrackWidgetUpdater.updateAllWidgets(context)
        }
    }

    private fun postStatusChangeNotification(
        context: Context,
        trip: Trip,
        newStatus: String
    ) {
        ensurePnrNotificationChannel(context)

        // Privacy rule: Only show the last 4 digits of the PNR in the notification
        val pnrMasked = "••••${trip.maskedReferenceLast4()}"
        val title = "PNR STATUS UPDATE // ${trip.title}"
        val message = "${trip.title} (PNR $pnrMasked): now $newStatus."

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("traveltrack://trip/${trip.id}")
            putExtra(MainActivity.EXTRA_TRIP_ID, trip.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            (trip.id + 5000).toInt(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_PNR_STATUS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify((trip.id + 5000).toInt(), notification)
        }
    }
}
