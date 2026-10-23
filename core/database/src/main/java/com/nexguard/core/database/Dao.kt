package com.nexguard.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ScanResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(e: ScanResultEntity)

    @Query("SELECT * FROM scan_results ORDER BY startedAt DESC LIMIT 50")
    suspend fun recent(): List<ScanResultEntity>
}

@Dao
interface FindingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(e: FindingEntity)

    @Query("SELECT * FROM findings WHERE scanId = :scanId")
    suspend fun forScan(scanId: String): List<FindingEntity>
}

@Dao
interface QuarantineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(e: QuarantineEntity)

    @Query("SELECT * FROM quarantine ORDER BY createdAt DESC")
    suspend fun all(): List<QuarantineEntity>

    @Query("DELETE FROM quarantine WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface TimelineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(e: TimelineEntity)

    @Query("SELECT * FROM timeline ORDER BY ts DESC LIMIT 200")
    suspend fun recent(): List<TimelineEntity>
}

@Dao
interface SettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(e: SettingEntity)

    @Query("SELECT value FROM settings WHERE key = :key")
    suspend fun get(key: String): String?
}