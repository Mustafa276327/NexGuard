package com.nexguard.feature.scan.detection

import com.nexguard.core.common.Finding
import com.nexguard.core.common.Severity
import java.net.URI

class StringIocDetector : Detector {
    override val name = "ioc"
    override val priority = 40

    private val commandTokens = listOf(
        "cmd.exe", "powershell", "/bin/sh", "wget ", "curl ", "chmod +x",
        "base64 -d", "eval(", "atob(", "nc -e", "/dev/tcp/", "certutil -decode",
        "bitsadmin", "mshta ", "regsvr32 "
    )
    private val suspiciousTlds = setOf(
        ".ru", ".cn", ".tk", ".top", ".xyz", ".click", ".gq", ".ml", ".cf", ".ga",
        ".work", ".support", ".country", ".stream", ".download", ".loan"
    )
    private val urlRegex = Regex("""https?://[^\s"'<>\\]+""", RegexOption.IGNORE_CASE)

    /**
     * EICAR standart test imzası. Bilinçli olarak parçalı tutulur: derlenmiş DEX
     * içinde tam dize bulunmadığı için NexGuard kendi APK'sını "EICAR" diye
     * işaretlemez. Çalışma zamanında birleştirilir.
     */
    private val eicar: String = buildString {
        append("X5O!P%@AP[4\\PZX54(P^)")
        append("7CC)7}")
        append("\$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!")
        append("\$H+H*")
    }

    override suspend fun inspect(target: ScanTarget): List<Finding> {
        if (target.extension in ScanPolicy.mediaExtensions) return emptyList()

        // Önce küçük bir baş ile metin mi diye bak: ikili kaplarda (APK/ZIP/EXE)
        // 4 MB örnek okumak ve dev bir String üretmek gereksizdir.
        val head = target.file.readHead(4096)
        if (head.isEmpty()) return emptyList()
        if (!isTextLike(target, head)) return emptyList()

        val bytes = target.sample()
        if (bytes.isEmpty()) return emptyList()

        val text = String(bytes, Charsets.ISO_8859_1)
        val out = mutableListOf<Finding>()
        // Python betiklerinde komut tokenleri ve URL'ler geliştirme için sık
        // görülür. Bunlar tehdit değil, PythonAnalyzer ile birlikte açıklamalı
        // uyarı olarak raporlanır.
        val python = target.extension in ScanPolicy.pythonExtensions

        if (text.contains(eicar)) {
            out += Finding(
                detector = name, ruleId = "EICAR_TEST", severity = Severity.CRITICAL, weight = 90,
                evidence = mapOf("source" to "bilinen test imzası")
            )
        }

        if (!python) {
            val foundCommands = commandTokens.filter { text.contains(it, ignoreCase = true) }
            // Kaynak/betik dosyalarda tek bir token (ör. eval) sık görülür; en az iki
            // güçlü belirti veya betik dışı dosyalar için tek token yeterlidir.
            val commandThreshold = if (target.extension in ScanPolicy.scriptExtensions) 2 else 1
            if (foundCommands.size >= commandThreshold) {
                out += Finding(
                    detector = name,
                    ruleId = "SUSPICIOUS_COMMAND",
                    severity = if (foundCommands.size >= 3) Severity.MEDIUM else Severity.LOW,
                    weight = minOf(45, 12 + foundCommands.size * 8),
                    evidence = mapOf("tokens" to foundCommands.take(8).joinToString(", "), "count" to foundCommands.size.toString())
                )
            }
        }

        val urls = urlRegex.findAll(text).map { it.value.trimEnd('.', ',', ')', ';') }.toSet()
        val riskyHosts = mutableListOf<String>()
        var ipUrl = false
        urls.forEach { u ->
            val host = hostOf(u) ?: return@forEach
            if (suspiciousTlds.any { host.endsWith(it) }) riskyHosts += u.take(160)
            if (Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""").matches(host)) ipUrl = true
        }
        if (riskyHosts.isNotEmpty()) {
            val evidence = mapOf("count" to riskyHosts.size.toString(), "url" to riskyHosts.first())
            out += if (python) warning("SUSPICIOUS_TLD", 16, evidence)
            else Finding(
                detector = name,
                ruleId = "SUSPICIOUS_TLD",
                severity = Severity.MEDIUM,
                weight = minOf(35, 15 + riskyHosts.size * 5),
                evidence = evidence
            )
        }
        if (ipUrl) {
            val evidence = mapOf("note" to "IP adresli bağlantı")
            out += if (python) warning("IP_URL", 14, evidence)
            else Finding(
                detector = name, ruleId = "IP_URL", severity = Severity.MEDIUM, weight = 20,
                evidence = evidence
            )
        }
        if (urls.size > 30) {
            val evidence = mapOf("count" to urls.size.toString())
            out += if (python) warning("MANY_URLS", 12, evidence)
            else Finding(
                detector = name, ruleId = "MANY_URLS", severity = Severity.LOW, weight = 15,
                evidence = evidence
            )
        }
        return out
    }

    /** Metin tabanlı içerik mi? Betik/belge uzantıları her zaman; diğerleri bayt oranına göre. */
    private fun isTextLike(target: ScanTarget, bytes: ByteArray): Boolean {
        val ext = target.extension
        if (ext in ScanPolicy.scriptExtensions || ext in ScanPolicy.documentExtensions) return true
        return FileTypeSniffer.looksLikeText(bytes)
    }

    private fun warning(ruleId: String, weight: Int, evidence: Map<String, String>): Finding =
        Finding(
            detector = name,
            ruleId = ruleId,
            severity = Severity.LOW,
            weight = weight.coerceAtMost(20),
            evidence = evidence,
            warningOnly = true
        )

    private fun hostOf(url: String): String? = try {
        URI(url).host?.lowercase()
    } catch (e: Exception) {
        Regex("""^https?://([^/:?#]+)""", RegexOption.IGNORE_CASE).find(url)?.groupValues?.getOrNull(1)?.lowercase()
    }
}
