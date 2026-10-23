package com.nexguard.feature.scan.detection

import com.nexguard.core.common.Finding
import com.nexguard.core.common.Severity
import kotlin.math.log2

class EntropyDetector : Detector {
    override val name = "entropy"
    override val priority = 55
    override suspend fun inspect(target: ScanTarget): List<Finding> {
        val bytes = try { target.file.readHead(256 * 1024) } catch (e: Exception) { return emptyList() }
        if (bytes.isEmpty()) return emptyList()
        val freq = IntArray(256)
        bytes.forEach { freq[it.toInt() and 0xFF]++ }
        var entropy = 0.0
        val n = bytes.size.toDouble()
        for (c in freq) {
            if (c == 0) continue
            val p = c / n
            entropy -= p * log2(p)
        }
        val out = mutableListOf<Finding>()
        if (entropy > 7.8) out += Finding(detector = name, ruleId = "HIGH_ENTROPY", severity = Severity.MEDIUM, weight = 25, evidence = mapOf("entropy" to "%.2f".format(entropy)))
        // packer signatures in filename/content
        val packers = listOf("UPX", "MPRESS", "Themida", "VMProtect", "Enigma")
        val text = try { String(bytes, Charsets.ISO_8859_1) } catch (e: Exception) { "" }
        packers.forEach { p ->
            if (text.contains(p)) out += Finding(detector = name, ruleId = "PACKER_SIGNATURE", severity = Severity.MEDIUM, weight = 30, evidence = mapOf("packer" to p))
        }
        return out
    }
}