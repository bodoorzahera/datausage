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
    val wifiRx: Long = 0,      // WiFi download
    val wifiTx: Long = 0,      // WiFi upload
    val mobileRx: Long = 0,    // Mobile download
    val mobileTx: Long = 0     // Mobile upload
) {
    val totalRx: Long get() = wifiRx + mobileRx
    val totalTx: Long get() = wifiTx + mobileTx
    val totalBytes: Long get() = totalRx + totalTx
    val wifiTotal: Long get() = wifiRx + wifiTx
    val mobileTotal: Long get() = mobileRx + mobileTx
}
