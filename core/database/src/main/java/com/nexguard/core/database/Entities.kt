package com.nexguard.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scan_results",
    indices = [Index("startedAt"), Index("verdict")]
)
data class ScanResultEntity(
    @PrimaryKey val id: String,
    val targetPath: String,
    val sha256: String,
    val verdict: String,
    val score: Int,
    val startedAt: Long,
    val durationMs: Long,
    val sizeBytes: Long = 0,
    val threatType: String = "",
    val source: String = "manual"
)

@Entity(
    tableName = "findings",
    indices = [Index("scanId")]
)
data class FindingEntity(
    @PrimaryKey val id: String,
    val scanId: String,
    val detector: String,
    val ruleId: String,
    val severity: String,
    val weight: Int,
    val evidenceJson: String
)

@Entity(
    tableName = "quarantine",
    indices = [Index("createdAt")]
)
data class QuarantineEntity(
    @PrimaryKey val id: String,
    val originalPath: String,
    val storedName: String,
    val sha256: String,
    val reason: String,
    val createdAt: Long,
    val restoredAt: Long? = null,
    val sizeBytes: Long = 0,
    val threatType: String = "",
    val encrypted: Boolean = false
)

@Entity(
    tableName = "timeline",
    indices = [Index("ts")]
)
data class TimelineEntity(
    @PrimaryKey val id: String,
    val ts: Long,
    val source: String,
    val kind: String,
    val summary: String,
    val refId: String? = null
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String
)

/**
 * Değişmemiş dosyaları yeniden taramamak için (yol + boyut + mtime + motor sürümü)
 * anahtarlı önbellek. Yalnızca temiz dosyalar hızlı yoldan döndürülür; tehditler
 * güncel bulguları göstermek için her zaman yeniden taranır.
 */
@Entity(
    tableName = "file_cache",
    indices = [Index("updatedAt")]
)
data class FileCacheEntity(
    @PrimaryKey val targetPath: String,
    val sizeBytes: Long,
    val mtime: Long,
    val sha256: String,
    val verdict: String,
    val score: Int,
    val threatType: String,
    val engineVersion: Int,
    val updatedAt: Long
)

@Entity(
    tableName = "scan_sessions",
    indices = [Index("startedAt")]
)
data class ScanSessionEntity(
    @PrimaryKey val id: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val mode: String,
    val label: String,
    val filesScanned: Int,
    val threats: Int,
    val cachedFiles: Int,
    val errors: Int = 0,
    val status: String,
    val score: Int = 100
)
