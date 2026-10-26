package com.nexguard.feature.scan

import android.content.Context
import com.nexguard.core.common.ScanResult
import com.nexguard.core.common.ScanSession
import com.nexguard.core.database.AppDatabase
import com.nexguard.core.eventbus.EventBus
import com.nexguard.feature.scan.detection.ApkAnalyzer
import com.nexguard.feature.scan.detection.ArchiveScanner
import com.nexguard.feature.scan.detection.CloudDetector
import com.nexguard.feature.scan.detection.CloudVerifier
import com.nexguard.feature.scan.detection.Detector
import com.nexguard.feature.scan.detection.DocumentAnalyzer
import com.nexguard.feature.scan.detection.EntropyDetector
import com.nexguard.feature.scan.detection.HashDatabase
import com.nexguard.feature.scan.detection.HashDetector
import com.nexguard.feature.scan.detection.PeAnalyzer
import com.nexguard.feature.scan.detection.PrefilterDetector
import com.nexguard.feature.scan.detection.PythonAnalyzer
import com.nexguard.feature.scan.detection.ScanCache
import com.nexguard.feature.scan.detection.ScanOptions
import com.nexguard.feature.scan.detection.ScanOrchestrator
import com.nexguard.feature.scan.detection.ScanPolicy
import com.nexguard.feature.scan.detection.ScriptAnalyzer
import com.nexguard.feature.scan.detection.StringIocDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Scanner @Inject constructor(
    @ApplicationContext context: Context,
    private val db: AppDatabase,
    private val bus: EventBus
) {
    private val hashDb = HashDatabase.load(context)
    private val cache = ScanCache(db)

    private val detectors: List<Detector> = listOf(
        PrefilterDetector(),
        HashDetector(hashDb),
        ApkAnalyzer(context),
        ArchiveScanner(),
        PeAnalyzer(),
        PythonAnalyzer(),
        ScriptAnalyzer(),
        DocumentAnalyzer(),
        EntropyDetector(),
        StringIocDetector(),
        CloudDetector(CloudVerifier(vtApiKey = null, mbEnabled = true))
    )

    private val orchestrator = ScanOrchestrator(detectors, db, bus, cache)

    val engineVersion: Int get() = ScanPolicy.ENGINE_VERSION
    val detectorCount: Int get() = detectors.size
    val signatureCount: Int get() = hashDb.size

    suspend fun scanFile(path: String, options: ScanOptions = ScanOptions(deep = true, useCache = false)): ScanResult =
        orchestrator.scanFile(path, options)

    suspend fun scanPaths(
        paths: List<String>,
        options: ScanOptions = ScanOptions(),
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        onFileStart: (String) -> Unit = {},
        onFileDone: (ScanResult) -> Unit = {}
    ): ScanSession = orchestrator.scanPaths(paths, options, onProgress, onFileStart, onFileDone)

    suspend fun scanDirectory(
        root: String,
        options: ScanOptions = ScanOptions(),
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        onFileStart: (String) -> Unit = {},
        onFileDone: (ScanResult) -> Unit = {}
    ): ScanSession {
        val paths = java.io.File(root).walkTopDown()
            .onEnter { dir -> !ScanPolicy.shouldSkipDirectory(dir.absolutePath, dir.name) }
            .filter { it.isFile && !ScanPolicy.shouldSkipFile(it.name) }
            .map { it.absolutePath }
            .toList()
        return scanPaths(paths, options, onProgress, onFileStart, onFileDone)
    }

    suspend fun cacheCount(): Int = cache.count()
    suspend fun clearCache() = cache.clear()
}
