package com.nexguard.feature.scan.detection

import android.content.Context
import com.nexguard.core.common.Finding
import com.nexguard.core.common.Severity

/**
 * Yerel hash imza veritabanı. Şu an EICAR test imzası ve assets/known_hashes.txt
 * içindeki kayıtlar yüklenir. Ağdan güncelleme ileride imzalı veri paketiyle eklenebilir.
 */
class HashDatabase(private val badHashes: Set<String> = emptySet()) {
    private val bloom = BloomFilter()

    init {
        badHashes.forEach { bloom.add(it.lowercase()) }
    }

    val size: Int get() = badHashes.size

    fun isBad(hash: String): Boolean {
        if (hash.isBlank()) return false
        val h = hash.lowercase()
        if (!bloom.mightContain(h)) return false
        return badHashes.contains(h)
    }

    companion object {
        /** EICAR standart antivirüs test dosyasının SHA-256 değeri (zararsız test). */
        const val EICAR_SHA256 = "275a021bbfb6489e54d471899f7db9d1663fc695ec2fe2a2c4538aabf651fd0f"

        fun load(context: Context): HashDatabase {
            val entries = linkedSetOf(EICAR_SHA256)
            try {
                context.assets.open("known_hashes.txt").bufferedReader().useLines { lines ->
                    lines.map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") }
                        .forEach { entries += it.lowercase() }
                }
            } catch (e: Exception) { /* asset yoksa yalnızca EICAR */ }
            return HashDatabase(entries)
        }
    }
}

class BloomFilter(private val size: Int = 1 shl 20, private val hashCount: Int = 7) {
    private val bits = java.util.BitSet(size)

    fun add(s: String) {
        for (i in 0 until hashCount) bits.set(index(s, i))
    }

    fun mightContain(s: String): Boolean {
        for (i in 0 until hashCount) if (!bits.get(index(s, i))) return false
        return true
    }

    private fun index(s: String, seed: Int): Int {
        var h = 0x811C9DC5.toInt() xor seed
        for (c in s) {
            h = h xor c.code
            h *= 0x01000193
        }
        return ((h and 0x7fffffff) % size)
    }
}

class HashDetector(private val db: HashDatabase) : Detector {
    override val name = "hash"
    override val priority = 95

    override suspend fun inspect(target: ScanTarget): List<Finding> {
        if (!db.isBad(target.sha256)) return emptyList()
        return listOf(
            Finding(
                detector = name,
                ruleId = "KNOWN_BAD_HASH",
                severity = Severity.CRITICAL,
                weight = 95,
                evidence = mapOf("sha256" to target.sha256.take(64))
            )
        )
    }
}
