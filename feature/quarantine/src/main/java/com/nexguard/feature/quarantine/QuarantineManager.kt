package com.nexguard.feature.quarantine

import android.content.Context
import com.nexguard.core.database.AppDatabase
import com.nexguard.core.database.QuarantineEntity
import com.nexguard.core.security.KeystoreHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

data class QuarantineOutcome(
    val success: Boolean,
    val id: String? = null,
    val message: String = ""
) {
    /** Karantina kopyası oluştu ancak kaynak dosya silinemedi. */
    val sourceDeleteFailed: Boolean
        get() = success && message.contains("silinemedi", ignoreCase = true)
}

/**
 * Karantina dizini uygulamanın özel alanındadır. Dosyalar AES-256-GCM ile
 * parça parça şifrelenir (Android Keystore anahtarı), böylece büyük dosyalar
 * belleğe tamamen yüklenmez. Kaynak dosya ancak şifreli kopya doğrulandıktan
 * ve veritabanı kaydı yazıldıktan sonra silinir.
 */
@Singleton
class QuarantineManager @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext context: Context,
    private val db: AppDatabase
) {
    private val dir = File(context.filesDir, "quarantine").apply { mkdirs() }
    private val random = SecureRandom()

    suspend fun quarantine(
        originalPath: String,
        reason: String,
        threatType: String = ""
    ): QuarantineOutcome = withContext(Dispatchers.IO) {
        val src = File(originalPath)
        if (!src.isFile || !src.canRead()) {
            return@withContext QuarantineOutcome(false, message = "Dosya okunamadı veya erişim yok")
        }

        val id = UUID.randomUUID().toString()
        val dst = File(dir, "$id.ngq")
        val tmp = File(dir, "$id.part")
        try {
            val key = KeystoreHelper.getOrCreateSecretKey()
            val originalSize = src.length()
            val sha = encryptFile(src, tmp, key)
            if (src.length() != originalSize) {
                tmp.delete()
                return@withContext QuarantineOutcome(false, message = "Dosya işlem sırasında değişti; kaynak korundu")
            }
            if (!tmp.renameTo(dst)) {
                tmp.copyTo(dst, overwrite = true)
                tmp.delete()
            }
            if (!dst.isFile || dst.length() <= HEADER_SIZE) {
                dst.delete()
                return@withContext QuarantineOutcome(false, message = "Şifreli kopya oluşturulamadı")
            }

            try {
                db.quarantineDao().insert(
                    QuarantineEntity(
                        id = id,
                        originalPath = originalPath,
                        storedName = dst.name,
                        sha256 = sha,
                        reason = reason,
                        createdAt = System.currentTimeMillis(),
                        sizeBytes = originalSize,
                        threatType = threatType,
                        encrypted = true
                    )
                )
            } catch (e: Exception) {
                // Kayıt yazılamazsa yetim şifreli dosya bırakma.
                dst.delete()
                return@withContext QuarantineOutcome(false, message = "Karantina kaydı oluşturulamadı")
            }

            val deleted = secureDelete(src)
            QuarantineOutcome(
                true,
                id,
                if (deleted) "Dosya karantinaya alındı" else "Karantinaya alındı; kaynak dosya silinemedi"
            )
        } catch (e: Exception) {
            tmp.delete()
            dst.delete()
            QuarantineOutcome(false, message = "Karantinaya alınamadı: ${e.message ?: "bilinmeyen hata"}")
        }
    }

    suspend fun restore(id: String): QuarantineOutcome = withContext(Dispatchers.IO) {
        val e = try { db.quarantineDao().get(id) } catch (ex: Exception) { null }
            ?: return@withContext QuarantineOutcome(false, message = "Karantina kaydı bulunamadı")
        val src = File(dir, e.storedName)
        if (!src.isFile) {
            return@withContext QuarantineOutcome(false, message = "Karantina dosyası bulunamadı")
        }
        val target = File(e.originalPath)
        if (target.exists()) {
            return@withContext QuarantineOutcome(false, message = "Hedefte aynı adla bir dosya var")
        }
        val parent = target.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return@withContext QuarantineOutcome(false, message = "Hedef klasör oluşturulamadı")
        }
        val tmp = File(parent ?: dir, ".${target.name}.ngrestore-${UUID.randomUUID()}")
        try {
            val key = if (e.encrypted) KeystoreHelper.getOrCreateSecretKey() else null
            val sha = if (e.encrypted) decryptFile(src, tmp, key!!) else copyAndHash(src, tmp)
            if (e.sha256.isNotBlank() && sha.isNotBlank() && !sha.equals(e.sha256, ignoreCase = true)) {
                tmp.delete()
                return@withContext QuarantineOutcome(false, message = "Bütünlük doğrulaması başarısız; dosya karantinada kaldı")
            }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = false)
                tmp.delete()
            }
            src.delete()
            try {
                db.quarantineDao().delete(id)
            } catch (dbError: Exception) {
                // Dosya geri yüklendi; kayıt sonradan kullanıcı tarafından silinebilir.
            }
            QuarantineOutcome(true, id, "Dosya geri yüklendi")
        } catch (ex: Exception) {
            tmp.delete()
            QuarantineOutcome(false, message = "Geri yüklenemedi: ${ex.message ?: "bozuk kayıt"}")
        }
    }

    suspend fun delete(id: String): QuarantineOutcome = withContext(Dispatchers.IO) {
        val e = try { db.quarantineDao().get(id) } catch (ex: Exception) { null }
            ?: return@withContext QuarantineOutcome(false, message = "Karantina kaydı bulunamadı")
        val stored = File(dir, e.storedName)
        if (stored.exists() && !secureDelete(stored)) {
            return@withContext QuarantineOutcome(
                false,
                message = "Şifreli kopya silinemedi; kayıt korundu"
            )
        }
        return@withContext try {
            db.quarantineDao().delete(id)
            QuarantineOutcome(true, id, "Karantina kaydı silindi")
        } catch (ex: Exception) {
            QuarantineOutcome(false, message = "Kayıt silinemedi")
        }
    }

    /** Tarama ekranındaki "kalıcı sil" eylemi. */
    suspend fun deleteFile(path: String): Boolean = withContext(Dispatchers.IO) {
        val f = File(path)
        if (!f.isFile) return@withContext false
        secureDelete(f)
    }

    // ---- Şifreleme ----

    private fun encryptFile(src: File, dst: File, key: SecretKey): String {
        val md = MessageDigest.getInstance("SHA-256")
        BufferedInputStream(src.inputStream()).use { ins ->
            FileOutputStream(dst).use { fos ->
                val out = DataOutputStream(BufferedOutputStream(fos))
                out.write(MAGIC)
                out.writeInt(VERSION)
                out.writeLong(src.length())
                out.writeInt(CHUNK_SIZE)
                val buf = ByteArray(CHUNK_SIZE)
                while (true) {
                    val n = ins.read(buf)
                    if (n <= 0) break
                    md.update(buf, 0, n)
                    // Android Keystore AES/GCM anahtarlarında rastgele şifreleme
                    // varsayılan olarak zorunludur; IV keystore tarafından üretilir.
                    // Çağıranın sağladığı IV "caller-provided IV not permitted" hatası verir.
                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    cipher.init(Cipher.ENCRYPT_MODE, key)
                    val iv = cipher.iv
                    val ct = cipher.doFinal(buf, 0, n)
                    out.writeInt(iv.size)
                    out.writeInt(ct.size)
                    out.write(iv)
                    out.write(ct)
                }
                out.flush()
                fos.fd.sync()
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun decryptFile(src: File, dst: File, key: SecretKey): String {
        val md = MessageDigest.getInstance("SHA-256")
        DataInputStream(BufferedInputStream(src.inputStream())).use { ins ->
            val magic = ByteArray(MAGIC.size)
            ins.readFully(magic)
            if (!magic.contentEquals(MAGIC)) throw IllegalStateException("geçersiz karantina başlığı")
            val version = ins.readInt()
            if (version != VERSION) throw IllegalStateException("desteklenmeyen sürüm")
            val plainSize = ins.readLong()
            ins.readInt() // chunk size
            BufferedOutputStream(FileOutputStream(dst)).use { out ->
                var written = 0L
                while (written < plainSize) {
                    val ivLen = ins.readInt()
                    val ctLen = ins.readInt()
                    if (ivLen !in 1..32 || ctLen !in 1..CHUNK_SIZE + 32) {
                        throw IllegalStateException("bozuk parça")
                    }
                    val iv = ByteArray(ivLen).also { ins.readFully(it) }
                    val ct = ByteArray(ctLen).also { ins.readFully(it) }
                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                    val plain = cipher.doFinal(ct)
                    out.write(plain)
                    md.update(plain)
                    written += plain.size
                }
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun copyAndHash(src: File, dst: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        BufferedInputStream(src.inputStream()).use { ins ->
            BufferedOutputStream(FileOutputStream(dst)).use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = ins.read(buf)
                    if (n <= 0) break
                    md.update(buf, 0, n)
                    out.write(buf, 0, n)
                }
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Flash depolamada güvenli silme garantili değildir; yine de küçük dosyalar
     * için üzerine yazma denemesi yapılır. Büyük dosyalarda gereksiz yıpranmayı
     * önlemek için doğrudan silinir.
     */
    private fun secureDelete(f: File): Boolean {
        if (!f.exists()) return true
        val len = f.length()
        if (len in 1..(32L * 1024 * 1024)) {
            val buf = ByteArray(8192)
            try {
                FileOutputStream(f).use { fos ->
                    var written = 0L
                    while (written < len) {
                        val n = minOf(buf.size.toLong(), len - written).toInt()
                        random.nextBytes(buf)
                        fos.write(buf, 0, n)
                        written += n
                    }
                    fos.fd.sync()
                }
            } catch (e: Exception) { /* best effort */ }
        }
        return f.delete()
    }

    private companion object {
        val MAGIC = "NGQ1".toByteArray(Charsets.US_ASCII)
        const val VERSION = 1
        const val CHUNK_SIZE = 64 * 1024
        const val IV_SIZE = 12
        const val HEADER_SIZE = 4 + 4 + 8 + 4
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
