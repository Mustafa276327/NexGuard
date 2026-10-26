package com.nexguard.feature.scan.detection

import androidx.room.withTransaction
import android.util.Log
import com.nexguard.core.common.Finding
import com.nexguard.core.common.RiskScorer
import com.nexguard.core.common.ScanResult
import com.nexguard.core.common.ScanSession
import com.nexguard.core.common.ScanStatus
import com.nexguard.core.common.ScanHealth
import com.nexguard.core.common.ScanStatusOutcome
import com.nexguard.core.common.ScanStatusPolicy
import com.nexguard.core.common.Severity
import com.nexguard.core.common.ThreatCatalog
import com.nexguard.core.common.Verdict
import com.nexguard.core.database.AppDatabase
import com.nexguard.core.database.FileCacheEntity
import com.nexguard.core.database.FindingEntity
import com.nexguard.core.database.ScanResultEntity
import com.nexguard.core.database.ScanSessionEntity
import com.nexguard.core.database.TimelineEntity
import com.nexguard.core.eventbus.EventBus
import com.nexguard.core.eventbus.NexEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import java.io.File
import java.security.MessageDigest
import java.util.UUID

data class ScanOptions(
    /** manual | download | install | app */
    val source: String = "manual",
    /** true: önbelleği yok say, tüm içeriği yeniden incele. */
    val deep: Boolean = false,
    /** false: değişmemiş temiz dosyalar için önbelleği kullanma. */
    val useCache: Boolean = true,
    /** Yalnızca kullanıcı açtıysa: sadece hash'i buluta gönder. */
    val cloudEnabled: Boolean = false,
    val mode: String = "full",
    val label: String = ""
)

