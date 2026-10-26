package com.nexguard.feature.scan.detection

import com.nexguard.core.common.Finding
import com.nexguard.core.common.Severity

class PrefilterDetector : Detector {
    override val name = "prefilter"
    override val priority = 100

    private val riskyExt = "(exe|apk|bat|cmd|scr|js|vbs|ps1|hta|jar|msi|com|pif)"
    private val doubleExt = Regex(
        """\.(jpg|jpeg|png|gif|pdf|doc|docx|xls|xlsx|ppt|pptx|txt|rtf|zip|rar|mp3|mp4)\.$riskyExt$""",
        RegexOption.IGNORE_CASE
    )
    private val hiddenExt = Regex("""\.$riskyExt\.""", RegexOption.IGNORE_CASE)
    private val rtlo = '\u202E'
    private val suspiciousNames = listOf(
        "e-devlet", "edevlet", "kargo", "banka", "whatsapp", "telegram", "instagram",
        "guncelle", "güncelle", "update", "flash", "player", "kripto", "borsa",
        "yatirim", "fatura", "iade", "ptt", "aras", "yurtici", "migros", "trendyol",
        "hepsiburada", "sahibinden", "bedava", "hediye"
    )

    override suspend fun inspect(target: ScanTarget): List<Finding> {
        val out = mutableListOf<Finding>()
        val rawName = target.file.name
        val lowerName = rawName.lowercase()
        val ext = target.extension

        if (rawName.contains(rtlo)) {
            out += Finding(
                detector = this.name, ruleId = "RTLO", severity = Severity.HIGH, weight = 60,
                evidence = mapOf("file" to rawName)
            )
        }
        if (doubleExt.containsMatchIn(lowerName)) {
            out += Finding(
                detector = this.name, ruleId = "DOUBLE_EXT", severity = Severity.HIGH, weight = 55,
                evidence = mapOf("file" to rawName)
            )
        }
        if (hiddenExt.containsMatchIn(lowerName)) {
            out += Finding(
                detector = this.name, ruleId = "HIDDEN_EXTENSION", severity = Severity.HIGH, weight = 50,
                evidence = mapOf("file" to rawName)
            )
        }
        if (rawName.endsWith(" ") || Regex("""\s+\.$riskyExt$""", RegexOption.IGNORE_CASE).containsMatchIn(lowerName)) {
            out += Finding(
                detector = this.name, ruleId = "TRAILING_SPACES", severity = Severity.HIGH, weight = 45,
                evidence = mapOf("file" to rawName)
            )
        }
        if (suspiciousNames.any { lowerName.contains(it) } && ext in setOf("apk", "exe", "msi", "scr")) {
            out += Finding(
                detector = this.name, ruleId = "FAKE_NAME", severity = Severity.MEDIUM, weight = 25,
                evidence = mapOf("file" to rawName)
            )
        }
        if (rawName.any { it in '\u0400'..'\u04FF' || it in '\u0370'..'\u03FF' }) {
            out += Finding(
                detector = this.name, ruleId = "HOMOGLYPH_NAME", severity = Severity.HIGH, weight = 45,
                evidence = mapOf("file" to rawName)
            )
        }

        // Uzantı ile gerçek içerik uyuşmazlığı. Kural: gerçek bir görsel/video,
        // uzantısı yanlış olsa bile tehdit sayılmaz. Yalnızca çalıştırılabilir
        // veya arşiv içeriği başka bir tür kılığındaysa risk bildirilir.
        val expected = FileTypeSniffer.expectedForExtension(ext)
        val content = FileTypeSniffer.sniff(target.file.readHead(4096))
        if (expected != null && content != ContentType.UNKNOWN && content != expected) {
            val evidence = mapOf(
                "extension" to ext,
                "expected" to expected.name.lowercase(),
                "actual" to content.name.lowercase()
            )
            when {
                // .jpg/.pdf kılığındaki .exe/.elf → DocumentAnalyzer da işaretler;
                // burada yalnızca o kapsamın dışındaki uzantılar ele alınır.
                content.isExecutable &&
                    ext !in ScanPolicy.documentExtensions &&
                    ext !in ScanPolicy.mediaExtensions ->
                    out += Finding(
                        detector = name, ruleId = "EXECUTABLE_DISGUISED",
                        severity = Severity.CRITICAL, weight = 85, evidence = evidence
                    )

                content.isArchive && !expected.isArchive ->
                    out += Finding(
                        detector = name, ruleId = "ARCHIVE_DISGUISED",
                        severity = Severity.HIGH, weight = 50, evidence = evidence
                    )

                // Yanlış etiketlenmiş medya (ör. WebP/HEIC dosyası .jpg adıyla):
                // tehdit değil, sessizce kabul et.
                content.isMedia && expected.isMedia -> Unit

                // Tek başına hüküm vermeye yetmeyen zayıf sinyal (eşik 30).
                else -> out += Finding(
                    detector = name, ruleId = "EXT_MAGIC_MISMATCH",
                    severity = Severity.LOW, weight = 22, evidence = evidence
                )
            }
        }
        return out
    }
}
