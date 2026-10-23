package com.nexguard.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_results")
data class ScanResultEntity(
    @PrimaryKey val id: String,
    val targetPath: String,
    val sha256: String,
    val verdict: String,
    val score: Int,
    val startedAt: Long,
    val durationMs: Long
)

@Entity(tableName = "findings")
data class FindingEntity(
    @PrimaryKey val id: String,
    val scanId: String,
    val detector: String,
    val ruleId: String,
    val severity: String,
    val weight: Int,
    val evidenceJson: String
)

@Entity(tableName = "quarantine")
data class QuarantineEntity(
    @PrimaryKey val id: String,
    val originalPath: String,
    val storedName: String,
    val sha256: String,
    val reason: String,
    val createdAt: Long,
    val restoredAt: Long? = null
)

@Entity(tableName = "timeline")
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