package com.nexguard.feature.scan.detection

import com.nexguard.core.common.Finding
import com.nexguard.core.common.Severity
import java.util.zip.ZipFile

/**
 * PDF, Office, RTF, HTML/SVG ve metin belgelerinde makro, gömülü nesne,
 * otomatik eylem ve polyglot (çoklu format) izlerini arar.
 */
class DocumentAnalyzer : Detector {
    override val name = "document"
    override val priority = 60

    private val magicZip = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val magicPdf = byteArrayOf(0x25, 0x50, 0x44, 0x46)
    private val magicOle = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte())
    private val magicElf = byteArrayOf(0x7F, 0x45, 0x4C, 0x46)
    private val magicMz = byteArrayOf(0x4D, 0x5A)

    override suspend fun inspect(target: ScanTarget): List<Finding> {
        val ext = target.extension
        val isDocument = ext in ScanPolicy.documentExtensions
        val isMedia = ext in ScanPolicy.mediaExtensions
        if (!isDocument && !isMedia) return emptyList()

        val bytes = target.sample()
        if (bytes.isEmpty()) return emptyList()
        val out = mutableListOf<Finding>()

        // Belge/medya gibi görünen çalıştırılabilir dosya
        if (startsWith(bytes, magicMz)) {
            out += Finding(
                detector = name, ruleId = "EXECUTABLE_DISGUISED", severity = Severity.CRITICAL, weight = 85,
                evidence = mapOf("extension" to ext, "magic" to "MZ")
            )
            return out
        }
        if (startsWith(bytes, magicElf)) {
            out += Finding(
                detector = name, ruleId = "EXECUTABLE_DISGUISED", severity = Severity.CRITICAL, weight = 85,
                evidence = mapOf("extension" to ext, "magic" to "ELF")
            )
            return out
        }

        // Polyglot: birincil format + ilk 1 KB içinde ikinci bir format imzası
        val primary = when {
            startsWith(bytes, magicZip) -> magicZip
            startsWith(bytes, magicPdf) -> magicPdf
            startsWith(bytes, magicOle) -> magicOle
            else -> null
        }
        // Medya dosyalarında sıkıştırma verisi içinde rastgele imza benzerliği
        // sık görülür; polyglot kontrolü yalnızca belgeler için yapılır.
        if (primary != null && isDocument) {
            val window = bytes.copyOf(minOf(bytes.size, 1024))
            val secondary = listOf(magicZip, magicPdf, magicOle, magicElf, magicMz)
                .filterNot { it.contentEquals(primary) }
                .any { indexOfSeq(window, it) > 0 }
            if (secondary) {
                out += Finding(
                    detector = name, ruleId = "POLYGLOT", severity = Severity.HIGH, weight = 55,
                    evidence = mapOf("extension" to ext, "note" to "birden fazla format imzası")
                )
            }
        }

        when (ext) {
            "pdf" -> out += inspectPdf(bytes)
            "doc", "xls", "ppt" -> out += inspectLegacyOffice(bytes)
            "docm", "xlsm", "pptm" -> out += inspectOoxml(target.file, macroExpected = true)
            "docx", "xlsx", "pptx" -> out += inspectOoxml(target.file, macroExpected = false)
            "rtf" -> out += inspectRtf(bytes)
            "html", "htm", "xhtml", "svg" -> out += inspectHtml(bytes)
        }
        return out
    }

    private fun inspectPdf(bytes: ByteArray): List<Finding> {
        val text = String(bytes, Charsets.ISO_8859_1)
        val out = mutableListOf<Finding>()
        if (Regex("""/JavaScript\b""").containsMatchIn(text) || Regex("""/JS\b""").containsMatchIn(text)) {
            out += Finding(
                detector = name, ruleId = "PDF_JS", severity = Severity.HIGH, weight = 50,
                evidence = mapOf("marker" to "/JavaScript")
            )
        }
        if (Regex("""/OpenAction\b""").containsMatchIn(text) || Regex("""/AA\b""").containsMatchIn(text)) {
            out += Finding(detector = name, ruleId = "PDF_AUTO_ACTION", severity = Severity.MEDIUM, weight = 30)
        }
        if (Regex("""/EmbeddedFile\b""").containsMatchIn(text)) {
            out += Finding(detector = name, ruleId = "PDF_EMBEDDED_FILE", severity = Severity.MEDIUM, weight = 25)
        }
        if (Regex("""/Launch\b""").containsMatchIn(text)) {
            out += Finding(detector = name, ruleId = "PDF_LAUNCH", severity = Severity.HIGH, weight = 45)
        }
        return out
    }

    private fun inspectLegacyOffice(bytes: ByteArray): List<Finding> {
        val text = String(bytes, Charsets.ISO_8859_1)
        val out = mutableListOf<Finding>()
        val hasVba = text.contains("VBA") || text.contains("Macros") || text.contains("vbaProject")
        if (hasVba) out += Finding(detector = name, ruleId = "OFFICE_MACRO", severity = Severity.HIGH, weight = 55)
        if (text.contains("AutoOpen") || text.contains("Document_Open") || text.contains("Auto_Open")) {
            out += Finding(detector = name, ruleId = "AUTO_MACRO", severity = Severity.HIGH, weight = 60)
        }
        return out
    }

    private fun inspectOoxml(file: java.io.File, macroExpected: Boolean): List<Finding> {
        val out = mutableListOf<Finding>()
        try {
            ZipFile(file).use { zf ->
                val entries = zf.entries().toList()
                if (entries.any { it.name.endsWith("vbaProject.bin", ignoreCase = true) }) {
                    out += Finding(
                        detector = name, ruleId = "OOXML_MACRO",
                        severity = if (macroExpected) Severity.HIGH else Severity.HIGH,
                        weight = 55,
                        evidence = mapOf("entry" to "vbaProject.bin")
                    )
                }
                val rels = entries.firstOrNull { it.name.endsWith(".rels") && it.size in 1..2_000_000 }
                if (rels != null) {
                    val text = zf.getInputStream(rels).use { it.readBytes() }.toString(Charsets.UTF_8)
                    if (text.contains("TargetMode=\"External\"", ignoreCase = true)) {
                        out += Finding(
                            detector = name, ruleId = "OOXML_EXTERNAL", severity = Severity.MEDIUM, weight = 30,
                            evidence = mapOf("entry" to rels.name)
                        )
                    }
                }
            }
        } catch (e: Exception) {
            out += Finding(detector = name, ruleId = "OOXML_PARSE_ERROR", severity = Severity.LOW, weight = 5)
        }
        return out
    }

    private fun inspectRtf(bytes: ByteArray): List<Finding> {
        val text = String(bytes, Charsets.ISO_8859_1)
        val out = mutableListOf<Finding>()
        if (text.contains("\\objdata") || text.contains("\\objupdate")) {
            out += Finding(detector = name, ruleId = "RTF_OBJECT", severity = Severity.MEDIUM, weight = 30)
        }
        return out
    }

    private fun inspectHtml(bytes: ByteArray): List<Finding> {
        val text = String(bytes, Charsets.ISO_8859_1).lowercase()
        val out = mutableListOf<Finding>()
        val scriptHits = listOf("<script", "javascript:", "onerror=", "onload=", "<iframe", "document.write", "eval(", "atob(")
            .count { text.contains(it) }
        if (scriptHits > 0) {
            out += Finding(
                detector = name, ruleId = "HTML_SCRIPT", severity = Severity.MEDIUM, weight = 25,
                evidence = mapOf("signals" to scriptHits.toString())
            )
        }
        val encoded = Regex("""(%u[0-9a-f]{4}){8,}|(\\x[0-9a-f]{2}){20,}|[A-Za-z0-9+/]{200,}={0,2}""", RegexOption.IGNORE_CASE)
        if (encoded.containsMatchIn(text) || scriptHits >= 4) {
            out += Finding(detector = name, ruleId = "HTML_OBFUSCATED", severity = Severity.HIGH, weight = 40)
        }
        return out
    }

    private fun startsWith(haystack: ByteArray, needle: ByteArray): Boolean {
        if (haystack.size < needle.size) return false
        for (i in needle.indices) if (haystack[i] != needle[i]) return false
        return true
    }

    private fun indexOfSeq(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty() || haystack.size < needle.size) return -1
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }
}