class ScanOrchestrator(
    private val detectors: List<Detector>,
    private val db: AppDatabase,
    private val bus: EventBus,
    private val cache: ScanCache = ScanCache(db)
) {
    private val networkDetectors = detectors.filter { it.network }
    private val localDetectors = detectors.filterNot { it.network }.sortedByDescending { it.priority }

    suspend fun scanFile(path: String, options: ScanOptions = ScanOptions(deep = true, useCache = false)): ScanResult =
        withContext(Dispatchers.IO) { scanOne(path, options, null, Mutex()) }

    /**
     * Yolları sırayla tarar, oturum kaydını başlatır ve bitirir.
     * Sonuçlar [onFileDone] ile canlı akar; dönen değer özet oturumdur.
     */
    suspend fun scanPaths(
        paths: List<String>,
        options: ScanOptions = ScanOptions(),
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        onFileStart: (String) -> Unit = {},
        onFileDone: (ScanResult) -> Unit = {}
    ): ScanSession = withContext(Dispatchers.IO) {
        val session = ScanSession(
            startedAt = System.currentTimeMillis(),
            mode = options.mode,
            label = options.label
        )
        try {
            db.scanSessionDao().insert(
                ScanSessionEntity(
                    id = session.id,
                    startedAt = session.startedAt,
                    mode = session.mode,
                    label = session.label,
                    filesScanned = 0,
                    threats = 0,
                    warnings = 0,
                    cachedFiles = 0,
                    status = "running"
                )
            )
        } catch (e: Exception) { /* DB yoksa tarama yine devam etsin */ }

        var scanned = 0
        var threats = 0
        var cached = 0
        val buffer = mutableListOf<FileCacheEntity>()

        suspend fun flush() {
            if (buffer.isEmpty()) return
            cache.putAll(buffer.toList())
            buffer.clear()
        }

        val scannedCounter = AtomicInteger(0)
        val threatCounter = AtomicInteger(0)
        val warningCounter = AtomicInteger(0)
        val cachedCounter = AtomicInteger(0)
        val issueCounter = AtomicInteger(0)
        val cacheMutex = Mutex()
        val concurrency = 4
        val channel = Channel<String>(64)
        try {
            coroutineScope {
                val workers = (1..concurrency).map {
                    async {
                        for (path in channel) {
                            currentCoroutineContext().ensureActive()
                            onFileStart(path)
                            val result = scanOne(path, options, buffer, cacheMutex)
                            val done = scannedCounter.incrementAndGet()
                            if (result.cached) cachedCounter.incrementAndGet()
                            if (result.isThreat) threatCounter.incrementAndGet()
                            if (result.isWarning) warningCounter.incrementAndGet()
                            if (result.status.isIssue) issueCounter.incrementAndGet()
                            onFileDone(result)
                            onProgress(done, paths.size)
                            bus.tryEmit(NexEvent.ScanProgress(done, threatCounter.get()))
                            cacheMutex.withLock {
                                if (buffer.size >= 100) {
                                    cache.putAll(buffer.toList())
                                    buffer.clear()
                                }
                            }
                        }
                    }
                }
                for (path in paths) {
                    currentCoroutineContext().ensureActive()
                    channel.send(path)
                }
                channel.close()
                workers.awaitAll()
            }
            flush()
            scanned = scannedCounter.get()
            threats = threatCounter.get()
            cached = cachedCounter.get()
            val finished = session.copy(
                finishedAt = System.currentTimeMillis(),
                filesScanned = scanned,
                threats = threats,
                warnings = warningCounter.get(),
                cachedFiles = cached,
                errors = issueCounter.get(),
                status = "completed",
                score = sessionScore(threats)
            )
            finishSession(finished)
            bus.tryEmit(NexEvent.Timeline(com.nexguard.core.common.TimelineEvent(
                source = "scan", kind = "session_finished",
                summary = buildString {
                    append("${finished.label}: $scanned dosya, $threats tehdit")
                    if (finished.warnings > 0) append(", ${finished.warnings} uyarı")
                },
                refId = finished.id
            )))
            finished
        } catch (e: CancellationException) {
            scanned = scannedCounter.get()
            threats = threatCounter.get()
            cached = cachedCounter.get()
            // İptal edilmiş bağlamda DB yazımı engellenebilir; özet kaydı yine de tamamlanmalı.
            withContext(NonCancellable) {
                flush()
                finishSession(
                    session.copy(
                        finishedAt = System.currentTimeMillis(),
                        filesScanned = scanned,
                        threats = threats,
                        warnings = warningCounter.get(),
                        cachedFiles = cached,
                        errors = issueCounter.get(),
                        status = "stopped",
                        score = sessionScore(threats)
                    )
                )
            }
            throw e
        } catch (e: Exception) {
            scanned = scannedCounter.get()
            threats = threatCounter.get()
            cached = cachedCounter.get()
            withContext(NonCancellable) {
                flush()
                finishSession(
                    session.copy(
                        finishedAt = System.currentTimeMillis(),
                        filesScanned = scanned,
                        threats = threats,
                        warnings = warningCounter.get(),
                        cachedFiles = cached,
                        errors = issueCounter.get(),
                        status = "failed",
                        score = sessionScore(threats)
                    )
                )
            }
            throw e
        }
    }

    private suspend fun finishSession(session: ScanSession) {
        try {
            db.scanSessionDao().finish(
                id = session.id,
                finishedAt = session.finishedAt ?: System.currentTimeMillis(),
                files = session.filesScanned,
                threats = session.threats,
                warnings = session.warnings,
                cached = session.cachedFiles,
                errors = session.errors,
                status = session.status,
                score = session.score
            )
        } catch (e: Exception) { /* özet kaydı kritik değil */ }
    }

    private fun sessionScore(threats: Int): Int = (100 - threats * 15).coerceIn(10, 100)

    private suspend fun scanOne(
        path: String,
        options: ScanOptions,
        cacheBuffer: MutableList<FileCacheEntity>?,
        cacheMutex: Mutex
    ): ScanResult {
        val startedAt = System.currentTimeMillis()
        val f = File(path)
        val exists = f.exists()
        val isFile = exists && f.isFile
        val readable = isFile && f.canRead()
        val health = ScanHealth(exists = exists, isFile = isFile, readable = readable)
        if (!exists || !isFile || !readable) {
            // Erişilemeyen dosya için hüküm üretilmez; "temiz" denmez.
            return inconclusive(path, startedAt, options, ScanStatusPolicy.evaluate(health))
        }

        val size = f.length()
        val mtime = f.lastModified()
        val extension = f.extension.lowercase()

        // Sistem bölümündeki uygulamalar platform imzasıyla gelir ve salt
        // okunurdur; kullanıcı uygulaması gibi taranmaz. Apps ekranı da bu
        // uygulamaları "güvenilir" olarak gösterir.
        if (ScanPolicy.isTrustedSystemApk(path)) {
            return ScanResult(
                targetPath = path,
                sha256 = "",
                verdict = Verdict.CLEAN,
                score = 0,
                startedAt = startedAt,
                durationMs = System.currentTimeMillis() - startedAt,
                sizeBytes = size,
                cached = false,
                source = options.source,
                status = ScanStatus.COMPLETED
            ).also { bus.tryEmit(NexEvent.ScanFinished(it)) }
        }

        if (options.useCache && !options.deep) {
            cache.cleanCached(path, size, mtime)?.let { hit ->
                return ScanResult(
                    targetPath = path,
                    sha256 = hit.sha256,
                    verdict = Verdict.CLEAN,
                    score = hit.score,
                    startedAt = startedAt,
                    durationMs = 0,
                    findings = emptyList(),
                    sizeBytes = size,
                    cached = true,
                    source = options.source
                )
            }
        }

        val sha = sha256(f, ScanPolicy.hashLimit(extension, size))
        if (sha.isBlank()) {
            // İçerik okunamadıysa imza/örnek analizi güvenilir değildir.
            return inconclusive(
                path, startedAt, options,
                ScanStatusPolicy.evaluate(health.copy(hashComputed = false)),
                size = size
            )
        }
        val sampleLimit = ScanPolicy.sampleLimit(extension, size)
        val head = f.readHead(minOf(sampleLimit, 64))
        val target = ScanTarget(
            path = path,
            file = f,
            sha256 = sha,
            size = size,
            mtime = mtime,
            magic = head.copyOf(minOf(4, head.size)),
            sampleLimit = sampleLimit
        )

        val allFindings = mutableListOf<Finding>()
        val failedDetectors = mutableListOf<String>()
        for (d in localDetectors) {
            currentCoroutineContext().ensureActive()
            try {
                allFindings += d.inspect(target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Dedektör hatası taramayı durdurmaz ama sonucu "kesin" de yapmaz.
                // Tanılama için yalnızca dedektör adı yazılır; dosya yolu loglanmaz.
                Log.w("NexGuardScan", "Dedektör çalışamadı: ${d.name}", e)
                failedDetectors += d.name
            }
        }

        var (score, verdict) = RiskScorer.evaluate(allFindings)

        // Bulut yalnızca kullanıcı açtıysa ve yerel analiz dosyayı şüpheli bulduysa.
        // Böylece her çalıştırılabilir dosya için ağ isteği yapılmaz.
        if (options.cloudEnabled && sha.isNotBlank() && verdict == Verdict.SUSPICIOUS) {
            for (d in networkDetectors) {
                currentCoroutineContext().ensureActive()
                try {
                    allFindings += d.inspect(target)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Ağ hatası yerel sonucu değiştirmez.
                }
            }
            val evaluated = RiskScorer.evaluate(allFindings)
            score = evaluated.first
            verdict = evaluated.second
        }

        val limitedFindings = allFindings
            .distinctBy { it.detector to it.ruleId to it.evidence.values.joinToString("|") }
            .take(200)
        val threatType = if (verdict == Verdict.CLEAN) "" else ThreatCatalog.threatType(limitedFindings)

        // Dosya tarama sırasında değiştiyse sonuç kesin sayılmaz.
        val changedDuringScan = f.length() != size || f.lastModified() != mtime
        val outcome = ScanStatusPolicy.evaluate(
            health.copy(failedDetectors = failedDetectors, changedDuringScan = changedDuringScan)
        )

        val result = ScanResult(
            targetPath = path,
            sha256 = sha,
            verdict = verdict,
            score = score,
            startedAt = startedAt,
            durationMs = System.currentTimeMillis() - startedAt,
            findings = limitedFindings,
            sizeBytes = size,
            cached = false,
            source = options.source,
            status = outcome.status,
            error = outcome.message,
            detectorErrors = failedDetectors.distinct()
        )

        // Önbellek yalnızca kesin (tamamlanmış) sonuçlar için kullanılır.
        // Uyarı içeren sonuçlar önbelleğe yazılmaz; bulgular kaybolmasın ve
        // uyarılar her taramada görünmeye devam etsin.
        if (outcome.status.isConclusive && !result.isWarning) {
            val cacheEntity = FileCacheEntity(
                targetPath = path,
                sizeBytes = size,
                mtime = mtime,
                sha256 = sha,
                verdict = verdict.name,
                score = score,
                threatType = threatType,
                engineVersion = ScanPolicy.ENGINE_VERSION,
                updatedAt = System.currentTimeMillis()
            )
            if (cacheBuffer != null) cacheMutex.withLock { cacheBuffer += cacheEntity } else cache.put(cacheEntity)
        }

        if (result.isThreat) {
            persistThreat(result, threatType)
            bus.tryEmit(NexEvent.ThreatDetected(path, verdict, score, threatType, options.source))
        }
        bus.tryEmit(NexEvent.ScanFinished(result))
        return result
    }

    /**
     * Dosya okunamadığında/analiz tamamlanamadığında üretilen sonuç. Hüküm
     * [Verdict.CLEAN] görünse bile [ScanStatus] ile "temiz değil, bilinmiyor"
     * olarak işaretlenir; önbelleğe yazılmaz, tehdit sayılmaz.
     */
    private fun inconclusive(
        path: String,
        startedAt: Long,
        options: ScanOptions,
        outcome: ScanStatusOutcome,
        size: Long = 0
    ): ScanResult = ScanResult(
        targetPath = path,
        sha256 = "",
        verdict = Verdict.CLEAN,
        score = 0,
        startedAt = startedAt,
        durationMs = System.currentTimeMillis() - startedAt,
        sizeBytes = size,
        cached = false,
        source = options.source,
        status = outcome.status,
        error = outcome.message
    )

    private suspend fun persistThreat(r: ScanResult, threatType: String) {
        try {
            db.withTransaction {
                db.scanResultDao().insert(
                    ScanResultEntity(
                        id = r.id,
                        targetPath = r.targetPath,
                        sha256 = r.sha256,
                        verdict = r.verdict.name,
                        score = r.score,
                        startedAt = r.startedAt,
                        durationMs = r.durationMs,
                        sizeBytes = r.sizeBytes,
                        threatType = threatType,
                        source = r.source
                    )
                )
                db.findingDao().insertAll(
                    r.findings.map { f ->
                        FindingEntity(
                            id = f.id,
                            scanId = r.id,
                            detector = f.detector,
                            ruleId = f.ruleId,
                            severity = f.severity.name,
                            weight = f.weight,
                            evidenceJson = toJson(f.evidence)
                        )
                    }
                )
                db.timelineDao().insert(
                    TimelineEntity(
                        id = UUID.randomUUID().toString(),
                        ts = System.currentTimeMillis(),
                        source = r.source,
                        kind = "threat",
                        summary = "${File(r.targetPath).name}: $threatType (${r.score})",
                        refId = r.id
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Kalıcı kayıt başarısız olsa da tarama sonucu gösterilir.
        }
    }

    private fun toJson(map: Map<String, String>): String {
        if (map.isEmpty()) return "{}"
        return "{" + map.entries.joinToString(",") {
            "\"${it.key.replace("\\", "\\\\").replace("\"", "\\\"")}\":\"${it.value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        } + "}"
    }

    private suspend fun sha256(f: File, maxBytes: Long): String = try {
        val md = MessageDigest.getInstance("SHA-256")
        var total = 0L
        f.inputStream().use { ins ->
            val buf = ByteArray(16 * 1024)
            while (total < maxBytes) {
                // Büyük dosyalarda durdurma isteğine hızlı yanıt ver.
                currentCoroutineContext().ensureActive()
                val n = ins.read(buf)
                if (n <= 0) break
                val take = minOf(n.toLong(), maxBytes - total).toInt()
                md.update(buf, 0, take)
                total += take
            }
        }
        val digest = md.digest().joinToString("") { "%02x".format(it) }
        if (f.length() > maxBytes) "$digest-$total" else digest
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ""
    }
}
