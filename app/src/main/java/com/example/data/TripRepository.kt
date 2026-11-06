package com.example.data

import kotlinx.coroutines.flow.Flow

class TripRepository(private val tripDao: TripDao) {
    val allTrips: Flow<List<Trip>> = tripDao.observeAllTrips()

    fun observeTripById(id: Long): Flow<Trip?> = tripDao.observeTripById(id)

    suspend fun getTripById(id: Long): Trip? = tripDao.getTripById(id)

    suspend fun getAllTripsSnapshot(): List<Trip> = tripDao.getAllTripsSnapshot()

    suspend fun findDuplicateByReference(reference: String, excludeId: Long = 0L): Trip? {
        val cleaned = reference.trim()
        if (cleaned.isEmpty()) return null
        val existing = tripDao.getTripByReference(cleaned)
        return if (existing != null && existing.id != excludeId) existing else null
    }

    /**
     * Saves a trip. If a trip with the same non-empty reference already exists and [overwriteDuplicate] is true,
     * updates the existing record's ID so duplicates are merged cleanly.
     */
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
    }

    suspend fun deleteTripById(id: Long) {
        tripDao.deleteTripById(id)
    }

    suspend fun deleteAllTrips() {
        tripDao.deleteAllTrips()
    }
}
