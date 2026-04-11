package com.datausage.monitor.di

import android.content.Context
import androidx.room.Room
import com.datausage.monitor.data.local.db.AppDatabase
import com.datausage.monitor.data.local.db.dao.AppUsageDao
import com.datausage.monitor.data.local.db.dao.CostConfigDao
import com.datausage.monitor.data.local.db.dao.DataLimitDao
import com.datausage.monitor.data.local.db.dao.MonitoredAppDao
import com.datausage.monitor.data.local.db.dao.ProfileDao
import com.datausage.monitor.data.local.db.dao.SessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "data_usage.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideProfileDao(db: AppDatabase): ProfileDao = db.profileDao()

    @Provides
    fun provideMonitoredAppDao(db: AppDatabase): MonitoredAppDao = db.monitoredAppDao()

    @Provides
    fun provideSessionDao(db: AppDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideAppUsageDao(db: AppDatabase): AppUsageDao = db.appUsageDao()

    @Provides
    fun provideDataLimitDao(db: AppDatabase): DataLimitDao = db.dataLimitDao()

    @Provides
    fun provideCostConfigDao(db: AppDatabase): CostConfigDao = db.costConfigDao()
}
