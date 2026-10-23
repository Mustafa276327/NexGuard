package com.nexguard.feature.scan.detection

import com.nexguard.core.common.Finding
import com.nexguard.core.common.Severity
import java.io.File
import java.util.zip.ZipFile

class ArchiveScanner(private val maxDepth: Int = 3, private val maxEntries: Int = 4096, private val maxTotalBytes: Long = 300L * 1024 * 1024) : Detector {
    override val name = "archive"
    override val priority = 70

    override suspend fun inspect(target: ScanTarget): List<Finding> {
        val ext = target.file.extension.lowercase()
        if (ext !in setOf("zip", "rar", "7z", "tar", "gz", "tgz")) return emptyList()
        val out = mutableListOf<Finding>()
        if (ext == "zip") out += scanZip(target.file, 0)
        else out += Finding(detector = name, ruleId = "ARCHIVE_TYPE", severity = Severity.INFO, weight = 0, evidence = mapOf("type" to ext, "note" to "deep scan limited"))
        return out
    }

    private fun scanZip(f: File, depth: Int): List<Finding> {
        if (depth > maxDepth) return listOf(Finding(detector = name, ruleId = "NESTED_TOO_DEEP", severity = Severity.MEDIUM, weight = 20))
        val out = mutableListOf<Finding>()
        var total = 0L
        var count = 0
        val tmpDir = File(System.getProperty("java.io.tmpdir"), "ng_zip_${f.name}")
        try {
            ZipFile(f).use { zf ->
                val entries = zf.entries().toList()
                if (entries.size > maxEntries) out += Finding(detector = name, ruleId = "ZIP_TOO_MANY_ENTRIES", severity = Severity.MEDIUM, weight = 20)
                for (e in entries) {
                    if (count++ > maxEntries) break
                    if (e.name.contains("..") || e.name.startsWith("/")) {
                        out += Finding(detector = name, ruleId = "ZIP_SLIP", severity = Severity.HIGH, weight = 60, evidence = mapOf("entry" to e.name))
                        continue
                    }
                    total += e.size
                    if (total > maxTotalBytes) {
                        out += Finding(detector = name, ruleId = "ZIP_BOMB", severity = Severity.HIGH, weight = 70)
                        break
                    }
                    // encrypted entry
                    if (e.method == ZipEntry.DEFLATED && e.size == 0L && e.compressedSize > 0) {
                        // likely encrypted; skip content
                    }
                    val lower = e.name.lowercase()
                    if (lower.endsWith(".zip")) {
                        // nested: extract to temp and recurse (best-effort)
                        val nested = File(tmpDir, e.name.substringAfterLast('/'))
                        nested.parentFile?.mkdirs()
                        zf.getInputStream(e).use { ins -> nested.outputStream().use { outs -> ins.copyTo(outs) } }
                        out += scanZip(nested, depth + 1)
                        nested.delete()
                    }
                }
            }
        } catch (e: Exception) {
            out += Finding(detector = name, ruleId = "ZIP_PARSE_ERROR", severity = Severity.LOW, weight = 5, evidence = mapOf("err" to (e.message ?: "")))
        } finally {
            tmpDir.deleteRecursively()
        }
        return out
    }
}