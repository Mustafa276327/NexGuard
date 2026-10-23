package com.nexguard.feature.scan.detection

import com.nexguard.core.common.Finding
import com.nexguard.core.common.Severity
import java.io.File

object SimHash {
    fun compute(file: File, sampleSize: Int = 65536): Long {
        val bytes = try { file.inputStream().use { it.readNBytes(sampleSize) } } catch (e: Exception) { return 0L }
        val v = LongArray(64)
        var i = 0
        while (i + 8 <= bytes.size) {
            val chunk = bytes.copyOfRange(i, i + 8)
            var h = 0L
            for (b in chunk) h = h * 31 + b
            for (k in 0 until 64) {
                if ((h shr k) and 1L == 1L) v[k]++ else v[k]--
            }
            i += 8
        }
        var result = 0L
        for (k in 0 until 64) if (v[k] > 0) result = result or (1L shl k)
        return result
    }
    fun hamming(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)
}

class FuzzyHashDetector(private val knownBad: Set<Long> = emptySet(), private val threshold: Int = 8) : Detector {
    override val name = "fuzzy"
    override val priority = 40
    override suspend fun inspect(target: ScanTarget): List<Finding> {
        val h = SimHash.compute(target.file)
        for (kb in knownBad) {
            if (SimHash.hamming(h, kb) <= threshold) {
                return listOf(Finding(detector = name, ruleId = "FUZZY_SIMILAR", severity = Severity.MEDIUM, weight = 40, evidence = mapOf("hamming" to SimHash.hamming(h, kb).toString())))
            }
        }
        return emptyList()
    }
}