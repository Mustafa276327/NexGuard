package com.nexguard.app.ui.scan

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexguard.core.common.ScanResult
import com.nexguard.core.common.Verdict
import com.nexguard.feature.quarantine.QuarantineManager
import com.nexguard.feature.scan.Scanner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

enum class ScanMode { FULL, QUICK }

@HiltViewModel
class ScanViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanner: Scanner,
    private val quarantineManager: QuarantineManager
) : ViewModel() {

    data class UiState(
        val running: Boolean = false,
        val preparing: Boolean = false,
        val stopped: Boolean = false,
        val finished: Boolean = false,
        val mode: ScanMode = ScanMode.FULL,
        val root: String = "",
        val current: String = "",
        val scanned: Int = 0,
        val total: Int = 0,
        val threats: Int = 0,
        val feed: List<ScanResult> = emptyList(),
        val message: String? = null,
        val error: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var job: Job? = null

    fun defaultRoot(): String =
        if (File("/storage/emulated/0").exists()) "/storage/emulated/0" else context.filesDir.absolutePath

    fun scan(mode: ScanMode = ScanMode.FULL) {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            _state.value = UiState(
                running = true,
                preparing = true,
                mode = mode,
                root = if (mode == ScanMode.FULL) "Tüm depolama + kurulu uygulamalar" else "İndirilenler"
            )
            try {
                val paths = withContext(Dispatchers.IO) { collectTargets(mode) }
                _state.update { it.copy(preparing = false, total = paths.size) }
                val results = scanner.scanPaths(
                    paths = paths,
                    onProgress = { done, total ->
                        _state.update { it.copy(scanned = done, total = total) }
                    },
                    onFileStart = { path ->
                        _state.update { it.copy(current = File(path).name) }
                    },
                    onFileDone = { r ->
                        _state.update { st ->
                            st.copy(
                                feed = (listOf(r) + st.feed).take(300),
                                threats = st.threats + if (r.verdict != Verdict.CLEAN) 1 else 0
                            )
                        }
                    }
                )
                _state.update {
                    it.copy(
                        running = false,
                        finished = true,
                        current = "",
                        scanned = results.size,
                        total = if (it.total == 0) results.size else it.total
                    )
                }
            } catch (e: CancellationException) {
                _state.update { it.copy(running = false, stopped = true, current = "") }
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(running = false, error = e.message ?: "Tarama hatası", current = "") }
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    fun quarantine(r: ScanResult) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = try {
                quarantineManager.quarantine(r.targetPath, "Tarama: ${r.verdict} (${r.score})")
                true
            } catch (e: Exception) {
                false
            }
            _state.update {
                it.copy(message = if (ok) "Karantinaya alındı: ${File(r.targetPath).name}" else "Karantinaya alınamadı")
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun collectTargets(mode: ScanMode): List<String> {
        val out = LinkedHashSet<String>()
        if (mode == ScanMode.QUICK) {
            val dl = File("/storage/emulated/0/Download")
            if (dl.exists()) {
                dl.walkTopDown().filter { it.isFile }.forEach { out += it.absolutePath }
            }
        } else {
            val root = File("/storage/emulated/0")
            if (root.exists()) {
                root.walkTopDown().filter { it.isFile }.forEach { out += it.absolutePath }
            }
            // Kurulu uygulamaların APK dosyaları
            try {
                @Suppress("DEPRECATION")
                context.packageManager.getInstalledPackages(0).forEach { pi ->
                    val ai = pi.applicationInfo ?: return@forEach
                    out += ai.sourceDir
                    ai.splitSourceDirs?.forEach { out += it }
                }
            } catch (e: Exception) { /* erişilemeyen paketler atlanır */ }
        }
        return out.toList()
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }
}
