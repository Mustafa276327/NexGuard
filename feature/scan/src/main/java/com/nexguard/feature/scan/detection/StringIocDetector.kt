package com.nexguard.feature.scan.detection

import com.nexguard.core.common.Finding
import com.nexguard.core.common.Severity
import java.io.File

class StringIocDetector : Detector {
    override val name = "ioc"
    override val priority = 45
    private val cmdTokens = listOf("cmd.exe", "powershell", "/bin/sh", "wget ", "curl ", "chmod ", "base64 -d", "eval(", "atob(")
    private val suspiciousTlds = setOf(".ru", ".cn", ".tk", ".top", ".xyz", ".click", ".gq", ".ml")
    private val urlRegex = Regex("""https?://[A-Za-z0-9.\-_/]+""")

    override suspend fun inspect(target: ScanTarget): List<Finding> {
        val out = mutableListOf<Finding>()
        val bytes = try { target.file.inputStream().use { it.readNBytes(1 * 1024 * 1024) } } catch (e: Exception) { return emptyList() }
        val text = String(bytes, Charsets.ISO_8859_1)
        cmdTokens.forEach { t ->
            if (text.contains(t, ignoreCase = true)) out += Finding(detector = name, ruleId = "SUSPICIOUS_COMMAND", severity = Severity.MEDIUM, weight = 20, evidence = mapOf("token" to t))
        }
        val urls = urlRegex.findAll(text).map { it.value }.toSet()
        urls.forEach { u ->
            if (suspiciousTlds.any { u.contains(it) }) out += Finding(detector = name, ruleId = "SUSPICIOUS_TLD", severity = Severity.MEDIUM, weight = 15, evidence = mapOf("url" to u))
        }
        if (urls.size > 20) out += Finding(detector = name, ruleId = "MANY_URLS", severity = Severity.LOW, weight = 10, evidence = mapOf("count" to urls.size.toString()))
        return out
    }
}