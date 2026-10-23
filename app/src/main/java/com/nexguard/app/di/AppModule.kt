package com.nexguard.app.di

import android.content.Context
import com.nexguard.core.database.AppDatabase
import com.nexguard.core.database.DatabaseProvider
import com.nexguard.core.eventbus.EventBus

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun provideDb(@ApplicationContext ctx: Context): AppDatabase = DatabaseProvider.get(ctx)

    @Provides @Singleton
    fun provideEventBus(): EventBus = EventBus()

    // Scanner, QuarantineManager, AlertManager, DownloadMonitor use @Inject constructors
}