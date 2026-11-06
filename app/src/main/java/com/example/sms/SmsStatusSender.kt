package com.example.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.example.data.AppDatabase
import com.example.data.Trip
import com.example.data.TripRepository

sealed interface SmsSendResult {
    data class Success(val checkId: Long, val pnr: String, val sentTo: String = "139") : SmsSendResult
    data class Error(val message: String) : SmsSendResult
    data object PermissionDenied : SmsSendResult
}

object SmsStatusSender {

    const val DESTINATION_NUMBER = "139"
    const val TIMEOUT_MILLIS = 180_000L // 3 minutes

    suspend fun sendPnrStatusCheck(
        context: Context,
        trip: Trip,
        isManualTrigger: Boolean = true
    ): SmsSendResult {
        // Check permission
        val hasSendPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasSendPermission) {
            return SmsSendResult.PermissionDenied
        }

        val cleanPnr = trip.cleanPnr()
        if (cleanPnr.length != 10) {
            return SmsSendResult.Error(
                "Invalid PNR: PNR must be exactly 10 digits (found: '${trip.reference}')"
            )
        }

        val database = AppDatabase.getInstance(context)
        val repository = TripRepository(database.tripDao(), database.pnrStatusCheckDao())

        val checkId = repository.recordPnrCheckRequest(
            tripId = trip.id,
            pnr = cleanPnr,
            isManual = isManualTrigger
        )

        val smsText = "PNR $cleanPnr"

        return try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(smsText)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(
                    DESTINATION_NUMBER,
                    null,
                    parts,
                    null,
                    null
                )
            } else {
                smsManager.sendTextMessage(
                    DESTINATION_NUMBER,
                    null,
                    smsText,
                    null,
                    null
                )
            }

            SmsSendResult.Success(checkId = checkId, pnr = cleanPnr)
        } catch (e: Exception) {
            SmsSendResult.Error(e.message ?: "Failed to dispatch SMS to 139")
        }
    }
}
