package com.example.data

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.Flow

class TripRepository(
    private val tripDao: TripDao,
    private val pnrCheckDao: PnrStatusCheckDao
) {
    private val isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

    val allTrips: Flow<List<Trip>> = tripDao.observeAllTrips()

    fun observeTripById(id: Long): Flow<Trip?> = tripDao.observeTripById(id)

    suspend fun getTripById(id: Long): Trip? = tripDao.getTripById(id)

    suspend fun getTripByPnr(pnr: String): Trip? {
        val digits = pnr.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        return tripDao.getTripByReference(digits) ?: tripDao.getTripByPnrDigits(digits)
    }

    suspend fun getAllTripsSnapshot(): List<Trip> = tripDao.getAllTripsSnapshot()

    suspend fun findDuplicateByReference(reference: String, excludeId: Long = 0L): Trip? {
        val cleaned = reference.trim()
        if (cleaned.isEmpty()) return null
        val existing = tripDao.getTripByReference(cleaned)
        return if (existing != null && existing.id != excludeId) existing else null
    }

    suspend fun saveTrip(trip: Trip, overwriteDuplicate: Boolean = true): Long {
        val duplicate = findDuplicateByReference(trip.reference, excludeId = trip.id)
        val tripToSave = if (duplicate != null && overwriteDuplicate && trip.id == 0L) {
            trip.copy(id = duplicate.id)
        } else {
            trip
        }
        return tripDao.insertTrip(tripToSave)
    }

    suspend fun deleteTrip(trip: Trip) {
        tripDao.deleteTrip(trip)
        pnrCheckDao.deleteChecksForTrip(trip.id)
    }

    suspend fun deleteTripById(id: Long) {
        tripDao.deleteTripById(id)
        pnrCheckDao.deleteChecksForTrip(id)
    }

    suspend fun deleteAllTrips() {
        tripDao.deleteAllTrips()
        pnrCheckDao.deleteAllChecks()
    }

    // --- PNR Status Check & Logging ---

    suspend fun recordPnrCheckRequest(
        tripId: Long,
        pnr: String,
        isManual: Boolean = false,
        method: String = "PREFILLED_SMS",
        requestTimeMillis: Long = System.currentTimeMillis()
    ): Long {
        val check = PnrStatusCheck(
            tripId = tripId,
            pnr = pnr.filter { it.isDigit() },
            requestedAt = requestTimeMillis,
            method = method,
            isManual = isManual
        )
        return pnrCheckDao.insertCheck(check)
    }

    suspend fun findPendingCheck(
        pnr: String,
        maxAgeMillis: Long = 180_000L
    ): PnrStatusCheck? {
        val digits = pnr.filter { it.isDigit() }
        val threshold = System.currentTimeMillis() - maxAgeMillis
        return pnrCheckDao.findPendingCheck(digits, threshold)
    }

    suspend fun getPendingChecks(maxAgeMillis: Long = 180_000L): List<PnrStatusCheck> {
        val threshold = System.currentTimeMillis() - maxAgeMillis
        return pnrCheckDao.getPendingChecks(threshold)
    }

    suspend fun updateTripPnrStatus(
        tripId: Long,
        newStatus: String,
        checkedAtIso: String = LocalDateTime.now().format(isoFormatter),
        rawReply: String?,
        checkId: Long? = null,
        success: Boolean = true
    ) {
        tripDao.updatePnrStatus(
            tripId = tripId,
            newCurrentStatus = newStatus,
            checkedAt = checkedAtIso,
            rawReply = rawReply
        )

        if (checkId != null) {
            val pending = pnrCheckDao.getLatestCheckForTrip(tripId)
            if (pending != null && pending.id == checkId) {
                pnrCheckDao.updateCheck(
                    pending.copy(
                        replyReceivedAt = System.currentTimeMillis(),
                        rawReply = rawReply,
                        parsedStatus = newStatus,
                        success = success
                    )
                )
            }
        }
    }

    suspend fun recordFailedCheckReply(
        checkId: Long,
        rawReply: String?
    ) {
        val all = pnrCheckDao.getLatestCheckForTrip(0L) // fallback if needed
    }

    suspend fun updateStatusManually(
        tripId: Long,
        manualStatus: String,
        nowIso: String = LocalDateTime.now().format(isoFormatter)
    ) {
        val trip = tripDao.getTripById(tripId) ?: return
        tripDao.updatePnrStatus(
            tripId = tripId,
            newCurrentStatus = manualStatus,
            checkedAt = nowIso,
            rawReply = "[Manual Update: $manualStatus]"
        )

        pnrCheckDao.insertCheck(
            PnrStatusCheck(
                tripId = tripId,
                pnr = trip.cleanPnr(),
                requestedAt = System.currentTimeMillis(),
                replyReceivedAt = System.currentTimeMillis(),
                rawReply = "[Manual Update: $manualStatus]",
                parsedStatus = manualStatus,
                success = true,
                isManual = true
            )
        )
    }

    fun observeChecksForTrip(tripId: Long): Flow<List<PnrStatusCheck>> =
        pnrCheckDao.observeChecksForTrip(tripId)

    suspend fun getLatestCheckForTrip(tripId: Long): PnrStatusCheck? =
        pnrCheckDao.getLatestCheckForTrip(tripId)

    suspend fun clearPnrCheckHistory() {
        pnrCheckDao.deleteAllChecks()
        tripDao.clearAllRawReplies()
    }
}
