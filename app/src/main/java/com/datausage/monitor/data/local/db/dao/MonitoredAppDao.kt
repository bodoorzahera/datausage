package com.datausage.monitor.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.datausage.monitor.data.local.db.entity.MonitoredAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MonitoredAppDao {

    @Query("SELECT * FROM monitored_apps WHERE profileId = :profileId ORDER BY appName ASC")
    fun getMonitoredApps(profileId: Long): Flow<List<MonitoredAppEntity>>

    @Query("SELECT * FROM monitored_apps WHERE profileId = :profileId")
    suspend fun getMonitoredAppsList(profileId: Long): List<MonitoredAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: MonitoredAppEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(apps: List<MonitoredAppEntity>)

    @Delete
    suspend fun delete(app: MonitoredAppEntity)

    @Query("DELETE FROM monitored_apps WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: Long)

    @Query("DELETE FROM monitored_apps WHERE profileId = :profileId AND packageName = :packageName")
    suspend fun deleteByPackage(profileId: Long, packageName: String)
}
