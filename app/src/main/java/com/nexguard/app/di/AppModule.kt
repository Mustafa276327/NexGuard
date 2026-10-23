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
    @Provides
    @Singleton
    fun provideEventBus(): EventBus = EventBus()

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        DatabaseProvider.get(context)
}