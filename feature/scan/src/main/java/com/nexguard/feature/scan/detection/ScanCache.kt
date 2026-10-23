package com.nexguard.feature.scan.detection

import com.nexguard.core.database.AppDatabase
import com.nexguard.core.database.ScanResultEntity

class ScanCache(private val db: AppDatabase) {
    suspend fun isUnchanged(path: String, size: Long, mtime: Long): Boolean {
        // Simple: check if any scan result exists with same path; (size/mtime stored in future schema)
        val recent = db.scanResultDao().recent()
        return recent.any { it.targetPath == path }
    }
}