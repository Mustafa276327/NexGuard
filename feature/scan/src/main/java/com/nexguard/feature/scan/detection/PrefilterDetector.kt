package com.nexguard.feature.scan.detection

import com.nexguard.core.common.Finding
import com.nexguard.core.common.Severity

class PrefilterDetector : Detector {
    override val name = "prefilter"
    override val priority = 100

    private val doubleExt = Regex("""\.(jpg|png|pdf|doc|txt)\.(exe|apk|bat|cmd|scr|js|vbs)$""", RegexOption.IGNORE_CASE)
    private val rtlo = "\u202E"
    private val suspiciousNames = listOf("e-devlet", "kargo", "banka", "whatsapp", "update", "flash", "player")

    override suspend fun inspect(target: ScanTarget): List<Finding> {
        val out = mutableListOf<Finding>()
        val name = target.file.name.lowercase()
        if (target.file.name.contains(rtlo)) out += Finding(detector = this.name, ruleId = "RTLO", severity = Severity.HIGH, weight = 60, evidence = mapOf("file" to target.file.name))
        if (doubleExt.containsMatchIn(name)) out += Finding(detector = this.name, ruleId = "DOUBLE_EXT", severity = Severity.HIGH, weight = 50, evidence = mapOf("file" to target.file.name))
        if (suspiciousNames.any { name.contains(it) } && target.file.extension.lowercase() in setOf("apk", "exe")) {
            out += Finding(detector = this.name, ruleId = "FAKE_NAME", severity = Severity.MEDIUM, weight = 30, evidence = mapOf("file" to target.file.name))
        }
        // extension vs magic mismatch
        val ext = target.file.extension.lowercase()
        val magic = target.magic
        val mismatch = when (ext) {
            "jpg", "jpeg" -> !(magic.size >= 3 && magic[0] == 0xFF.toByte() && magic[1] == 0xD8.toByte())
            "png" -> !(magic.size >= 4 && magic[0] == 0x89.toByte() && magic[1] == 0x50.toByte())
            "pdf" -> !(magic.size >= 4 && magic[0] == 0x25.toByte() && magic[1] == 0x50.toByte())
            "zip", "apk", "jar" -> !(magic.size >= 4 && magic[0] == 0x50.toByte() && magic[1] == 0x4B.toByte())
            else -> false
        }
        if (mismatch && ext in setOf("jpg","jpeg","png","pdf","zip","apk","jar")) {
            out += Finding(detector = this.name, ruleId = "EXT_MAGIC_MISMATCH", severity = Severity.MEDIUM, weight = 40, evidence = mapOf("ext" to ext))
        }
        // RTLO in extension
        if (target.file.name.contains("\u202E")) out += Finding(detector = this.name, ruleId = "RTLO_EXT", severity = Severity.HIGH, weight = 50)
        return out
    }
}