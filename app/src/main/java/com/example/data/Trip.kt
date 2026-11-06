package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import java.time.Duration
import java.time.LocalDateTime
import java.util.Locale

import com.example.parser.Passenger

enum class TripType {
    TRAIN,
    BUS
}

enum class PnrStatusCategory {
    CONFIRMED,
    RAC,
    WAITLISTED,
    CANCELLED,
    CHART_PREPARED,
    UNKNOWN
}

class TripTypeConverters {
    @TypeConverter
    fun fromTripType(type: TripType): String = type.name

    @TypeConverter
    fun toTripType(value: String): TripType =
        runCatching { TripType.valueOf(value) }.getOrDefault(TripType.TRAIN)

    @TypeConverter
    fun fromPassengerList(passengers: List<Passenger>?): String {
        if (passengers.isNullOrEmpty()) return ""
        return passengers.joinToString("\n") {
            "${it.name}|${it.age ?: ""}|${it.gender}|${it.bookingStatus}|${it.currentStatus}"
        }
    }

    @TypeConverter
    fun toPassengerList(raw: String?): List<Passenger> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lines().mapNotNull { line ->
            val parts = line.split("|")
            if (parts.isNotEmpty() && parts[0].isNotBlank()) {
                Passenger(
                    name = parts[0],
                    age = parts.getOrNull(1)?.toIntOrNull(),
                    gender = parts.getOrNull(2).orEmpty(),
                    bookingStatus = parts.getOrNull(3).orEmpty(),
                    currentStatus = parts.getOrNull(4).orEmpty()
                )
            } else null
        }
    }
}

