package com.nexguard.feature.scan.detection

import com.nexguard.core.common.Finding
import com.nexguard.core.common.Severity
import java.io.File

object SimHash {
    fun compute(file: File, sampleSize: Int = 65536): Long {
        val bytes = try { file.readHead(sampleSize) } catch (e: Exception) { return 0L }
        if (bytes.isEmpty()) return 0L
        val v = LongArray(64)
        var i = 0
        while (i + 8 <= bytes.size) {
            var h = 0L
            for (k in 0 until 8) h = h * 31 + bytes[i + k]
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

class FuzzyHashDetector(
    private val knownBad: Set<Long> = emptySet(),
    private val threshold: Int = 8
) : Detector {
    override val name = "fuzzy"
    override val priority = 30

    override suspend fun inspect(target: ScanTarget): List<Finding> {
        if (knownBad.isEmpty()) return emptyList()
        if (target.extension !in ScanPolicy.executableExtensions && target.extension !in ScanPolicy.documentExtensions) {
            return emptyList()
        }
        val h = SimHash.compute(target.file, minOf(65536, target.size.toInt().coerceAtLeast(1)))
        for (kb in knownBad) {
            val distance = SimHash.hamming(h, kb)
            if (distance <= threshold) {
                return listOf(
                    Finding(
                        detector = name, ruleId = "FUZZY_SIMILAR", severity = Severity.MEDIUM, weight = 40,
                        evidence = mapOf("hamming" to distance.toString())
                    )
                )
            }
        }
        return emptyList()
    }
}
