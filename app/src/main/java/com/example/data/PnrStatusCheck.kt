package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pnr_status_checks",
    indices = [
        Index(value = ["tripId"]),
        Index(value = ["pnr"])
    ]
)
data class PnrStatusCheck(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val tripId: Long,
    val pnr: String,
    val requestedAt: Long = System.currentTimeMillis(),
    val method: String = "PREFILLED_SMS",
    val replyReceivedAt: Long? = null,
    val rawReply: String? = null,
    val parsedStatus: String? = null,
    val success: Boolean = false,
    val isManual: Boolean = false
)
