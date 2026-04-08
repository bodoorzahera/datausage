package com.datausage.monitor.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.datausage.monitor.data.local.db.entity.CostConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CostConfigDao {

    @Query("SELECT * FROM cost_config WHERE profileId = :profileId LIMIT 1")
    fun getCostConfig(profileId: Long): Flow<CostConfigEntity?>

    @Query("SELECT * FROM cost_config WHERE profileId = :profileId LIMIT 1")
    suspend fun getCostConfigSync(profileId: Long): CostConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: CostConfigEntity): Long

    @Update
    suspend fun update(config: CostConfigEntity)

    @Query("DELETE FROM cost_config WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: Long)

    @Query("DELETE FROM cost_config")
    suspend fun deleteAll()
}
