package com.nexguard.app.ui.scan

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexguard.core.common.ScanResult
import com.nexguard.core.common.Verdict
import com.nexguard.feature.scan.Scanner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanner: Scanner
) : ViewModel() {

    data class UiState(
        val running: Boolean = false,
        val stopped: Boolean = false,
        val finished: Boolean = false,
        val root: String = "",
        val current: String = "",
        val scanned: Int = 0,
        val total: Int = 0,
        val threats: Int = 0,
        val feed: List<ScanResult> = emptyList(),
        val error: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var job: Job? = null

    fun defaultRoot(): String {
        val dl = File("/storage/emulated/0/Download")
        return if (dl.exists() && dl.canRead()) dl.absolutePath else context.filesDir.absolutePath
    }

    fun scan(root: String = defaultRoot()) {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            _state.value = UiState(running = true, root = root)
            try {
                val results = scanner.scanDirectory(
                    root = root,
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

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }
}
