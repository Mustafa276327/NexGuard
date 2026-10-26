package com.nexguard.feature.scan.detection

import com.nexguard.core.common.Finding
import com.nexguard.core.common.Severity
import kotlin.math.log2

class EntropyDetector : Detector {
    override val name = "entropy"
    override val priority = 50

    // Betik/kaynak dosyalarda yüksek entropi normal olabilir (minify/encode); yalnızca
    // ikili/çalıştırılabilir dosyalarda entropi kontrolü yapılır.
    // APK/JAR birer ZIP kabıdır ve içeriği zaten sıkıştırılmıştır; entropileri
    // doğal olarak ~8'dir. Bu yüzden APK/JAR entropi kontrolünden çıkarılır.
    private val allowed = setOf(
        "exe", "dll", "sys", "scr", "ocx", "cpl", "drv", "elf", "so", "bin",
        "", "dat", "tmp", "unknown"
    )

    override suspend fun inspect(target: ScanTarget): List<Finding> {
        // Sıkıştırılmış medya ve arşivler doğal olarak yüksek entropilidir; yanlış alarm üretme.
        if (target.extension !in allowed) return emptyList()
        val bytes = target.sample()
        if (bytes.size < 4096) return emptyList()

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
        if (entropy >= 7.60) {
            out += Finding(
                detector = name,
                ruleId = "HIGH_ENTROPY",
                severity = if (entropy >= 7.90) Severity.MEDIUM else Severity.LOW,
                weight = if (entropy >= 7.90) 30 else 18,
                evidence = mapOf("entropy" to "%.2f".format(entropy), "sample" to bytes.size.toString())
            )
        }

        val text = String(bytes, Charsets.ISO_8859_1)
        val packers = listOf("UPX!", "UPX0", "MPRESS1", "MPRESS2", "Themida", "VMProtect", "Enigma protector")
        packers.forEach { p ->
            if (text.contains(p)) {
                out += Finding(
                    detector = name,
                    ruleId = "PACKER_SIGNATURE",
                    severity = Severity.MEDIUM,
                    weight = 30,
                    evidence = mapOf("packer" to p)
                )
            }
        }
        return out
    }
}
