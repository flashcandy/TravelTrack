package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY departureDateTime ASC")
    fun observeAllTrips(): Flow<List<Trip>>

    @Query("SELECT * FROM trips ORDER BY departureDateTime ASC")
    suspend fun getAllTripsSnapshot(): List<Trip>

    @Query("SELECT * FROM trips WHERE id = :id LIMIT 1")
    fun observeTripById(id: Long): Flow<Trip?>

    @Query("SELECT * FROM trips WHERE id = :id LIMIT 1")
    suspend fun getTripById(id: Long): Trip?

    @Query("SELECT * FROM trips WHERE reference = :reference AND reference != '' LIMIT 1")
    suspend fun getTripByReference(reference: String): Trip?

    @Query("SELECT * FROM trips WHERE reference LIKE '%' || :pnrDigits || '%' LIMIT 1")
    suspend fun getTripByPnrDigits(pnrDigits: String): Trip?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: Trip): Long

    @Update
    suspend fun updateTrip(trip: Trip)

    @Query(
        """
        UPDATE trips 
        SET currentStatus = :newCurrentStatus, 
            lastCheckedAt = :checkedAt, 
            lastRawSmsReply = :rawReply 
        WHERE id = :tripId
        """
    )
    suspend fun updatePnrStatus(
        tripId: Long,
        newCurrentStatus: String,
        checkedAt: String,
        rawReply: String?
    )

    @Delete
    suspend fun deleteTrip(trip: Trip)

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteTripById(id: Long)

    @Query("DELETE FROM trips")
    suspend fun deleteAllTrips()
}
