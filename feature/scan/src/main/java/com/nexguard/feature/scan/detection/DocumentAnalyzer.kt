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
        val bytes = try { f.readHead(4 * 1024 * 1024) } catch (e: Exception) { return emptyList() }
        val text = String(bytes, Charsets.ISO_8859_1)
        // Polyglot: dosya birden çok magic'ı ilk 1 KB içinde barındırıyor mu?
        val magicSeqs = listOf(
            byteArrayOf(0x50, 0x4B, 0x03, 0x04), // zip
            byteArrayOf(0x25, 0x50, 0x44, 0x46), // pdf
            byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte()), // ole2
            byteArrayOf(0x7F, 0x45, 0x4C, 0x46)  // elf
        )
        val window = bytes.copyOf(minOf(bytes.size, 1024))
        val foundMagics = magicSeqs.count { indexOfSeq(window, it) >= 0 }
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

    /** [haystack] içinde [needle] dizisinin ilk indeksini bulur (-1 = yok). */
    private fun indexOfSeq(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty() || haystack.size < needle.size) return -1
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }
}