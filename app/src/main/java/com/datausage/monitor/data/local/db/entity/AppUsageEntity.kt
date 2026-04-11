package com.datausage.monitor.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_usage",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MonitoredAppEntity::class,
            parentColumns = ["id"],
            childColumns = ["monitoredAppId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId"), Index("monitoredAppId")]
)
data class AppUsageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val monitoredAppId: Long,
    val packageName: String,
    val timestamp: Long,
    val wifiRx: Long = 0,      // WiFi download
    val wifiTx: Long = 0,      // WiFi upload
    val mobileRx: Long = 0,    // Mobile download
    val mobileTx: Long = 0     // Mobile upload
)
