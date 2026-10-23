package com.nexguard.feature.scan

import com.nexguard.core.common.Finding
import com.nexguard.core.common.ScanResult
import com.nexguard.core.common.Severity
import com.nexguard.core.common.Verdict
import com.nexguard.core.database.AppDatabase
import com.nexguard.core.database.ScanResultEntity
import com.nexguard.core.database.FindingEntity
import com.nexguard.core.eventbus.EventBus
import com.nexguard.core.eventbus.NexEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Scanner @Inject constructor(private val db: AppDatabase, private val bus: EventBus) {
    private val eicar = "X5O!P%@AP[4\\PZX54(P^)7CC)7}\$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!\$H+H*"

    suspend fun scanFile(path: String): ScanResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val f = File(path)
        val sha = sha256(f)
        val findings = mutableListOf<Finding>()
        var score = 0
        if (f.exists()) {
            val content = try { f.readText(Charsets.ISO_8859_1) } catch (e: Exception) { "" }
            if (content.contains("EICAR-STANDARD-ANTIVIRUS-TEST-FILE")) {
                findings += Finding(detector = "signature", ruleId = "EICAR", severity = Severity.CRITICAL, weight = 90, evidence = mapOf("match" to "EICAR"))
                score += 90
            }
            // suspicious extension mismatch
            val ext = f.extension.lowercase()
            if (ext in listOf("jpg","png","pdf") && f.readBytes().copyOf(4).contentEquals(byteArrayOf(0x50,0x4B,0x03,0x04))) {
                findings += Finding(detector = "prefilter", ruleId = "EXT_MISMATCH", severity = Severity.MEDIUM, weight = 40)
                score += 40
            }
        }
        val verdict = when {
            score >= 70 && findings.size >= 2 -> Verdict.DANGEROUS
            score >= 30 -> Verdict.SUSPICIOUS
            else -> Verdict.CLEAN
        }
        // enforce 2-signal rule for dangerous
        val finalVerdict = if (verdict == Verdict.DANGEROUS && findings.map { it.detector }.distinct().size < 2) Verdict.SUSPICIOUS else verdict
        val result = ScanResult(targetPath = path, sha256 = sha, verdict = finalVerdict, score = score.coerceAtMost(100), startedAt = start, durationMs = System.currentTimeMillis() - start, findings = findings)
        persist(result)
        bus.tryEmit(NexEvent.ScanFinished(result))
        result
    }

    suspend fun scanDirectory(root: String, onProgress: (Int, Int) -> Unit = { _, _ -> }): List<ScanResult> = withContext(Dispatchers.IO) {
        val files = File(root).walkTopDown().filter { it.isFile }.toList()
        val out = mutableListOf<ScanResult>()
        files.forEachIndexed { i, f ->
            out += scanFile(f.absolutePath)
            onProgress(i + 1, files.size)
            bus.tryEmit(NexEvent.ScanProgress(i + 1, out.count { it.verdict != Verdict.CLEAN }))
        }
        out
    }

    private fun sha256(f: File): String = try {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { ins ->
            val buf = ByteArray(8192); var n: Int
            while (ins.read(buf).also { n = it } > 0) md.update(buf, 0, n)
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) { "" }

    private suspend fun persist(r: ScanResult) {
        db.scanResultDao().insert(ScanResultEntity(r.id, r.targetPath, r.sha256, r.verdict.name, r.score, r.startedAt, r.durationMs))
        r.findings.forEach { f ->
            db.findingDao().insert(FindingEntity(f.id, r.id, f.detector, f.ruleId, f.severity.name, f.weight, f.evidence.toString()))
        }
        db.timelineDao().insert(com.nexguard.core.database.TimelineEntity(java.util.UUID.randomUUID().toString(), System.currentTimeMillis(), "scan", "scan_finished", "${r.targetPath} -> ${r.verdict}"))
    }
}