package com.nexguard.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ScanResultEntity::class,
        FindingEntity::class,
        QuarantineEntity::class,
        TimelineEntity::class,
        SettingEntity::class,
        FileCacheEntity::class,
        ScanSessionEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanResultDao(): ScanResultDao
    abstract fun findingDao(): FindingDao
    abstract fun quarantineDao(): QuarantineDao
    abstract fun timelineDao(): TimelineDao
    abstract fun settingsDao(): SettingsDao
    abstract fun fileCacheDao(): FileCacheDao
    abstract fun scanSessionDao(): ScanSessionDao
}
