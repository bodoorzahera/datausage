package com.datausage.monitor.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.datausage.monitor.data.local.db.dao.AppUsageDao
import com.datausage.monitor.data.local.db.dao.CostConfigDao
import com.datausage.monitor.data.local.db.dao.DataLimitDao
import com.datausage.monitor.data.local.db.dao.MonitoredAppDao
import com.datausage.monitor.data.local.db.dao.ProfileDao
import com.datausage.monitor.data.local.db.dao.SessionDao
import com.datausage.monitor.data.local.db.entity.AppUsageEntity
import com.datausage.monitor.data.local.db.entity.CostConfigEntity
import com.datausage.monitor.data.local.db.entity.DataLimitEntity
import com.datausage.monitor.data.local.db.entity.MonitoredAppEntity
import com.datausage.monitor.data.local.db.entity.ProfileEntity
import com.datausage.monitor.data.local.db.entity.SessionEntity

@Database(
    entities = [
        ProfileEntity::class,
        MonitoredAppEntity::class,
        SessionEntity::class,
        AppUsageEntity::class,
        DataLimitEntity::class,
        CostConfigEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun monitoredAppDao(): MonitoredAppDao
    abstract fun sessionDao(): SessionDao
    abstract fun appUsageDao(): AppUsageDao
    abstract fun dataLimitDao(): DataLimitDao
    abstract fun costConfigDao(): CostConfigDao
}
