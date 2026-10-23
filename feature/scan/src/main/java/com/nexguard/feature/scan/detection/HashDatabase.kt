package com.nexguard.feature.scan.detection

import com.nexguard.core.common.Finding
import com.nexguard.core.common.Severity
import java.util.BitSet

class BloomFilter(private val size: Int = 1 shl 20, private val hashes: Int = 7) {
    private val bits = BitSet(size)
    fun add(s: String) { for (i in 0 until hashes) bits.set((s.hashCode() + i * 31).floorMod(size)) }
    fun mightContain(s: String): Boolean { for (i in 0 until hashes) if (!bits.get((s.hashCode() + i * 31).floorMod(size))) return false; return true }
    private fun Int.floorMod(m: Int): Int = ((this % m) + m) % m
}

class HashDatabase(private val badHashes: Set<String> = emptySet()) {
    private val bloom = BloomFilter()
    init { badHashes.forEach { bloom.add(it.lowercase()) } }

    fun isBad(hash: String): Boolean {
        val h = hash.lowercase()
        if (!bloom.mightContain(h)) return false
        return badHashes.contains(h)
    }
}

class HashDetector(private val db: HashDatabase) : Detector {
    override val name = "hash"
    override val priority = 90
    override suspend fun inspect(target: ScanTarget): List<Finding> {
        if (db.isBad(target.sha256)) return listOf(Finding(detector = name, ruleId = "KNOWN_BAD_HASH", severity = Severity.CRITICAL, weight = 95, evidence = mapOf("sha256" to target.sha256)))
        return emptyList()
    }
}