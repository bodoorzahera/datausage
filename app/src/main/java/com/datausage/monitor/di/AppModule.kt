package com.datausage.monitor.di

import android.content.Context
import com.datausage.monitor.util.NetworkUsageHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideNetworkUsageHelper(@ApplicationContext context: Context): NetworkUsageHelper {
        return NetworkUsageHelper(context)
    }
}
