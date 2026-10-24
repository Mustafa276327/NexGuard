package com.nexguard.feature.scan.detection

import com.nexguard.core.common.Finding
import com.nexguard.core.common.Severity
import kotlin.math.log2

/**
 * Windows PE (exe/dll) başlıklarını, bölümlerini ve içe aktarılan API adlarını
 * inceleyen hafif statik analiz. Kod çalıştırmaz.
 */
class PeAnalyzer : Detector {
    override val name = "pe"
    override val priority = 70

    private val peExtensions = setOf("exe", "dll", "sys", "scr", "ocx", "cpl", "drv")
    private val packedNames = listOf("upx0", "upx1", "upx2", ".aspack", ".adata", ".themida", ".vmp0", ".vmp1", ".enigma", ".petite", ".mpress")
    private val suspiciousApis = listOf(
        "VirtualAllocEx", "WriteProcessMemory", "CreateRemoteThread", "SetWindowsHookEx",
        "URLDownloadToFile", "InternetOpenUrl", "WinExec", "ShellExecute", "CreateProcess",
        "RegSetValueEx", "CryptEncrypt", "IsDebuggerPresent", "GetProcAddress", "LoadLibrary",
        "AdjustTokenPrivileges", "NtUnmapViewOfSection", "QueueUserAPC"
    )

    override suspend fun inspect(target: ScanTarget): List<Finding> {
        if (target.extension !in peExtensions) return emptyList()
        val bytes = target.sample()
        val out = mutableListOf<Finding>()

        if (bytes.size < 64 || bytes[0] != 0x4D.toByte() || bytes[1] != 0x5A.toByte()) {
            out += Finding(
                detector = name, ruleId = "PE_INVALID", severity = Severity.MEDIUM, weight = 30,
                evidence = mapOf("reason" to "MZ başlığı yok")
            )
            return out
        }

        val peOffset = readInt(bytes, 0x3C)
        if (peOffset <= 0 || peOffset + 24 > bytes.size ||
            bytes[peOffset] != 0x50.toByte() || bytes[peOffset + 1] != 0x45.toByte()
        ) {
            out += Finding(
                detector = name, ruleId = "PE_INVALID", severity = Severity.MEDIUM, weight = 30,
                evidence = mapOf("reason" to "PE başlığı geçersiz")
            )
            return out
        }

        val sectionCount = readShort(bytes, peOffset + 6)
        val optionalSize = readShort(bytes, peOffset + 20)
        val sectionStart = peOffset + 24 + optionalSize
        val sectionNames = mutableListOf<String>()
        var rwx = false
        for (i in 0 until minOf(sectionCount, 96)) {
            val base = sectionStart + i * 40
            if (base + 40 > bytes.size) break
            val nameBytes = bytes.copyOfRange(base, base + 8)
            val sectionName = String(nameBytes, Charsets.ISO_8859_1).trimEnd('\u0000').trim()
            if (sectionName.isNotEmpty()) sectionNames += sectionName
            val chars = readInt(bytes, base + 36)
            val executable = (chars and 0x20000000) != 0
            val writable = (chars and 0x80000000.toInt()) != 0
            if (executable && writable) rwx = true
        }

        val lowerSections = sectionNames.map { it.lowercase() }
        val packer = packedNames.firstOrNull { p -> lowerSections.any { it == p || it.startsWith(p) } }
        if (packer != null) {
            out += Finding(
                detector = name, ruleId = "PE_PACKED", severity = Severity.HIGH, weight = 50,
                evidence = mapOf("packer" to packer, "sections" to sectionNames.take(8).joinToString(", "))
            )
        }
        if (rwx) {
            out += Finding(
                detector = name, ruleId = "PE_RWX_SECTION", severity = Severity.HIGH, weight = 45,
                evidence = mapOf("sections" to sectionNames.take(8).joinToString(", "))
            )
        }

        val text = String(bytes, Charsets.ISO_8859_1)
        val apis = suspiciousApis.filter { text.contains(it) }
        if (apis.isNotEmpty()) {
            out += Finding(
                detector = name,
                ruleId = "PE_SUSPICIOUS_IMPORTS",
                severity = if (apis.size >= 5) Severity.HIGH else Severity.MEDIUM,
                weight = minOf(65, 15 + apis.size * 8),
                evidence = mapOf("apis" to apis.take(10).joinToString(", "), "count" to apis.size.toString())
            )
        }

        if (bytes.size >= 8192) {
            val entropy = entropyOf(bytes)
            if (entropy >= 7.80) {
                out += Finding(
                    detector = name, ruleId = "PE_HIGH_ENTROPY", severity = Severity.MEDIUM, weight = 30,
                    evidence = mapOf("entropy" to "%.2f".format(entropy))
                )
            }
        }
        return out
    }

    private fun entropyOf(bytes: ByteArray): Double {
        val freq = IntArray(256)
        bytes.forEach { freq[it.toInt() and 0xFF]++ }
        var entropy = 0.0
        val n = bytes.size.toDouble()
        for (c in freq) {
            if (c == 0) continue
            val p = c / n
            entropy -= p * log2(p)
        }
        return entropy
    }

    private fun readInt(b: ByteArray, off: Int): Int =
        if (off + 4 <= b.size) {
            (b[off].toInt() and 0xFF) or
                ((b[off + 1].toInt() and 0xFF) shl 8) or
                ((b[off + 2].toInt() and 0xFF) shl 16) or
                ((b[off + 3].toInt() and 0xFF) shl 24)
        } else 0

    private fun readShort(b: ByteArray, off: Int): Int =
        if (off + 2 <= b.size) (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8) else 0
}
