package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PnrStatusCheckDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCheck(check: PnrStatusCheck): Long

    @Update
    suspend fun updateCheck(check: PnrStatusCheck)

    @Query(
        """
        SELECT * FROM pnr_status_checks 
        WHERE pnr = :pnr 
          AND replyReceivedAt IS NULL 
          AND requestedAt >= :sinceTimestamp
        ORDER BY requestedAt DESC 
        LIMIT 1
        """
    )
    suspend fun findPendingCheck(pnr: String, sinceTimestamp: Long): PnrStatusCheck?

    @Query("SELECT * FROM pnr_status_checks WHERE tripId = :tripId ORDER BY requestedAt DESC")
    fun observeChecksForTrip(tripId: Long): Flow<List<PnrStatusCheck>>

    @Query("SELECT * FROM pnr_status_checks WHERE tripId = :tripId ORDER BY requestedAt DESC LIMIT 1")
    suspend fun getLatestCheckForTrip(tripId: Long): PnrStatusCheck?

    @Query("SELECT * FROM pnr_status_checks ORDER BY requestedAt DESC")
    fun observeAllChecks(): Flow<List<PnrStatusCheck>>

    @Query("DELETE FROM pnr_status_checks")
    suspend fun deleteAllChecks()

    @Query("DELETE FROM pnr_status_checks WHERE tripId = :tripId")
    suspend fun deleteChecksForTrip(tripId: Long)
}
