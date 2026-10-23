package com.nexguard.feature.quarantine

import android.content.Context
import com.nexguard.core.database.AppDatabase
import com.nexguard.core.database.QuarantineEntity
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuarantineManager @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val db: AppDatabase
) {
    private val dir = File(context.filesDir, "quarantine").apply { mkdirs() }
    private val random = SecureRandom()

    suspend fun quarantine(originalPath: String, reason: String): String {
        val src = File(originalPath)
        val id = UUID.randomUUID().toString()
        val dst = File(dir, id)
        if (src.exists()) {
            copyFile(src, dst)
            secureDelete(src)
        }
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
        File(dir, e.storedName).let { secureDelete(it); it.delete() }
        db.quarantineDao().delete(id)
        return true
    }

    private fun copyFile(src: File, dst: File): Boolean = try {
        src.inputStream().use { ins -> dst.outputStream().use { outs -> ins.copyTo(outs) } }
        true
    } catch (e: Exception) { false }

    /** Dosyayı rastgele veriyle (geçiş 1) ve sıfırlarla (geçiş 2) üzerine yazar, sonra siler. */
    private fun secureDelete(f: File) {
        if (!f.exists()) return
        val len = f.length()
        val buf = ByteArray(8192)
        try {
            // Geçiş 1: rastgele baytlar
            FileOutputStream(f).use { fos ->
                var written = 0L
                while (written < len) {
                    val n = (minOf(buf.size.toLong(), len - written)).toInt()
                    random.nextBytes(buf)
                    fos.write(buf, 0, n)
                    written += n
                }
            }
            // Geçiş 2: sıfırlar
            for (i in buf.indices) buf[i] = 0
            FileOutputStream(f).use { fos ->
                var written = 0L
                while (written < len) {
                    val n = (minOf(buf.size.toLong(), len - written)).toInt()
                    fos.write(buf, 0, n)
                    written += n
                }
            }
        } catch (e: Exception) { /* best effort */ } finally {
            for (i in buf.indices) buf[i] = 0
            f.delete()
        }
    }

    private fun sha256(f: File): String = try {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        f.inputStream().use { ins -> val b = ByteArray(8192); var n: Int; while (ins.read(b).also { n = it } > 0) md.update(b, 0, n) }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) { "" }
}