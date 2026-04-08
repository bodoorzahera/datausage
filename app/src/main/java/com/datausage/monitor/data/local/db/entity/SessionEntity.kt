package com.datausage.monitor.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["profileId"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("profileId")]
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val sessionId: Long = 0,
    val profileId: Long,
    val startTime: Long,
    val endTime: Long? = null,
    val totalBytesRx: Long = 0,
    val totalBytesTx: Long = 0,
    val networkType: Int = 0 // 0=all, 1=wifi, 2=mobile
)
