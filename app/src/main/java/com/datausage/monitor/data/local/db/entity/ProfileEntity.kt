package com.datausage.monitor.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val profileId: Long = 0,
    val name: String,
    val pin: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)
