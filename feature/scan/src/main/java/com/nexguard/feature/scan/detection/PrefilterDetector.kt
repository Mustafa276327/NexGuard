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

        val magic = target.magic
        val mismatch = when (ext) {
            "jpg", "jpeg" -> !(magic.size >= 3 && magic[0] == 0xFF.toByte() && magic[1] == 0xD8.toByte())
            "png" -> !(magic.size >= 4 && magic[0] == 0x89.toByte() && magic[1] == 0x50.toByte())
            "gif" -> !(magic.size >= 4 && magic[0] == 0x47.toByte() && magic[1] == 0x49.toByte())
            "webp" -> !(magic.size >= 4 && magic[0] == 0x52.toByte() && magic[1] == 0x49.toByte())
            "pdf" -> !(magic.size >= 4 && magic[0] == 0x25.toByte() && magic[1] == 0x50.toByte())
            "zip", "apk", "jar", "docx", "xlsx", "pptx" ->
                !(magic.size >= 4 && magic[0] == 0x50.toByte() && magic[1] == 0x4B.toByte())
            "rar" -> !(magic.size >= 4 && magic[0] == 0x52.toByte() && magic[1] == 0x61.toByte())
            "7z" -> !(magic.size >= 4 && magic[0] == 0x37.toByte() && magic[1] == 0x7A.toByte())
            "exe", "dll", "scr" -> !(magic.size >= 2 && magic[0] == 0x4D.toByte() && magic[1] == 0x5A.toByte())
            "elf", "so" -> !(magic.size >= 4 && magic[0] == 0x7F.toByte() && magic[1] == 0x45.toByte())
            else -> false
        }
        if (mismatch) {
            out += Finding(
                detector = this.name, ruleId = "EXT_MAGIC_MISMATCH", severity = Severity.MEDIUM, weight = 45,
                evidence = mapOf("extension" to ext, "magic" to magic.joinToString("") { "%02x".format(it) })
            )
        }
        return out
    }
}
