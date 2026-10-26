package com.nexguard.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ScanResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(e: ScanResultEntity)

    @Query("SELECT * FROM scan_results ORDER BY startedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ScanResultEntity>

    @Query("SELECT * FROM scan_results WHERE verdict != 'CLEAN' ORDER BY startedAt DESC LIMIT :limit")
    suspend fun threats(limit: Int): List<ScanResultEntity>

    @Query("SELECT * FROM scan_results WHERE id = :id")
    suspend fun get(id: String): ScanResultEntity?

    @Query("SELECT COUNT(*) FROM scan_results WHERE verdict != 'CLEAN'")
    suspend fun threatCount(): Int

    @Query("SELECT COUNT(*) FROM scan_results")
    suspend fun count(): Int

    @Query("DELETE FROM scan_results WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM scan_results")
    suspend fun clear()
}

@Dao
interface FindingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(e: FindingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<FindingEntity>)

    @Query("SELECT * FROM findings WHERE scanId = :scanId")
    suspend fun forScan(scanId: String): List<FindingEntity>

    @Query("DELETE FROM findings WHERE scanId = :scanId")
    suspend fun deleteForScan(scanId: String)

    @Query("DELETE FROM findings")
    suspend fun clear()
}

@Dao
interface QuarantineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(e: QuarantineEntity)

    @Query("SELECT * FROM quarantine ORDER BY createdAt DESC")
    suspend fun all(): List<QuarantineEntity>

    @Query("SELECT * FROM quarantine WHERE id = :id")
    suspend fun get(id: String): QuarantineEntity?

    @Query("DELETE FROM quarantine WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM quarantine")
    suspend fun clear()
}

@Dao
interface TimelineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(e: TimelineEntity)

    @Query("SELECT * FROM timeline ORDER BY ts DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<TimelineEntity>

    @Query("DELETE FROM timeline")
    suspend fun clear()
}

@Dao
interface SettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(e: SettingEntity)

    @Query("SELECT value FROM settings WHERE key = :key")
    suspend fun get(key: String): String?
}

@Dao
interface FileCacheDao {
    @Query("SELECT * FROM file_cache WHERE targetPath = :path")
    suspend fun get(path: String): FileCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<FileCacheEntity>)

    @Query("SELECT COUNT(*) FROM file_cache")
    suspend fun count(): Int

    @Query("DELETE FROM file_cache")
    suspend fun clear()
}

@Dao
interface ScanSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(e: ScanSessionEntity)

    @Query(
        "UPDATE scan_sessions SET finishedAt = :finishedAt, filesScanned = :files, " +
            "threats = :threats, warnings = :warnings, cachedFiles = :cached, errors = :errors, " +
            "status = :status, score = :score WHERE id = :id"
    )
    suspend fun finish(
        id: String,
        finishedAt: Long,
        files: Int,
        threats: Int,
        warnings: Int,
        cached: Int,
        errors: Int,
        status: String,
        score: Int
    )

    @Query("SELECT * FROM scan_sessions ORDER BY startedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ScanSessionEntity>

    @Query("SELECT * FROM scan_sessions ORDER BY startedAt DESC LIMIT 1")
    suspend fun latest(): ScanSessionEntity?

    @Query("DELETE FROM scan_sessions")
    suspend fun clear()
}
