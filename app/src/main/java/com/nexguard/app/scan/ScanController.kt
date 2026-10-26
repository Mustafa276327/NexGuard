package com.nexguard.app.scan

import android.content.Context
import androidx.lifecycle.ViewModel
import com.nexguard.app.util.NgLog
import com.nexguard.app.util.Prefs
import com.nexguard.core.common.ScanResult
import com.nexguard.core.common.ScanSession
import com.nexguard.core.common.Verdict
import com.nexguard.feature.alerts.AlertManager
import com.nexguard.feature.quarantine.QuarantineManager
import com.nexguard.feature.scan.Scanner
import com.nexguard.feature.scan.detection.ScanOptions
import com.nexguard.feature.scan.detection.ScanPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ScanUiState(
    val running: Boolean = false,
    val preparing: Boolean = false,
    val stopped: Boolean = false,
    val finished: Boolean = false,
    val mode: ScanMode = ScanMode.FULL,
    val label: String = "",
    val root: String = "",
    val current: String = "",
    val scanned: Int = 0,
    val total: Int = 0,
    val threats: Int = 0,
    /** Tehdit eşiğinde olmayan, açıklamalı uyarı üreten dosya sayısı. */
    val warnings: Int = 0,
    val cached: Int = 0,
    /** Teknik olarak tamamlanamayan dosya sayısı (okunamadı, bulunamadı, kısmi). */
    val issues: Int = 0,
    val feed: List<com.nexguard.core.common.ScanResult> = emptyList(),
    val session: com.nexguard.core.common.ScanSession? = null,
    val handledPaths: Set<String> = emptySet(),
    val message: String? = null,
    val error: String? = null
)

enum class ScanMode { FULL, QUICK }

