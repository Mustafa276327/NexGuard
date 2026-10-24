package com.nexguard.feature.scan.detection

import com.nexguard.core.database.AppDatabase
import com.nexguard.core.database.FileCacheEntity

/**
 * (yol + boyut + mtime + motor sürümü) aynıysa ve önceki hüküm TEMİZ ise
 * dosyayı yeniden okumadan sonucu döndürür. Tehditler her zaman yeniden taranır.
 */
class ScanCache(private val db: AppDatabase) {

    suspend fun cleanCached(path: String, size: Long, mtime: Long): FileCacheEntity? {
        val e = try { db.fileCacheDao().get(path) } catch (ex: Exception) { null } ?: return null
        if (e.sizeBytes != size || e.mtime != mtime) return null
        if (e.engineVersion != ScanPolicy.ENGINE_VERSION) return null
        if (e.verdict != "CLEAN") return null
        return e
    }

    suspend fun put(entity: FileCacheEntity) {
        try { db.fileCacheDao().upsertAll(listOf(entity)) } catch (e: Exception) { /* önbellek kritik değil */ }
    }

    suspend fun putAll(entities: List<FileCacheEntity>) {
        if (entities.isEmpty()) return
        try { db.fileCacheDao().upsertAll(entities) } catch (e: Exception) { /* önbellek kritik değil */ }
    }

    suspend fun count(): Int = try { db.fileCacheDao().count() } catch (e: Exception) { 0 }

    suspend fun clear() {
        try { db.fileCacheDao().clear() } catch (e: Exception) { }
    }
}
