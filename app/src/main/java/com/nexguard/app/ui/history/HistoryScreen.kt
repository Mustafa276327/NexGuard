package com.nexguard.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexguard.app.ui.components.*
import com.nexguard.app.util.fileTypeIcon
import com.nexguard.app.util.formatDateTime
import com.nexguard.app.util.humanBytes
import com.nexguard.core.common.ThreatCatalog
import com.nexguard.core.common.Verdict
import com.nexguard.core.database.AppDatabase
import com.nexguard.core.database.FindingEntity
import com.nexguard.core.database.ScanResultEntity
import com.nexguard.core.database.ScanSessionEntity
import com.nexguard.feature.alerts.AlertManager
import com.nexguard.feature.quarantine.QuarantineManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

private enum class HistoryTab { SESSIONS, THREATS }

private fun verdictOf(entity: ScanResultEntity): Verdict =
    try { Verdict.valueOf(entity.verdict) } catch (e: Exception) { Verdict.SUSPICIOUS }

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val db: AppDatabase,
    private val quarantineManager: QuarantineManager,
    private val alertManager: AlertManager
) : ViewModel() {

    data class UiState(
        val sessions: List<ScanSessionEntity> = emptyList(),
        val threats: List<ScanResultEntity> = emptyList(),
        val loading: Boolean = true,
        val selected: ScanResultEntity? = null,
        val findings: List<FindingEntity> = emptyList(),
        val selectedSession: ScanSessionEntity? = null,
        val message: String? = null,
        val busy: Boolean = false
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(loading = true) }
            val sessions = try { db.scanSessionDao().recent(30) } catch (e: Exception) { emptyList() }
            val threats = try { db.scanResultDao().recent(300) } catch (e: Exception) { emptyList() }
                .filter { it.verdict != "CLEAN" }
            _state.update { it.copy(sessions = sessions, threats = threats, loading = false) }
        }
    }

    fun open(entity: ScanResultEntity) {
        _state.update { it.copy(selected = entity, findings = emptyList()) }
        viewModelScope.launch(Dispatchers.IO) {
            val findings = try { db.findingDao().forScan(entity.id) } catch (e: Exception) { emptyList() }
            _state.update { if (it.selected?.id == entity.id) it.copy(findings = findings) else it }
        }
    }

    fun close() {
        _state.update { it.copy(selected = null, findings = emptyList()) }
    }

    fun openSession(session: ScanSessionEntity) {
        _state.update { it.copy(selectedSession = session) }
    }

    fun closeSession() {
        _state.update { it.copy(selectedSession = null) }
    }

    fun quarantine(entity: ScanResultEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(busy = true) }
            val outcome = quarantineManager.quarantine(
                entity.targetPath,
                "Geçmişten karantina (${entity.score})",
                entity.threatType
            )
            if (outcome.success && !outcome.sourceDeleteFailed) alertManager.cancelThreat(entity.targetPath)
            _state.update {
                it.copy(
                    busy = false,
                    selected = null,
                    findings = emptyList(),
                    message = when {
                        !outcome.success -> "Karantina başarısız: ${outcome.message}"
                        outcome.sourceDeleteFailed ->
                            "Karantinaya alındı ancak kaynak dosya silinemedi: ${File(entity.targetPath).name}"
                        else -> "Karantinaya alındı: ${File(entity.targetPath).name}"
                    }
                )
            }
        }
    }

    fun deleteRecord(entity: ScanResultEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                db.findingDao().deleteForScan(entity.id)
                db.scanResultDao().delete(entity.id)
                _state.update {
                    it.copy(
                        selected = null,
                        findings = emptyList(),
                        message = "Kayıt silindi: ${File(entity.targetPath).name}"
                    )
                }
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(message = "Kayıt silinemedi") }
            }
        }
    }

    fun clearAll() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                db.findingDao().clear()
                db.scanResultDao().clear()
                db.scanSessionDao().clear()
                db.timelineDao().clear()
                _state.update { it.copy(message = "Tarama geçmişi temizlendi") }
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(message = "Geçmiş temizlenemedi") }
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }
}

