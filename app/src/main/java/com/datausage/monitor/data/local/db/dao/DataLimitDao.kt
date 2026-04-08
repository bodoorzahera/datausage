package com.datausage.monitor.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.datausage.monitor.data.local.db.entity.DataLimitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DataLimitDao {

    @Query("SELECT * FROM data_limits WHERE profileId = :profileId ORDER BY periodType ASC")
    fun getLimitsForProfile(profileId: Long): Flow<List<DataLimitEntity>>

    @Query("SELECT * FROM data_limits WHERE profileId = :profileId")
    suspend fun getLimitsForProfileList(profileId: Long): List<DataLimitEntity>

    @Query("SELECT * FROM data_limits WHERE profileId = :profileId AND packageName IS NULL")
    suspend fun getProfileWideLimits(profileId: Long): List<DataLimitEntity>

    @Query("SELECT * FROM data_limits WHERE profileId = :profileId AND packageName = :packageName")
    suspend fun getAppLimits(profileId: Long, packageName: String): List<DataLimitEntity>

    @Query("SELECT * FROM data_limits WHERE id = :id")
    suspend fun getById(id: Long): DataLimitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(limit: DataLimitEntity): Long

    @Update
    suspend fun update(limit: DataLimitEntity)

    @Delete
    suspend fun delete(limit: DataLimitEntity)

    @Query("DELETE FROM data_limits WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: Long)

    @Query("DELETE FROM data_limits")
    suspend fun deleteAll()
}
