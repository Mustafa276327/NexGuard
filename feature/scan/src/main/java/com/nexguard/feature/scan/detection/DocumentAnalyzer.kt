package com.nexguard.feature.scan.detection

import com.nexguard.core.common.Finding
import com.nexguard.core.common.Severity
import java.io.File

class DocumentAnalyzer : Detector {
    override val name = "document"
    override val priority = 50

    override suspend fun inspect(target: ScanTarget): List<Finding> {
        val out = mutableListOf<Finding>()
        val f = target.file
        val ext = f.extension.lowercase()
        val bytes = try { f.inputStream().use { it.readNBytes(4 * 1024 * 1024) } } catch (e: Exception) { return emptyList() }
        val text = String(bytes, Charsets.ISO_8859_1)
        // Polyglot: file starts as one type but contains another magic
        val magics = listOf(0x504B0304, 0x25504446, 0xD0CF11E0.toInt(), 0x7F454C46)
        val foundMagics = magics.count { m ->
            val b = (m shr 24 and 0xFF).toByte()
            // search first 1KB
            bytes.take(1024).any { it == b }
        }
        if (foundMagics >= 2) out += Finding(detector = name, ruleId = "POLYGLOT", severity = Severity.HIGH, weight = 60)
        when (ext) {
            "pdf" -> {
                if (text.contains("/JavaScript") || text.contains("/JS")) out += Finding(detector = name, ruleId = "PDF_JS", severity = Severity.HIGH, weight = 50)
                if (text.contains("/OpenAction") || text.contains("/AA")) out += Finding(detector = name, ruleId = "PDF_AUTO_ACTION", severity = Severity.MEDIUM, weight = 30)
                if (text.contains("/EmbeddedFile")) out += Finding(detector = name, ruleId = "PDF_EMBEDDED_FILE", severity = Severity.MEDIUM, weight = 25)
                if (text.contains("/Launch")) out += Finding(detector = name, ruleId = "PDF_LAUNCH", severity = Severity.HIGH, weight = 45)
            }
            "doc", "docm", "xls", "xlsm", "ppt", "pptm" -> {
                if (text.contains("VBA") || text.contains("Macros")) out += Finding(detector = name, ruleId = "OFFICE_MACRO", severity = Severity.HIGH, weight = 50)
                if (text.contains("AutoOpen") || text.contains("Document_Open")) out += Finding(detector = name, ruleId = "AUTO_MACRO", severity = Severity.HIGH, weight = 55)
            }
            "rtf" -> {
                if (text.contains("\\objdata") || text.contains("\\objupdate")) out += Finding(detector = name, ruleId = "RTF_OBJECT", severity = Severity.MEDIUM, weight = 30)
            }
        }
        // Generic: executable magic in document
        if (bytes.size >= 2 && bytes[0] == 0x4D.toByte() && bytes[1] == 0x5A.toByte() && ext in setOf("pdf","doc","rtf","txt")) {
            out += Finding(detector = name, ruleId = "EXECUTABLE_DISGUISED", severity = Severity.CRITICAL, weight = 80)
        }
        return out
    }
}