@Entity(
    tableName = "trips",
    indices = [Index(value = ["reference"])]
)
data class Trip(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val type: TripType,
    val operator: String,
    val title: String,
    val originName: String,
    val originCode: String,
    val destinationName: String,
    val destinationCode: String,
    /** ISO 8601 local date-time: yyyy-MM-dd'T'HH:mm */
    val departureDateTime: String,
    /** Nullable ISO 8601 local date-time: yyyy-MM-dd'T'HH:mm */
    val arrivalDateTime: String? = null,
    /** Stored in paise (e.g., 20180L for ₹201.80, 125000L for ₹1250.00) */
    val amount: Long,
    val currency: String = "INR",
    /** PNR or Ticket Number */
    val reference: String,
    /** Fallback or original extracted status string */
    val status: String,
    val passengerNames: String,
    val passengers: List<Passenger> = emptyList(),
    val seatNumbers: String? = null,
    val travelClass: String? = null,
    /** Booking status kept distinct from current live status (e.g., "RLWL/29") */
    val bookingStatus: String? = null,
    val pickupPoint: String? = null,
    val dropPoint: String? = null,
    val originStartTime: String? = null,
    val transactionId: String? = null,
    val rawExtractedText: String? = null,
    val sourceFileUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** Live current status updated via 139 SMS check or manual override (e.g., "CNF/B2/31", "RAC/12", "WL/3") */
    val currentStatus: String? = null,
    /** ISO 8601 datetime when PNR status was last checked/updated */
    val lastCheckedAt: String? = null,
    /** Raw 139 SMS reply text retained for debugging and user review */
    val lastRawSmsReply: String? = null
) {
    val amountInRupees: Double
        get() = amount / 100.0

    /**
     * Train formatted as "11020 KONARK EXPRESS"
     */
    val train: String
        get() = operatorDisplay()

    fun operatorDisplay(): String {
        return operator.replace("/", " ").trim()
    }

    /**
     * Effective status prefers the live updated currentStatus if present, falling back to bookingStatus or status.
     */
    val effectiveStatus: String
        get() = currentStatus?.trim()?.takeIf { it.isNotEmpty() }
            ?: bookingStatus?.trim()?.takeIf { it.isNotEmpty() }
            ?: status.trim()

    /**
     * Status displayed on the boarding pass badge:
     * Prefers current live status, then booking status, then raw status.
     */
    fun displayStatus(): String {
        return currentStatus?.trim()?.takeIf { it.isNotEmpty() }
            ?: bookingStatus?.trim()?.takeIf { it.isNotEmpty() }
            ?: status.trim()
    }

    val statusCategory: PnrStatusCategory
        get() = resolveStatusCategory(effectiveStatus)

    fun formattedPassengersDisplay(): String {
        return if (passengers.isNotEmpty()) {
            passengers.joinToString(", ") { it.displayNameWithDetails() }
        } else {
            passengerNames
        }
    }

    fun formattedAmount(): String {
        val rupees = amount / 100.0
        return if (amount % 100L == 0L) {
            String.format(Locale.US, "₹%,.0f", rupees)
        } else {
            String.format(Locale.US, "₹%,.2f", rupees)
        }
    }

    fun isWaitlisted(): Boolean = statusCategory == PnrStatusCategory.WAITLISTED

    fun isRac(): Boolean = statusCategory == PnrStatusCategory.RAC

    fun isConfirmed(): Boolean = statusCategory == PnrStatusCategory.CONFIRMED

    fun isCancelled(): Boolean = statusCategory == PnrStatusCategory.CANCELLED

    companion object {
        fun resolveStatusCategory(statusStr: String?): PnrStatusCategory {
            if (statusStr.isNullOrBlank()) return PnrStatusCategory.UNKNOWN
            val upper = statusStr.trim().uppercase(Locale.US)
            if (upper.contains("NOT CHECKED") || upper == "N/A" || upper == "UNKNOWN") {
                return PnrStatusCategory.UNKNOWN
            }
            return when {
                upper.startsWith("CAN") || upper.contains("CANCEL") -> PnrStatusCategory.CANCELLED
                upper.contains("CHART PREPARED") -> PnrStatusCategory.CHART_PREPARED
                upper.startsWith("CNF") || upper.startsWith("CONFIRM") -> PnrStatusCategory.CONFIRMED
                upper.startsWith("RAC") -> PnrStatusCategory.RAC
                upper.startsWith("RLWL") || upper.startsWith("PQWL") ||
                    upper.startsWith("RSWL") || upper.startsWith("TQWL") ||
                    upper.startsWith("GNWL") || upper.startsWith("WL") ||
                    upper.contains("/WL") || upper.contains("WAITLIST") -> PnrStatusCategory.WAITLISTED
                else -> PnrStatusCategory.UNKNOWN
            }
        }
    }

    /**
     * Determines whether this trip can have its PNR checked via SMS to 139.
     * Default rule: Train with a waitlisted or RAC status (or any train if showForConfirmed is toggled).
     */
    fun isPnrCheckEligible(allowConfirmed: Boolean = false): Boolean {
        if (type != TripType.TRAIN) return false
        if (cleanPnr().length != 10) return false
        if (isCancelled()) return false
        if (isWaitlisted() || isRac()) return true
        return allowConfirmed && isConfirmed()
    }

    fun cleanPnr(): String = reference.trim().filter { it.isDigit() }

    /**
     * Privacy rule: Show the last 4 characters of the reference on widgets/lists.
     */
    fun maskedReferenceLast4(): String {
        return if (type == TripType.TRAIN) {
            val digits = cleanPnr()
            if (digits.length >= 4) digits.takeLast(4) else digits.ifEmpty { reference.takeLast(4) }
        } else {
            val cleaned = reference.trim().trimEnd('#')
            if (cleaned.length <= 4) cleaned else cleaned.takeLast(4)
        }
    }

    /**
     * Displays PNR as "•••• 7777" for privacy in headers and non-checking screens.
     */
    fun formattedMaskedPnr(): String {
        val last4 = maskedReferenceLast4()
        return "•••• $last4"
    }

    val departureDate: String
        get() = departureDateTime.substringBefore('T')

    val departureTime: String
        get() = departureDateTime.substringAfter('T', "")

    val arrivalDate: String?
        get() = arrivalDateTime?.substringBefore('T')?.takeIf { it.isNotBlank() }

    val arrivalTime: String?
        get() = arrivalDateTime?.substringAfter('T', "")?.takeIf { it.isNotBlank() }

    fun formattedLastChecked(now: LocalDateTime = LocalDateTime.now()): String {
        if (lastCheckedAt.isNullOrBlank()) return "Not checked yet"
        val checkedDt = TripFilter.parseIsoDateTime(lastCheckedAt) ?: return "Checked recently"
        val duration = Duration.between(checkedDt, now)
        val minutes = duration.toMinutes()
        return when {
            minutes < 1 -> "Checked just now"
            minutes < 60 -> "Checked ${minutes}m ago"
            minutes < 1440 -> "Checked ${minutes / 60}h ago"
            else -> "Checked ${minutes / 1440}d ago"
        }
    }
}
