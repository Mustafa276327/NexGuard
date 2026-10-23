package com.nexguard.app

import android.app.Application
import com.nexguard.core.database.DatabaseProvider
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NexGuardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // warm up encrypted db
        DatabaseProvider.get(this)
    }
}