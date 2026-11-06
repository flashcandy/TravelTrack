package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import java.util.Locale

enum class TripType {
    TRAIN,
    BUS
}

class TripTypeConverters {
    @TypeConverter
    fun fromTripType(type: TripType): String = type.name

    @TypeConverter
    fun toTripType(value: String): TripType =
        runCatching { TripType.valueOf(value) }.getOrDefault(TripType.TRAIN)
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
    val status: String,
    val passengerNames: String,
    val seatNumbers: String? = null,
    val travelClass: String? = null,
    val bookingStatus: String? = null,
    val pickupPoint: String? = null,
    val dropPoint: String? = null,
    val originStartTime: String? = null,
    val transactionId: String? = null,
    val rawExtractedText: String? = null,
    val sourceFileUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val amountInRupees: Double
        get() = amount / 100.0

    fun formattedAmount(): String {
        val rupees = amount / 100.0
        return if (amount % 100L == 0L) {
            String.format(Locale.US, "₹%,.0f", rupees)
        } else {
            String.format(Locale.US, "₹%,.2f", rupees)
        }
    }

    fun isWaitlisted(): Boolean {
        val normalized = status.trim().uppercase(Locale.US)
        return normalized.startsWith("RLWL") ||
            normalized.startsWith("PQWL") ||
            normalized.startsWith("RSWL") ||
            normalized.startsWith("WL") ||
            normalized.contains("/WL") ||
            normalized.contains("WAITLIST")
    }

    /**
     * Privacy rule: Do not include the full PNR or passenger names on the widget.
     * Show the last 4 characters of the reference only.
     */
    fun maskedReferenceLast4(): String {
        val cleaned = reference.trim().trimEnd('#')
        val source = if (cleaned.length >= 4) cleaned else reference.trim()
        return if (source.length <= 4) source else source.takeLast(4)
    }

    val departureDate: String
        get() = departureDateTime.substringBefore('T')

    val departureTime: String
        get() = departureDateTime.substringAfter('T', "")

    val arrivalDate: String?
        get() = arrivalDateTime?.substringBefore('T')?.takeIf { it.isNotBlank() }

    val arrivalTime: String?
        get() = arrivalDateTime?.substringAfter('T', "")?.takeIf { it.isNotBlank() }
}
