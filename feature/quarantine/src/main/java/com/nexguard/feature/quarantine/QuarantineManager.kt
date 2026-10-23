package com.nexguard.feature.quarantine

import android.content.Context
import com.nexguard.core.database.AppDatabase
import com.nexguard.core.database.QuarantineEntity
import java.io.File
import java.util.UUID

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuarantineManager @Inject constructor(private val context: Context, private val db: AppDatabase) {
    private val dir = File(context.filesDir, "quarantine").apply { mkdirs() }

    suspend fun quarantine(originalPath: String, reason: String): String {
        val src = File(originalPath)
        val id = UUID.randomUUID().toString()
        val dst = File(dir, id)
        if (src.exists()) copyFile(src, dst) && src.delete()
        db.quarantineDao().insert(QuarantineEntity(id, originalPath, dst.name, sha256(dst), reason, System.currentTimeMillis()))
        return id
    }

    suspend fun restore(id: String): Boolean {
        val e = db.quarantineDao().all().firstOrNull { it.id == id } ?: return false
        val src = File(dir, e.storedName)
        val dst = File(e.originalPath)
        dst.parentFile?.mkdirs()
        val ok = copyFile(src, dst) && src.delete()
        if (ok) db.quarantineDao().delete(id)
        return ok
    }

    suspend fun delete(id: String): Boolean {
        val e = db.quarantineDao().all().firstOrNull { it.id == id } ?: return false
        File(dir, e.storedName).delete()
        db.quarantineDao().delete(id)
        return true
    }

    private fun copyFile(src: File, dst: File): Boolean = try {
        src.inputStream().use { ins -> dst.outputStream().use { outs -> ins.copyTo(outs) } }
        true
    } catch (e: Exception) { false }

    private fun sha256(f: File): String = try {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        f.inputStream().use { ins -> val b = ByteArray(8192); var n: Int; while (ins.read(b).also { n = it } > 0) md.update(b, 0, n) }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) { "" }
}