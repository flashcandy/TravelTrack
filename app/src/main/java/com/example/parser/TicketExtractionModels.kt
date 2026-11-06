package com.example.parser

import com.example.data.Trip
import com.example.data.TripType
import java.util.Locale

enum class TicketFormat(val displayName: String) {
    IRCTC_TRAIN("IRCTC Electronic Reservation Slip"),
    REDBUS_TSRTC("redBus / TSRTC Bus Ticket"),
    UNKNOWN("Unrecognized Format (Manual Review)")
}

enum class ConfidenceLevel(val label: String) {
    HIGH("Verified Label Match"),
    MEDIUM("Inferred Pattern"),
    LOW("Needs Verification"),
    MANUAL("Manual Input Required")
}

data class ParsedTicketDraft(
    val format: TicketFormat,
    val type: TripType,
    val operator: String = "",
    val trainNumber: String = "",
    val trainName: String = "",
    val travelClass: String = "",
    val originName: String = "",
    val originCode: String = "",
    val destinationName: String = "",
    val destinationCode: String = "",
    val pickupPoint: String = "",
    val dropPoint: String = "",
    /** ISO yyyy-MM-dd */
    val departureDate: String = "",
    /** 24-hour HH:mm */
    val departureTime: String = "",
    /** ISO yyyy-MM-dd */
    val arrivalDate: String = "",
    /** 24-hour HH:mm */
    val arrivalTime: String = "",
    /** Normalized ISO yyyy-MM-dd'T'HH:mm or raw normalized string */
    val originStartTime: String = "",
    /** Amount in paise (e.g. 20180L for ₹201.80) */
    val amountPaise: Long = 0L,
    val currency: String = "INR",
    /** PNR or Ticket No */
    val reference: String = "",
    val bookingStatus: String = "",
    val currentStatus: String = "",
    /** Effective status (currentStatus if present, else bookingStatus or status) */
    val status: String = "",
    val passengerNames: String = "",
    val seatNumbers: String = "",
    val transactionId: String = "",
    val rawText: String = "",
    val fieldConfidences: Map<String, ConfidenceLevel> = emptyMap(),
    val fieldHints: Map<String, String> = emptyMap()
) {
    val departureDateTimeIso: String
        get() = if (departureDate.isNotBlank() && departureTime.isNotBlank()) {
            "${departureDate.trim()}T${departureTime.trim()}"
        } else {
            departureDate.trim()
        }

    val arrivalDateTimeIso: String?
        get() = if (arrivalDate.isNotBlank() && arrivalTime.isNotBlank()) {
            "${arrivalDate.trim()}T${arrivalTime.trim()}"
        } else {
            null
        }

    fun buildRouteTitle(): String {
        val cleanOrigin = formatCityDisplayName(originName).ifBlank { originCode.ifBlank { "Origin" } }
        val cleanDest = formatCityDisplayName(destinationName).ifBlank { destinationCode.ifBlank { "Destination" } }
        return "$cleanOrigin → $cleanDest"
    }

    fun toTrip(sourceFileUri: String? = null, existingId: Long = 0L): Trip {
        val effectiveOperator = when {
            operator.isNotBlank() -> operator.trim()
            trainNumber.isNotBlank() || trainName.isNotBlank() ->
                listOf(trainNumber.trim(), trainName.trim()).filter { it.isNotEmpty() }.joinToString("/")
            type == TripType.TRAIN -> "IRCTC Indian Railways"
            else -> "Bus Operator"
        }
        val effectiveStatus = status.ifBlank { currentStatus.ifBlank { bookingStatus.ifBlank { "Confirmed" } } }

        return Trip(
            id = existingId,
            type = type,
            operator = effectiveOperator,
            title = buildRouteTitle(),
            originName = originName.trim(),
            originCode = originCode.trim(),
            destinationName = destinationName.trim(),
            destinationCode = destinationCode.trim(),
            departureDateTime = departureDateTimeIso,
            arrivalDateTime = arrivalDateTimeIso,
            amount = amountPaise,
            currency = currency.ifBlank { "INR" },
            reference = reference.trim(),
            status = effectiveStatus.trim(),
            passengerNames = passengerNames.trim(),
            seatNumbers = seatNumbers.trim().takeIf { it.isNotEmpty() },
            travelClass = travelClass.trim().takeIf { it.isNotEmpty() },
            bookingStatus = bookingStatus.trim().takeIf { it.isNotEmpty() },
            pickupPoint = pickupPoint.trim().takeIf { it.isNotEmpty() },
            dropPoint = dropPoint.trim().takeIf { it.isNotEmpty() },
            originStartTime = originStartTime.trim().takeIf { it.isNotEmpty() },
            transactionId = transactionId.trim().takeIf { it.isNotEmpty() },
            rawExtractedText = rawText.takeIf { it.isNotBlank() },
            sourceFileUri = sourceFileUri
        )
    }

    companion object {
        fun formatCityDisplayName(raw: String): String {
            val cleaned = raw.trim()
                .replace(Regex("\\s+JN\\.?$", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s+BS$", RegexOption.IGNORE_CASE), "")
                .trim()
            if (cleaned.isEmpty()) return ""
            // Keep short all-caps acronyms like KPHB intact
            if (cleaned.length <= 4 && cleaned.all { it.isUpperCase() }) {
                return cleaned
            }
            return cleaned.lowercase(Locale.US).split(Regex("\\s+")).joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
            }
        }
    }
}
