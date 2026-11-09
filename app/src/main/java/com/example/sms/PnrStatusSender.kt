package com.example.sms

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.example.data.AppDatabase
import com.example.data.Trip
import com.example.data.TripRepository

sealed interface PnrCheckDispatchResult {
    data class PrefilledIntent(val intent: Intent, val checkId: Long, val pnr: String) : PnrCheckDispatchResult
    data class TelegramBotIntent(val intent: Intent, val fallbackIntent: Intent, val checkId: Long, val pnr: String) : PnrCheckDispatchResult
    data class AutoSent(val checkId: Long, val pnr: String) : PnrCheckDispatchResult
    data class Error(val message: String) : PnrCheckDispatchResult
}

// Retain legacy sealed interface for backward compatibility
sealed interface SmsSendResult {
    data class Success(val checkId: Long, val pnr: String, val sentTo: String = "139") : SmsSendResult
    data class Error(val message: String) : SmsSendResult
    data object PermissionDenied : SmsSendResult
}

object PnrStatusSender {

    const val DESTINATION_NUMBER = "139"
    const val TELEGRAM_BOT_USERNAME = "check_pnr_status_bot"
    const val TIMEOUT_MILLIS = 180_000L // 3 minutes

    fun validatePnr(pnr: String): Boolean {
        return pnr.filter { it.isDigit() }.length == 10
    }

    /**
     * Primary SMS method: Prefilled SMS intent requiring ZERO SMS permissions.
     * Uses Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:139")) with putExtra("sms_body", "PNR <10-digit PNR>").
     */
    fun buildPrefilledIntent(pnr: String): Intent {
        val cleanPnr = pnr.filter { it.isDigit() }
        return Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$DESTINATION_NUMBER")).apply {
            putExtra("sms_body", "PNR $cleanPnr")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Telegram Bot method: Deep-links directly into @check_pnr_status_bot inside Telegram app.
     */
    fun buildTelegramBotIntent(botUsername: String = TELEGRAM_BOT_USERNAME): Intent {
        val cleanBot = botUsername.trim().removePrefix("@").ifBlank { TELEGRAM_BOT_USERNAME }
        return Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=$cleanBot")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun buildTelegramWebFallbackIntent(botUsername: String = TELEGRAM_BOT_USERNAME): Intent {
        val cleanBot = botUsername.trim().removePrefix("@").ifBlank { TELEGRAM_BOT_USERNAME }
        return Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$cleanBot")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Dispatches a PNR status check via Telegram Bot (@check_pnr_status_bot):
     * 1. Copies the 10-digit PNR to clipboard for instant 1-tap paste in Telegram.
     * 2. Saves a PnrStatusCheck record with method "TELEGRAM_BOT".
     * 3. Returns the deep-link intent to open @check_pnr_status_bot.
     */
    suspend fun dispatchTelegramBotCheck(
        context: Context,
        trip: Trip,
        botUsername: String = TELEGRAM_BOT_USERNAME,
        isManualTrigger: Boolean = true
    ): PnrCheckDispatchResult {
        val cleanPnr = trip.cleanPnr()
        if (cleanPnr.length != 10) {
            return PnrCheckDispatchResult.Error(
                "Invalid PNR: PNR must be exactly 10 digits (found: '${trip.reference}')"
            )
        }

        // Copy PNR to clipboard so user only has to paste & send in @check_pnr_status_bot
        runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("PNR Number", cleanPnr))
        }

        val database = AppDatabase.getInstance(context)
        val repository = TripRepository(database.tripDao(), database.pnrStatusCheckDao())

        val checkId = repository.recordPnrCheckRequest(
            tripId = trip.id,
            pnr = cleanPnr,
            isManual = isManualTrigger,
            method = "TELEGRAM_BOT"
        )

        val tgIntent = buildTelegramBotIntent(botUsername)
        val fallbackIntent = buildTelegramWebFallbackIntent(botUsername)
        return PnrCheckDispatchResult.TelegramBotIntent(
            intent = tgIntent,
            fallbackIntent = fallbackIntent,
            checkId = checkId,
            pnr = cleanPnr
        )
    }

    /**
     * Dispatches a PNR status check via 139 SMS.
     * If [autoSendRequested] is true and SEND_SMS permission is granted, sends directly via SmsManager.
     * Otherwise, saves a check record with method "PREFILLED_SMS" and returns the prefilled intent.
     */
    suspend fun dispatchCheck(
        context: Context,
        trip: Trip,
        autoSendRequested: Boolean = false,
        isManualTrigger: Boolean = true
    ): PnrCheckDispatchResult {
        val cleanPnr = trip.cleanPnr()
        if (cleanPnr.length != 10) {
            return PnrCheckDispatchResult.Error(
                "Invalid PNR: PNR must be exactly 10 digits (found: '${trip.reference}')"
            )
        }

        val database = AppDatabase.getInstance(context)
        val repository = TripRepository(database.tripDao(), database.pnrStatusCheckDao())

        val hasSendPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        // If auto-send is enabled and permission is granted, send silently via SmsManager
        if (autoSendRequested && hasSendPermission) {
            val checkId = repository.recordPnrCheckRequest(
                tripId = trip.id,
                pnr = cleanPnr,
                isManual = isManualTrigger,
                method = "AUTO_SMS"
            )

            val smsText = "PNR $cleanPnr"
            val success = trySendViaSmsManager(context, DESTINATION_NUMBER, smsText)
            return if (success) {
                PnrCheckDispatchResult.AutoSent(checkId = checkId, pnr = cleanPnr)
            } else {
                // Fallback to prefilled intent
                val prefilled = buildPrefilledIntent(cleanPnr)
                PnrCheckDispatchResult.PrefilledIntent(intent = prefilled, checkId = checkId, pnr = cleanPnr)
            }
        }

        // Primary zero-permission method: record prefilled check and return intent for user to send
        val checkId = repository.recordPnrCheckRequest(
            tripId = trip.id,
            pnr = cleanPnr,
            isManual = isManualTrigger,
            method = "PREFILLED_SMS"
        )
        val intent = buildPrefilledIntent(cleanPnr)
        return PnrCheckDispatchResult.PrefilledIntent(intent = intent, checkId = checkId, pnr = cleanPnr)
    }

    private fun trySendViaSmsManager(context: Context, destination: String, text: String): Boolean {
        return try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(text)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(destination, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(destination, null, text, null, null)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Legacy helper kept for backward compatibility with existing tests and callers.
     */
    suspend fun sendPnrStatusCheck(
        context: Context,
        trip: Trip,
        isManualTrigger: Boolean = true
    ): SmsSendResult {
        return when (val res = dispatchCheck(context, trip, autoSendRequested = true, isManualTrigger = isManualTrigger)) {
            is PnrCheckDispatchResult.AutoSent -> SmsSendResult.Success(res.checkId, res.pnr)
            is PnrCheckDispatchResult.PrefilledIntent -> SmsSendResult.Success(res.checkId, res.pnr)
            is PnrCheckDispatchResult.TelegramBotIntent -> SmsSendResult.Success(res.checkId, res.pnr)
            is PnrCheckDispatchResult.Error -> SmsSendResult.Error(res.message)
        }
    }
}

// Alias for existing references
typealias SmsStatusSender = PnrStatusSender
