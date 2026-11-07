package com.example.sms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.PnrStatusCategory
import com.example.data.Trip
import com.example.data.TripRepository
import com.example.widget.TravelTrackWidgetUpdater
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PnrReplyListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val CHANNEL_PNR_STATUS = "travel_track_pnr_status_channel"

        fun isNotificationListenerGranted(context: Context): Boolean {
            val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
            return enabledListeners.contains(context.packageName)
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

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        // 1. Ignore notifications from our own app package
        if (sbn.packageName == packageName) return

        // Ignore group summary notifications to prevent duplicate bundle processing
        val notification = sbn.notification ?: return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val extras = notification.extras ?: return

        // 2. Read sender title and text
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()

        // Read EXTRA_MESSAGES for messaging-style notifications
        val messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        val messagesContent = messagingStyle?.messages?.joinToString("\n") { msg ->
            "${msg.person?.name ?: ""}: ${msg.text}"
        }

        val combinedBody = when {
            !bigText.isNullOrBlank() -> bigText
            !text.isNullOrBlank() -> text
            !messagesContent.isNullOrBlank() -> messagesContent
            else -> ""
        }.trim()

        val candidateSender = when {
            PnrReplyParser.isSender139(title) -> title
            PnrReplyParser.isSender139(subText) -> subText
            messagingStyle?.conversationTitle != null && PnrReplyParser.isSender139(messagingStyle.conversationTitle?.toString()) ->
                messagingStyle.conversationTitle.toString()
            else -> null
        }

        // 3. Only process if the title or sender identifies 139 (or the text contains explicit 139 railway inquiry header)
        val isFrom139 = candidateSender != null ||
            PnrReplyParser.isSender139(title) ||
            (title.contains("139") && !title.contains("1390"))

        if (!isFrom139) {
            // Strictly ignore all non-139 notifications immediately without persisting any text
            return
        }

        if (combinedBody.isEmpty()) return

        // 4. Asynchronously process the notification against active pending checks
        serviceScope.launch {
            handle139Notification(combinedBody)
        }
    }

    private suspend fun handle139Notification(body: String) {
        val database = AppDatabase.getInstance(applicationContext)
        val repository = TripRepository(database.tripDao(), database.pnrStatusCheckDao())

        // 5. Only process if a PNR check is pending from the last 3 minutes
        val pendingChecks = repository.getPendingChecks(maxAgeMillis = 180_000L)
        if (pendingChecks.isEmpty()) {
            // No active pending check in last 3 minutes — discard notification immediately
            return
        }

        // Check if any pending check matches full PNR or last 4 digits
        var matchedCheck: com.example.data.PnrStatusCheck? = null
        var matchedResult: PnrParseResult? = null

        for (pending in pendingChecks) {
            val pendingLast4 = pending.pnr.takeLast(4)
            val parseResult = PnrReplyParser.parse(body, pendingPnrLast4 = pendingLast4)
            if (parseResult.matchedPnrLast4 == pendingLast4 || (parseResult.pnr != null && parseResult.pnr == pending.pnr)) {
                matchedCheck = pending
                matchedResult = parseResult
                break
            }
        }

        if (matchedCheck == null || matchedResult == null) {
            // Discard: notification text does not match any pending check
            return
        }

        val trip = repository.getTripById(matchedCheck.tripId)
            ?: repository.getTripByPnr(matchedCheck.pnr)
            ?: return

        val isoNow = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))

        if (matchedResult.isSuccess && matchedResult.extractedStatus != null) {
            val oldCategory = trip.statusCategory
            val newCategory = matchedResult.category
            val newStatusString = matchedResult.extractedStatus

            // Update trip current status and mark check completed
            repository.updateTripPnrStatus(
                tripId = trip.id,
                newStatus = newStatusString,
                checkedAtIso = isoNow,
                rawReply = body,
                checkId = matchedCheck.id,
                success = true
            )

            // Refresh home screen widget immediately
            TravelTrackWidgetUpdater.updateAllWidgets(applicationContext)

            // Post local notification when status changes to CONFIRMED, RAC, or CANCELLED
            val shouldNotify = (newCategory == PnrStatusCategory.CONFIRMED && oldCategory != PnrStatusCategory.CONFIRMED) ||
                (newCategory == PnrStatusCategory.RAC && oldCategory != PnrStatusCategory.RAC) ||
                (newCategory == PnrStatusCategory.CANCELLED && oldCategory != PnrStatusCategory.CANCELLED)

            if (shouldNotify) {
                postStatusChangeNotification(
                    context = applicationContext,
                    trip = trip,
                    newStatus = newStatusString
                )
            }
        } else {
            // If parsing status fails, keep existing status, save raw reply for user review
            repository.updateTripPnrStatus(
                tripId = trip.id,
                newStatus = trip.effectiveStatus,
                checkedAtIso = isoNow,
                rawReply = body,
                checkId = matchedCheck.id,
                success = false
            )
            TravelTrackWidgetUpdater.updateAllWidgets(applicationContext)
        }
    }

    private fun postStatusChangeNotification(
        context: Context,
        trip: Trip,
        newStatus: String
    ) {
        ensurePnrNotificationChannel(context)

        // Privacy rule: Only show the last 4 digits of the PNR (••••7777)
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