@Singleton
class ScanController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanner: Scanner
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    private var job: Job? = null

    val engineVersion: Int get() = scanner.engineVersion
    val detectorCount: Int get() = scanner.detectorCount
    val signatureCount: Int get() = scanner.signatureCount

    fun updateState(transform: (ScanUiState) -> ScanUiState) {
        _state.update(transform)
    }

    fun scan(mode: ScanMode = ScanMode.FULL) {
        if (job?.isActive == true) return
        job = scope.launch {
            val label = if (mode == ScanMode.FULL) "Tam tarama" else "Hızlı tarama"
            val startedAt = System.currentTimeMillis()
            _state.value = ScanUiState(
                running = true,
                preparing = true,
                mode = mode,
                label = label,
                root = if (mode == ScanMode.FULL) "Tüm depolama + kurulu uygulamalar" else "İndirilenler ve belgeler"
            )
            NgLog.i("Tarama", "$label başlatıldı")
            try {
                val paths = collectTargets(mode)
                if (paths.isEmpty()) {
                    NgLog.w("Tarama", "$label için taranacak dosya bulunamadı")
                    _state.update {
                        it.copy(
                            running = false,
                            preparing = false,
                            error = "Taranacak dosya bulunamadı. Depolama iznini kontrol edin."
                        )
                    }
                    return@launch
                }
                NgLog.i("Tarama", "${paths.size} dosya tarama kuyruğuna alındı")
                val useCache = Prefs.useCache(context) && mode == ScanMode.FULL
                val options = com.nexguard.feature.scan.detection.ScanOptions(
                    source = "manual",
                    deep = !useCache,
                    useCache = useCache,
                    cloudEnabled = Prefs.cloudLookup(context),
                    mode = if (mode == ScanMode.FULL) "full" else "quick",
                    label = label
                )
                _state.update { it.copy(preparing = false, total = paths.size) }

                var lastCurrent = 0L
                var lastFeed = 0L
                var lastProgress = 0L
                fun due(last: Long, intervalMs: Long): Boolean =
                    System.currentTimeMillis() - last >= intervalMs

                val session = scanner.scanPaths(
                    paths = paths,
                    options = options,
                    onProgress = { done, total ->
                        if (done == total || due(lastProgress, 100L)) {
                            lastProgress = System.currentTimeMillis()
                            _state.update { it.copy(scanned = done, total = total) }
                        }
                    },
                    onFileStart = { path ->
                        if (due(lastCurrent, 120L)) {
                            lastCurrent = System.currentTimeMillis()
                            _state.update { it.copy(current = File(path).name.ifBlank { path }) }
                        }
                    },
                    onFileDone = { r ->
                        val threat = r.isThreat
                        val warning = r.isWarning
                        val issue = r.status.isIssue
                        if (threat || warning || issue || due(lastFeed, 100L)) {
                            lastFeed = System.currentTimeMillis()
                            _state.update { st ->
                                st.copy(
                                    feed = (listOf(r) + st.feed).take(500),
                                    threats = st.threats + if (threat) 1 else 0,
                                    warnings = st.warnings + if (warning) 1 else 0,
                                    issues = st.issues + if (issue) 1 else 0,
                                    cached = st.cached + if (r.cached) 1 else 0
                                )
                            }
                        }
                    }
                )
                _state.update {
                    it.copy(
                        running = false,
                        finished = true,
                        current = "",
                        scanned = session.filesScanned,
                        threats = session.threats,
                        warnings = session.warnings,
                        cached = session.cachedFiles,
                        issues = session.errors,
                        session = session
                    )
                }
                NgLog.i(
                    "Tarama",
                    "$label tamamlandı: ${session.filesScanned} dosya, ${session.threats} tehdit, " +
                        "${session.warnings} uyarı, ${session.errors} sorun, skor ${session.score}"
                )
            } catch (e: CancellationException) {
                NgLog.w("Tarama", "$label kullanıcı tarafından durduruldu")
                val current = _state.value
                val stoppedSession = ScanSession(
                    startedAt = startedAt,
                    finishedAt = System.currentTimeMillis(),
                    mode = if (mode == ScanMode.FULL) "full" else "quick",
                    label = label,
                    filesScanned = current.scanned,
                    threats = current.threats,
                    warnings = current.warnings,
                    cachedFiles = current.cached,
                    errors = current.issues,
                    status = "stopped",
                    score = (100 - current.threats * 15).coerceIn(10, 100)
                )
                _state.update {
                    it.copy(
                        running = false,
                        preparing = false,
                        stopped = true,
                        current = "",
                        session = stoppedSession
                    )
                }
                throw e
            } catch (e: Exception) {
                NgLog.e("Tarama", "$label hatası: ${e.message ?: "bilinmeyen hata"}")
                _state.update {
                    it.copy(
                        running = false,
                        preparing = false,
                        error = e.message ?: "Tarama sırasında bir hata oluştu",
                        current = ""
                    )
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    fun clearMessage() {
        _state.update { it.copy(message = null, error = null) }
    }

    private suspend fun collectTargets(mode: ScanMode): List<String> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val out = LinkedHashSet<String>()
        val roots = storageRoots()
        val primaryRoot = roots.firstOrNull() ?: File("/storage/emulated/0")
        val ctx = coroutineContext

        fun addTree(base: File, limit: Int) {
            if (!base.exists()) return
            try {
                base.walkTopDown()
                    .onEnter { dir ->
                        ctx.ensureActive()
                        dir == base || !ScanPolicy.shouldSkipDirectory(dir.absolutePath, dir.name)
                    }
                    .onEach { ctx.ensureActive() }
                    .filter { it.isFile && it.length() > 0 && !ScanPolicy.shouldSkipFile(it.name) }
                    .take(limit)
                    .forEach { out += it.absolutePath }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
        }

        if (mode == ScanMode.QUICK) {
            val quickDirs = listOf(
                "Download", "Downloads", "Documents", "DCIM", "Pictures", "Movies",
                "Bluetooth", "Telegram/Telegram Documents",
                "WhatsApp/Media/WhatsApp Documents"
            )
            quickDirs.forEach { addTree(File(primaryRoot, it), 20_000) }
        } else {
            var remaining = 200_000
            roots.forEach { root ->
                if (remaining > 0) {
                    addTree(root, remaining)
                    remaining = (200_000 - out.size).coerceAtLeast(0)
                }
            }
            if (Prefs.scanInstalledApps(context)) {
                try {
                    ctx.ensureActive()
                    @Suppress("DEPRECATION")
                    context.packageManager.getInstalledPackages(0).forEach { pi ->
                        ctx.ensureActive()
                        val ai = pi.applicationInfo ?: return@forEach
                        // Sistem uygulamaları ve kendi paketimiz taranmaz: değiştirilemezler
                        // ve güçlü izinleri nedeniyle yanlış alarm üretirler.
                        val systemFlags = android.content.pm.ApplicationInfo.FLAG_SYSTEM or
                            android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
                        val isSystem = (ai.flags and systemFlags) != 0
                        if (isSystem || pi.packageName == context.packageName) return@forEach
                        ai.sourceDir?.let { if (it.isNotBlank()) out += it }
                        ai.splitSourceDirs?.forEach { if (it.isNotBlank()) out += it }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                }
            }
        }
        out.toList()
    }

    private fun storageRoots(): List<File> {
        val roots = LinkedHashSet<File>()
        try {
            android.os.Environment.getExternalStorageDirectory()?.let { roots += it }
        } catch (e: Exception) { }
        try {
            context.getExternalFilesDirs(null).forEach { dir ->
                val path = dir?.absolutePath?.replace('\\', '/') ?: return@forEach
                val marker = "/Android/data/"
                val index = path.indexOf(marker)
                if (index > 0) {
                    val volume = File(path.substring(0, index))
                    if (volume.exists()) roots += volume
                }
            }
        } catch (e: Exception) { }
        if (roots.isEmpty()) roots += File("/storage/emulated/0")
        return roots.toList()
    }

    fun onCleared() {
        job?.cancel()
    }
}
