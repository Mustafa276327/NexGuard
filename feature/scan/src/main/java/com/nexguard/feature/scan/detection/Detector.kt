package com.nexguard.feature.scan.detection

import com.nexguard.core.common.Finding

data class ScanTarget(
    val path: String,
    val file: java.io.File,
    val sha256: String,
    val size: Long,
    val mtime: Long,
    val magic: ByteArray = byteArrayOf(),
    val head: ByteArray = byteArrayOf()
)

/**
 * Dosyanın ilk [max] baytını okur.
 * NOT: `InputStream.readNBytes` yalnızca API 33+; minSdk 29 olduğundan elle döngü kullanılır.
 */
fun java.io.File.readHead(max: Int): ByteArray {
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

interface Detector {
    val name: String
    /** Higher = runs earlier. */
    val priority: Int get() = 0
    suspend fun inspect(target: ScanTarget): List<Finding>
}