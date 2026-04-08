package com.datausage.monitor.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "data_limits",
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
data class DataLimitEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val profileId: Long,
    val packageName: String? = null, // null = profile-wide limit
    val limitBytes: Long,
    val periodType: String, // "daily", "weekly", "monthly", "session"
    val warningPercent: Int = 80,
    val actionOnExceed: String = "notify" // "notify", "warn_dialog", "block_app"
)
