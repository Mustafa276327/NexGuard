package com.nexguard.feature.scan.detection

import com.nexguard.core.common.Finding
import com.nexguard.core.common.ScanResult
import com.nexguard.core.common.Severity
import com.nexguard.core.common.Verdict
import com.nexguard.core.database.AppDatabase
import com.nexguard.core.database.FindingEntity
import com.nexguard.core.database.ScanResultEntity
import com.nexguard.core.database.TimelineEntity
import com.nexguard.core.eventbus.EventBus
import com.nexguard.core.eventbus.NexEvent
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class ScanOrchestrator(
    private val detectors: List<Detector>,
    private val db: AppDatabase,
    private val bus: EventBus,
    private val cloudEnabled: Boolean = false
) {
    suspend fun scanFile(path: String): ScanResult = withContext(Dispatchers.IO) {
        bus.tryEmit(NexEvent.ScanStarted(path))
        val start = System.currentTimeMillis()
        val f = File(path)
        if (!f.exists()) {
            val result = ScanResult(targetPath = path, sha256 = "", verdict = Verdict.CLEAN, score = 0,
                startedAt = start, durationMs = 0, findings = emptyList())
            bus.tryEmit(NexEvent.ScanFinished(result))
            return@withContext result
        }
        val sha = sha256(f)
        val size = f.length()
        val mtime = f.lastModified()
        val head = try { f.readHead(64) } catch (e: Exception) { byteArrayOf() }
        val magic = head.copyOf(minOf(4, head.size))
        val target = ScanTarget(path, f, sha, size, mtime, magic, head)

        val sorted = detectors.sortedByDescending { it.priority }
        val allFindings = mutableListOf<Finding>()
        for (d in sorted) {
            try {
                val res = d.inspect(target)
                allFindings += res
                // early stop: two independent critical signals -> DANGEROUS already
                val distinctCritical = allFindings.filter { it.severity == Severity.CRITICAL }.map { it.detector }.toSet()
                if (distinctCritical.size >= 2) break
            } catch (e: Exception) {
                // detector failure shouldn't abort whole scan
            }
        }

        val score = computeScore(allFindings).coerceAtMost(100)
        val verdict = computeVerdict(score, allFindings)
        val result = ScanResult(
            targetPath = path, sha256 = sha, verdict = verdict, score = score,
            startedAt = start, durationMs = System.currentTimeMillis() - start, findings = allFindings
        )

        // Threat event for UI popup
        if (verdict != Verdict.CLEAN) {
            bus.tryEmit(NexEvent.ThreatDetected(path, verdict, score))
        }

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
            bus.tryEmit(NexEvent.ScanProgress(i + 1, out.count { it.verdict != com.nexguard.core.common.Verdict.CLEAN }))
        }
        out
    }

    private fun computeScore(findings: List<Finding>): Int {
        val byDetector = findings.groupBy { it.detector }
        var total = 0
        byDetector.forEach { (_, fs) ->
            val sorted = fs.map { it.weight }.sortedDescending()
            total += (sorted.firstOrNull() ?: 0) + sorted.drop(1).sum() / 2
        }
        return total
    }

    private fun computeVerdict(score: Int, findings: List<Finding>): Verdict {
        val distinctDetectors = findings.map { it.detector }.distinct().size
        val hasCritical = findings.any { it.severity == Severity.CRITICAL }
        return when {
            score >= 70 && hasCritical && distinctDetectors >= 2 -> Verdict.DANGEROUS
            score >= 30 -> Verdict.SUSPICIOUS
            else -> Verdict.CLEAN
        }
    }

    private suspend fun persist(r: ScanResult) {
        db.withTransaction {
            db.scanResultDao().insert(ScanResultEntity(r.id, r.targetPath, r.sha256, r.verdict.name, r.score, r.startedAt, r.durationMs))
            r.findings.forEach { f ->
                db.findingDao().insert(FindingEntity(f.id, r.id, f.detector, f.ruleId, f.severity.name, f.weight, toJson(f.evidence)))
            }
            db.timelineDao().insert(TimelineEntity(UUID.randomUUID().toString(), System.currentTimeMillis(), "scan", "scan_finished", "${r.targetPath} -> ${r.verdict}"))
        }
    }

    private fun toJson(map: Map<String, String>): String {
        if (map.isEmpty()) return "{}"
        return "{" + map.entries.joinToString(",") { "\"${it.key.replace("\\", "\\\\").replace("\"", "\\\"")}\":\"${it.value.replace("\\", "\\\\").replace("\"", "\\\"")}\"" } + "}"
    }

    private fun sha256(f: File): String = try {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { ins ->
            val buf = ByteArray(8192); var n: Int
            while (ins.read(buf).also { n = it } > 0) md.update(buf, 0, n)
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) { "" }
}