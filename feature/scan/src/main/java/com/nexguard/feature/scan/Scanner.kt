package com.nexguard.feature.scan

import com.nexguard.core.common.ScanResult
import com.nexguard.core.database.AppDatabase
import com.nexguard.core.eventbus.EventBus
import com.nexguard.feature.scan.detection.ApkAnalyzer
import com.nexguard.feature.scan.detection.ArchiveScanner
import com.nexguard.feature.scan.detection.CloudDetector
import com.nexguard.feature.scan.detection.CloudVerifier
import com.nexguard.feature.scan.detection.Detector
import com.nexguard.feature.scan.detection.DocumentAnalyzer
import com.nexguard.feature.scan.detection.EntropyDetector
import com.nexguard.feature.scan.detection.FuzzyHashDetector
import com.nexguard.feature.scan.detection.HashDatabase
import com.nexguard.feature.scan.detection.HashDetector
import com.nexguard.feature.scan.detection.PrefilterDetector
import com.nexguard.feature.scan.detection.ScanOrchestrator
import com.nexguard.feature.scan.detection.StringIocDetector
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Scanner @Inject constructor(private val db: AppDatabase, private val bus: EventBus) {
    private val detectors: List<Detector> = listOf(
        PrefilterDetector(),
        HashDetector(HashDatabase(emptySet())),
        ApkAnalyzer(),
        ArchiveScanner(),
        DocumentAnalyzer(),
        EntropyDetector(),
        StringIocDetector(),
        FuzzyHashDetector(emptySet()),
        CloudDetector(CloudVerifier(vtApiKey = null, mbEnabled = true))
    )
    private val orchestrator = ScanOrchestrator(detectors, db, bus)

    suspend fun scanFile(path: String): ScanResult = orchestrator.scanFile(path)
    suspend fun scanDirectory(root: String, onProgress: (Int, Int) -> Unit = { _, _ -> }): List<ScanResult> =
        orchestrator.scanDirectory(root, onProgress)
}