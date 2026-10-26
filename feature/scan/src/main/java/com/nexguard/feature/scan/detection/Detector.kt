package com.nexguard.feature.scan.detection

import com.nexguard.core.common.Finding

/**
 * Taranacak dosyanın paylaşılan görünümü. Tüm dedektörler aynı örnek baytları
 * kullanır; böylece dosya her dedektör için ayrı ayrı okunmaz.
 */
class ScanTarget(
    val path: String,
    val file: java.io.File,
    val sha256: String,
    val size: Long,
    val mtime: Long,
    val magic: ByteArray = byteArrayOf(),
    private val sampleLimit: Int = 64 * 1024
) {
    @Volatile
    private var cachedSample: ByteArray? = null

    val extension: String get() = file.extension.lowercase()

    /** En fazla [sampleLimit] baytlık, tüm dedektörlerce paylaşılan örnek. */
    fun sample(): ByteArray {
        cachedSample?.let { return it }
        val bytes = file.readHead(sampleLimit)
        cachedSample = bytes
        return bytes
    }
}

/**
 * Dosyanın ilk [max] baytını okur.
 * NOT: `InputStream.readNBytes` yalnızca API 33+; minSdk 29 olduğundan elle döngü kullanılır.
 */
fun java.io.File.readHead(max: Int): ByteArray {
    if (max <= 0) return byteArrayOf()
    val buf = ByteArray(max)
    var off = 0
    try {
        inputStream().use { ins ->
            while (off < max) {
                val n = ins.read(buf, off, max - off)
                if (n < 0) break
                off += n
            }
        }
    } catch (e: Exception) { /* best effort */ }
    return if (off == max) buf else buf.copyOf(off)
}

/**
 * Dosyanın son [max] baytını okur. Görsel/medya dosyalarına sonradan eklenmiş
 * yükleri (polyglot) yakalamak için kullanılır.
 */
fun java.io.File.readTail(max: Int): ByteArray {
    if (max <= 0 || !isFile) return byteArrayOf()
    val len = length()
    if (len <= 0) return byteArrayOf()
    val start = (len - max).coerceAtLeast(0)
    return try {
        java.io.RandomAccessFile(this, "r").use { raf ->
            raf.seek(start)
            val buf = ByteArray((len - start).toInt())
            raf.readFully(buf)
            buf
        }
    } catch (e: Exception) {
        byteArrayOf()
    }
}

interface Detector {
    val name: String

    /** Yüksek = daha önce çalışır. */
    val priority: Int get() = 0

    /** Ağ erişimi kullanan dedektörler yalnızca kullanıcı izin verirse çalışır. */
    val network: Boolean get() = false

    suspend fun inspect(target: ScanTarget): List<Finding>
}

internal fun ByteArray.containsAscii(needle: String): Boolean =
    contains(needle.toByteArray(Charsets.ISO_8859_1))

internal fun ByteArray.contains(needle: ByteArray): Boolean {
    if (needle.isEmpty() || needle.size > size) return false
    outer@ for (i in 0..size - needle.size) {
        for (j in needle.indices) {
            if (this[i + j] != needle[j]) continue@outer
        }
        return true
    }
    return false
}