@Composable
fun HistoryRoute(onBack: () -> Unit) {
    val vm: HistoryViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    var tab by remember { mutableStateOf(HistoryTab.SESSIONS) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<ScanResultEntity?>(null) }

    val threatCount = remember(state.threats) {
        state.threats.count { it.verdict == "DANGEROUS" }
    }

    NgBackground {
        Column(Modifier.fillMaxSize()) {
            NgTopBar(
                title = "Tarama Geçmişi",
                subtitle = "${state.sessions.size} oturum · ${state.threats.size} tehdit kaydı",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Yenile")
                    }
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = "Geçmişi temizle")
                    }
                }
            )

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                state.message?.let { msg ->
                    item {
                        InfoBanner(
                            msg,
                            icon = Icons.Outlined.CheckCircle,
                            container = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                            contentColor = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = tab == HistoryTab.SESSIONS,
                            onClick = { tab = HistoryTab.SESSIONS },
                            label = { Text("Oturumlar ${state.sessions.size}") }
                        )
                        FilterChip(
                            selected = tab == HistoryTab.THREATS,
                            onClick = { tab = HistoryTab.THREATS },
                            label = { Text("Tehditler ${state.threats.size}") }
                        )
                    }
                }

                if (state.loading) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 60.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }
                    }
                } else if (tab == HistoryTab.SESSIONS) {
                    if (state.sessions.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Outlined.History,
                                title = "Henüz tarama yok",
                                text = "İlk taramanı başlatarak cihazının güvenlik geçmişini oluştur.",
                                actionLabel = "Geri dön",
                                onAction = onBack
                            )
                        }
                    } else {
                        items(state.sessions, key = { it.id }) { session ->
                            SessionCard(session) { vm.openSession(session) }
                        }
                    }
                } else {
                    if (state.threats.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Outlined.VerifiedUser,
                                title = "Tehdit kaydı yok",
                                text = "Yapılan taramalarda tehdit bulunmadı. Bu iyi bir işaret."
                            )
                        }
                    } else {
                        item {
                            InfoBanner(
                                text = "${state.threats.size} tehdit kaydı var; ${threatCount} tanesi tehlikeli. " +
                                    "Dosya hâlâ cihazdaysa karantinaya alabilirsiniz.",
                                icon = Icons.Outlined.Warning,
                                container = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        items(state.threats, key = { it.id }) { entity ->
                            ThreatHistoryCard(entity) { vm.open(entity) }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    state.selected?.let { entity ->
        ThreatHistoryDialog(
            entity = entity,
            findings = state.findings,
            busy = state.busy,
            onQuarantine = { vm.quarantine(entity) },
            onDeleteRecord = { confirmDelete = entity },
            onDismiss = { vm.close() }
        )
    }

    state.selectedSession?.let { session ->
        SessionDetailDialog(session = session, onDismiss = { vm.closeSession() })
    }

    confirmDelete?.let { entity ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Kayıt silinsin mi?") },
            text = { Text("${File(entity.targetPath).name} tarama kaydı silinecek. Dosyanın kendisi silinmez.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteRecord(entity)
                    confirmDelete = null
                }) { Text("Kaydı sil") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Vazgeç") }
            }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { Icon(Icons.Outlined.DeleteSweep, contentDescription = null) },
            title = { Text("Geçmiş temizlensin mi?") },
            text = {
                Text(
                    "Tüm tarama oturumları, tehdit kayıtları ve zaman çizelgesi silinir. " +
                        "Karantinadaki dosyalar ve uygulama ayarları korunur."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.clearAll(); confirmClear = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Temizle") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Vazgeç") }
            }
        )
    }
}

@Composable
private fun SessionCard(session: ScanSessionEntity, onClick: () -> Unit) {
    val statusLabel = when (session.status) {
        "completed" -> "Tamamlandı"
        "stopped" -> "Durduruldu"
        "failed" -> "Hata"
        else -> "Sürüyor"
    }
    val statusColor = when (session.status) {
        "completed" -> Color(0xFF2E7D32)
        "stopped" -> Color(0xFFE08A00)
        "failed" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (session.mode == "quick") Icons.Outlined.Bolt else Icons.Outlined.Radar,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        session.label.ifBlank { if (session.mode == "quick") "Hızlı tarama" else "Tam tarama" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        formatDateTime(session.startedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(statusLabel, statusColor.copy(alpha = 0.12f), statusColor)
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SessionMetric("Dosya", "${session.filesScanned}")
                SessionMetric("Tehdit", "${session.threats}", if (session.threats > 0) MaterialTheme.colorScheme.error else null)
                SessionMetric("Önbellek", "${session.cachedFiles}")
                SessionMetric("Skor", "${session.score}")
            }
        }
    }
}

@Composable
private fun SessionMetric(label: String, value: String, color: Color? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color ?: MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ThreatHistoryCard(entity: ScanResultEntity, onClick: () -> Unit) {
    val verdict = verdictOf(entity)
    val color = verdictColor(verdict)
    val file = File(entity.targetPath)
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    fileTypeIcon(file.extension),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(21.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    file.name.ifBlank { entity.targetPath },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${entity.threatType.ifBlank { "Şüpheli dosya" }} · ${formatDateTime(entity.startedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                VerdictPill(verdict)
                Spacer(Modifier.height(3.dp))
                Text("${entity.score}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
            }
        }
    }
}

@Composable
private fun ThreatHistoryDialog(
    entity: ScanResultEntity,
    findings: List<FindingEntity>,
    busy: Boolean,
    onQuarantine: () -> Unit,
    onDeleteRecord: () -> Unit,
    onDismiss: () -> Unit
) {
    val verdict = verdictOf(entity)
    val file = File(entity.targetPath)
    val exists = file.isFile
    val canModify = exists && file.canRead() && file.parentFile?.canWrite() != false
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(file.name.ifBlank { "Dosya" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VerdictPill(verdict)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${entity.threatType.ifBlank { "Şüpheli dosya" }} · ${entity.score}/100",
                        style = MaterialTheme.typography.labelMedium,
                        color = verdictColor(verdict),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        text = {
            Column(
                Modifier
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    entity.targetPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                InfoRow("Tür", entity.threatType.ifBlank { "Şüpheli dosya" })
                InfoRow("Tarih", formatDateTime(entity.startedAt))
                InfoRow("Boyut", humanBytes(entity.sizeBytes))
                InfoRow("Skor", "${entity.score}/100")
                if (entity.sha256.isNotBlank()) {
                    InfoRow("SHA-256", entity.sha256.take(30) + "…", mono = true)
                }
                Spacer(Modifier.height(8.dp))
                if (!exists) {
                    WarningBanner("Dosya artık bu konumda değil. Karantinaya alınmış veya taşınmış olabilir.")
                } else if (!canModify) {
                    WarningBanner("Dosya salt okunur bir konumda; karantina uygulanamaz.")
                }
                Spacer(Modifier.height(10.dp))

                if (findings.isEmpty()) {
                    Text("Bu kayıt için ayrıntılı bulgu saklanmamış.", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("Tespitler (${findings.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    findings.forEach { f ->
                        val info = ThreatCatalog.info(f.ruleId)
                        val severity = try { com.nexguard.core.common.Severity.valueOf(f.severity) }
                        catch (e: Exception) { com.nexguard.core.common.Severity.INFO }
                        Column(Modifier.padding(vertical = 7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    info.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                SeverityPill(severity)
                            }
                            Text(
                                info.advice,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Dedektör: ${f.detector} · ağırlık ${f.weight}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (canModify) {
                    TextButton(onClick = onQuarantine, enabled = !busy) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Karantinaya al")
                        }
                    }
                }
                TextButton(
                    onClick = onDeleteRecord,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Kaydı sil") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Kapat") }
        }
    )
}

@Composable
private fun SessionDetailDialog(session: ScanSessionEntity, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(session.label.ifBlank { "Tarama oturumu" }) },
        text = {
            Column {
                InfoRow("Başlangıç", formatDateTime(session.startedAt))
                session.finishedAt?.let { InfoRow("Bitiş", formatDateTime(it)) }
                val seconds = session.finishedAt?.let { (it - session.startedAt) / 1000 } ?: 0
                if (seconds > 0) InfoRow("Süre", if (seconds < 60) "$seconds sn" else "${seconds / 60} dk ${seconds % 60} sn")
                InfoRow("Taranan dosya", "${session.filesScanned}")
                InfoRow("Tehdit", "${session.threats}")
                InfoRow("Önbellekten", "${session.cachedFiles}")
                InfoRow("Güvenlik skoru", "${session.score}/100")
                InfoRow("Mod", if (session.mode == "quick") "Hızlı tarama" else "Tam tarama")
                InfoRow("Durum", when (session.status) {
                    "completed" -> "Tamamlandı"
                    "stopped" -> "Durduruldu"
                    "failed" -> "Hata"
                    else -> "Sürüyor"
                })
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Kapat") }
        }
    )
}
