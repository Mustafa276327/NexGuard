package com.nexguard.app.ui.scan

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexguard.app.scan.ScanController
import com.nexguard.app.scan.ScanMode
import com.nexguard.app.scan.ScanUiState
import com.nexguard.app.util.NgLog
import com.nexguard.app.util.Prefs
import com.nexguard.core.common.ScanResult
import com.nexguard.core.common.ThreatCatalog
import com.nexguard.core.common.Verdict
import com.nexguard.feature.quarantine.QuarantineManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val controller: ScanController,
    private val quarantineManager: QuarantineManager
) : ViewModel() {

    val state: StateFlow<ScanUiState> = controller.state
    val engineVersion: Int get() = controller.engineVersion
    val detectorCount: Int get() = controller.detectorCount
    val signatureCount: Int get() = controller.signatureCount

    fun scan(mode: ScanMode = ScanMode.FULL) = controller.scan(mode)
    fun stop() = controller.stop()
    fun clearMessage() = controller.clearMessage()

    fun quarantine(r: ScanResult) {
        viewModelScope.launch(Dispatchers.IO) {
            val type = if (r.verdict == Verdict.CLEAN) "" else ThreatCatalog.threatType(r.findings)
            val outcome = quarantineManager.quarantine(
                r.targetPath,
                "Taramadan karantina (${r.score})",
                type
            )
            val name = File(r.targetPath).name
            val text = when {
                !outcome.success -> "Karantina başarısız: ${outcome.message}"
                outcome.sourceDeleteFailed ->
                    "Karantinaya alındı ancak kaynak silinemedi: $name"
                else -> "Karantinaya alındı: $name"
            }
            when {
                !outcome.success -> NgLog.e("Karantina", "Karantina başarısız: ${outcome.message}")
                outcome.sourceDeleteFailed ->
                    NgLog.w("Karantina", "Karantina oluştu ancak kaynak silinemedi: $name")
                else -> NgLog.i("Karantina", "Dosya karantinaya alındı: $name")
            }
            controller.updateState {
                it.copy(
                    handledPaths = if (outcome.success && !outcome.sourceDeleteFailed) {
                        it.handledPaths + r.targetPath
                    } else {
                        it.handledPaths
                    },
                    message = text
                )
            }
        }
    }

    fun deleteFile(r: ScanResult) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = quarantineManager.deleteFile(r.targetPath)
            val name = File(r.targetPath).name
            if (ok) NgLog.i("Karantina", "Dosya kalıcı olarak silindi: $name")
            else NgLog.w("Karantina", "Dosya silinemedi: $name")
            controller.updateState {
                it.copy(
                    handledPaths = if (ok) it.handledPaths + r.targetPath else it.handledPaths,
                    message = if (ok) "Kalıcı olarak silindi: $name"
                    else "Dosya silinemedi (salt okunur veya erişim yok)"
                )
            }
        }
    }
}
