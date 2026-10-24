package com.nexguard.feature.scan.detection

import com.nexguard.core.common.Finding
import com.nexguard.core.common.Severity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * ZIP/JAR arşivleri için zip bomb, zip-slip, iç içe arşiv ve makro taraması.
 * Hiçbir giriş diske sınırsız çıkarılmaz; toplam bütçe ve derinlik sınırı vardır.
 */
class ArchiveScanner(
    private val maxDepth: Int = 3,
    private val maxEntries: Int = 10_000,
    private val maxTotalBytes: Long = 500L * 1024 * 1024,
    private val maxNestedBytes: Long = 20L * 1024 * 1024
) : Detector {
    override val name = "archive"
    override val priority = 80

    private class Budget {
        var entries = 0
        var bytes = 0L
    }

    override suspend fun inspect(target: ScanTarget): List<Finding> {
        if (target.extension !in setOf("zip", "jar")) return emptyList()
        val budget = Budget()
        val out = scanZip(target.file, 0, budget).toMutableList()
        if (budget.entries >= maxEntries) {
            out += Finding(
                detector = name, ruleId = "ZIP_TOO_MANY_ENTRIES", severity = Severity.MEDIUM, weight = 20,
                evidence = mapOf("entries" to budget.entries.toString())
            )
        }
        return out
    }

    private suspend fun scanZip(f: File, depth: Int, budget: Budget): List<Finding> {
        if (depth > maxDepth) {
            return listOf(Finding(detector = name, ruleId = "NESTED_TOO_DEEP", severity = Severity.MEDIUM, weight = 20))
        }
        val out = mutableListOf<Finding>()
        val tmpDir = File(System.getProperty("java.io.tmpdir") ?: ".", "ng-archive-scan")
        try {
            ZipFile(f).use { zf ->
                val entries = zf.entries()
                while (entries.hasMoreElements()) {
                    currentCoroutineContext().ensureActive()
                    val e = entries.nextElement()
                    budget.entries++
                    if (budget.entries > maxEntries) break
                    if (budget.bytes > maxTotalBytes) {
                        out += Finding(detector = name, ruleId = "ZIP_BOMB", severity = Severity.HIGH, weight = 70)
                        break
                    }

                    // zip-slip
                    if (e.name.contains("..") || e.name.startsWith("/") || e.name.contains('\\')) {
                        out += Finding(
                            detector = name, ruleId = "ZIP_SLIP", severity = Severity.HIGH, weight = 60,
                            evidence = mapOf("entry" to e.name.take(160))
                        )
                        continue
                    }

                    if (e.name.endsWith("vbaProject.bin", ignoreCase = true)) {
                        out += Finding(
                            detector = name, ruleId = "OOXML_MACRO", severity = Severity.HIGH, weight = 55,
                            evidence = mapOf("entry" to e.name.take(120))
                        )
                    }

                    val size = if (e.size > 0) e.size else 0L
                    budget.bytes += size
                    if (budget.bytes > maxTotalBytes) {
                        out += Finding(detector = name, ruleId = "ZIP_BOMB", severity = Severity.HIGH, weight = 70)
                        break
                    }
                    // Aşırı sıkıştırma oranı (zip bomb göstergesi)
                    if (e.compressedSize > 0 && size > 10L * 1024 * 1024 && size / e.compressedSize > 1000) {
                        out += Finding(
                            detector = name, ruleId = "ZIP_BOMB", severity = Severity.HIGH, weight = 75,
                            evidence = mapOf("entry" to e.name.take(120), "ratio" to (size / e.compressedSize).toString())
                        )
                    }

                    if (e.name.endsWith(".zip", ignoreCase = true) && depth < maxDepth &&
                        size in 1..maxNestedBytes
                    ) {
                        val safeName = e.name.substringAfterLast('/').replace(Regex("""[^A-Za-z0-9._-]"""), "_")
                        val nested = File(tmpDir, "${System.nanoTime()}_$safeName")
                        try {
                            nested.parentFile?.mkdirs()
                            val copied = copyLimited(zf, e, nested, maxNestedBytes)
                            if (copied) {
                                out += scanZip(nested, depth + 1, budget)
                            } else {
                                out += Finding(
                                    detector = name, ruleId = "ZIP_BOMB", severity = Severity.HIGH, weight = 65,
                                    evidence = mapOf("entry" to e.name.take(120))
                                )
                            }
                        } finally {
                            nested.delete()
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            out += Finding(
                detector = name, ruleId = "ZIP_PARSE_ERROR", severity = Severity.LOW, weight = 5,
                evidence = mapOf("err" to (e.message ?: "").take(120))
            )
        } finally {
            try { tmpDir.deleteRecursively() } catch (e: Exception) { }
        }
        return out
    }

    /** Girişi en fazla [limit] bayt olacak şekilde çıkarır. Sınır aşılırsa false. */
    private suspend fun copyLimited(zf: ZipFile, e: ZipEntry, dst: File, limit: Long): Boolean {
        var total = 0L
        try {
            zf.getInputStream(e).use { ins ->
                dst.outputStream().use { outs ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = ins.read(buf)
                        if (n <= 0) break
                        total += n
                        if (total > limit) return false
                        outs.write(buf, 0, n)
                    }
                }
            }
            return true
        } catch (e: CancellationException) {
            dst.delete()
            throw e
        } catch (e: Exception) {
            dst.delete()
            return false
        }
    }
}
