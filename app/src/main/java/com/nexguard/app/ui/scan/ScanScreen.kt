package com.nexguard.app.ui.scan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nexguard.app.scan.ScanMode
import com.nexguard.app.ui.components.*
import com.nexguard.app.util.Prefs
import com.nexguard.app.util.fileTypeIcon
import com.nexguard.app.util.humanBytes
import com.nexguard.core.common.ScanResult
import com.nexguard.core.common.Severity
import com.nexguard.core.common.ThreatCatalog
import com.nexguard.core.common.Verdict
import kotlinx.coroutines.delay
import java.io.File

private fun hasStorageAccess(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try { Environment.isExternalStorageManager() } catch (e: Exception) { false }
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

private fun openAppDetails(context: Context) {
    try {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
        )
    } catch (e: Exception) { }
}

private enum class FeedFilter { ALL, THREATS, WARNINGS, ISSUES, CLEAN }

private fun sourceLabel(source: String): String = when (source) {
    "download" -> "İndirme izleme"
    "install" -> "Yeni uygulama"
    "app" -> "Uygulama analizi"
    else -> "Manuel tarama"
}

@Composable
fun ScanRoute(onBack: () -> Unit) {
    val vm: ScanViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var storageOk by remember { mutableStateOf(hasStorageAccess(context)) }
    var selected by remember { mutableStateOf<ScanResult?>(null) }
    var confirmDelete by remember { mutableStateOf<ScanResult?>(null) }
    var filter by remember { mutableStateOf(FeedFilter.ALL) }
    var autoStarted by remember { mutableStateOf(false) }
    val preferredMode = remember {
        if (Prefs.defaultMode(context) == "quick") ScanMode.QUICK else ScanMode.FULL
    }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        storageOk = hasStorageAccess(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) storageOk = hasStorageAccess(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(storageOk) {
        if (storageOk && !autoStarted && !state.running && !state.finished &&
            !state.stopped && state.error == null
        ) {
            autoStarted = true
            vm.scan(preferredMode)
        }
    }

    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(4500)
            vm.clearMessage()
        }
    }

    val progress = if (state.total > 0) state.scanned.toFloat() / state.total else 0f
    val filtered = remember(state.feed, filter) {
        when (filter) {
            FeedFilter.ALL -> state.feed
            FeedFilter.THREATS -> state.feed.filter { it.isThreat }
            FeedFilter.WARNINGS -> state.feed.filter { it.isWarning }
            FeedFilter.ISSUES -> state.feed.filter { it.status.isIssue }
            FeedFilter.CLEAN -> state.feed.filter { it.isCleanFile && !it.isWarning }
        }
    }

    fun openStorageSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            } catch (e: Exception) {
                try {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (e2: Exception) { }
            }
        } else {
            try {
                storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            } catch (e: Exception) {
                openAppDetails(context)
            }
        }
    }

    NgBackground {
        Column(Modifier.fillMaxSize()) {
            NgTopBar(
                title = "Dosya Tarama",
                subtitle = "Motor v${vm.engineVersion} · ${vm.detectorCount} dedektör · ${vm.signatureCount} imza",
                onBack = onBack,
                actions = {
                    if (!state.running) {
                        IconButton(
                            onClick = {
                                vm.scan(
                                    if (!state.finished && !state.stopped) preferredMode else state.mode
                                )
                            }
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Yeniden tara")
                        }
                    }
                }
            )

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!storageOk) {
                    item {
                        SectionCard(title = "Depolama erişimi", icon = Icons.Outlined.Folder) {
                            Text(
                                "Tam tarama için tüm dosyalara erişim izni gerekir. İzin vermeden de erişilebilen dosyalar taranabilir.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { openStorageSettings() }, shape = RoundedCornerShape(12.dp)) {
                                    Text("İzni aç")
                                }
                                OutlinedButton(
                                    onClick = { storageOk = hasStorageAccess(context) },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Kontrol et")
                                }
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                                    TextButton(onClick = { openAppDetails(context) }) {
                                        Text("Uygulama ayarları")
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    SectionCard(title = "Tarama durumu", icon = Icons.Outlined.Radar) {
                        if (state.running || state.preparing) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    if (state.total > 0) "%${(progress * 100).toInt()}" else "%0",
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f).padding(bottom = 6.dp)) {
                                    Text(
                                        if (state.preparing) "Dosya listesi hazırlanıyor…" else "Taranan ${state.scanned} / ${state.total}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        buildString {
                                            append("${state.threats} tehdit")
                                            if (state.issues > 0) append(" · ${state.issues} sorun")
                                            append(" · ${state.cached} önbellek")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = when {
                                            state.threats > 0 -> MaterialTheme.colorScheme.error
                                            state.issues > 0 -> Color(0xFFE08A00)
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                            }
                            Spacer(Modifier.height(12.dp))
                            if (state.preparing) {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                LinearProgressIndicator(
                                    progress = progress,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(CircleShape)
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                if (state.current.isNotBlank()) "Şu an: ${state.current}" else "Dosyalar hazırlanıyor…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = { vm.stop() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                Icon(Icons.Outlined.Stop, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("TARAMAYI DURDUR", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            val session = state.session
                            val threatCount = session?.threats ?: state.threats
                            val warningCount = session?.warnings ?: state.warnings
                            val issueCount = session?.errors ?: state.issues
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ScoreRing(
                                    score = session?.score ?: if (state.threats > 0) 40 else 100,
                                    modifier = Modifier.size(104.dp),
                                    stroke = 10.dp,
                                    label = "Skor"
                                )
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        when {
                                            state.error != null -> "Tarama tamamlanamadı"
                                            state.stopped -> "Tarama durduruldu"
                                            state.finished && threatCount > 0 -> "Tehditler bulundu"
                                            state.finished && issueCount > 0 -> "Kısmen tamamlandı"
                                            state.finished && warningCount > 0 -> "Uyarılar var"
                                            state.finished -> "Cihaz temiz"
                                            else -> "Taramaya hazır"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (session != null) {
                                        Text(
                                            "${session.filesScanned} dosya · ${session.cachedFiles} önbellek",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            buildString {
                                                append("${session.threats} tehdit")
                                                if (session.warnings > 0) append(" · ${session.warnings} uyarı")
                                                if (session.errors > 0) append(" · ${session.errors} sorun")
                                                append(" · skor ${session.score}/100")
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = when {
                                                session.threats > 0 -> MaterialTheme.colorScheme.error
                                                session.warnings > 0 || session.errors > 0 -> Color(0xFFE08A00)
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    } else {
                                        Text(
                                            "Tüm depolama ve kurulu uygulamalar taranır. Dosya içeriği cihazdan çıkmaz.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            if (!state.running) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { vm.scan(ScanMode.FULL) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Icon(Icons.Outlined.Radar, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (state.finished || state.stopped) "Tam tara" else "TARA")
                                    }
                                    OutlinedButton(
                                        onClick = { vm.scan(ScanMode.QUICK) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Icon(Icons.Outlined.Bolt, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Hızlı tara")
                                    }
                                }
                            }
                            if (state.error != null) {
                                Spacer(Modifier.height(10.dp))
                                WarningBanner(state.error ?: "")
                            }
                        }
                    }
                }

                state.message?.let { msg ->
                    item {
                        InfoBanner(
                            text = msg,
                            icon = Icons.Outlined.CheckCircle,
                            container = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                            contentColor = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Canlı akış",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(4.dp))
                        FilterChip(
                            selected = filter == FeedFilter.ALL,
                            onClick = { filter = FeedFilter.ALL },
                            label = { Text("Tümü ${state.feed.size}") }
                        )
                        FilterChip(
                            selected = filter == FeedFilter.THREATS,
                            onClick = { filter = FeedFilter.THREATS },
                            label = { Text("Tehditler ${state.feed.count { it.isThreat }}") }
                        )
                        FilterChip(
                            selected = filter == FeedFilter.WARNINGS,
                            onClick = { filter = FeedFilter.WARNINGS },
                            label = { Text("Uyarılar ${state.feed.count { it.isWarning }}") }
                        )
                        FilterChip(
                            selected = filter == FeedFilter.ISSUES,
                            onClick = { filter = FeedFilter.ISSUES },
                            label = { Text("Sorunlar ${state.feed.count { it.status.isIssue }}") }
                        )
                        FilterChip(
                            selected = filter == FeedFilter.CLEAN,
                            onClick = { filter = FeedFilter.CLEAN },
                            label = { Text("Temiz ${state.feed.count { it.isCleanFile && !it.isWarning }}") }
                        )
                    }
                }

                if (filtered.isEmpty()) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (state.running || state.preparing) {
                                    CircularProgressIndicator(modifier = Modifier.size(30.dp), strokeWidth = 3.dp)
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        "Dosyalar taranıyor…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Icon(
                                        Icons.Outlined.FolderOpen,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Bu filtrede kayıt yok",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(filtered, key = { it.id }) { r ->
                        FeedRow(
                            result = r,
                            handled = r.targetPath in state.handledPaths,
                            onClick = { selected = r }
                        )
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    selected?.let { r ->
        val type = when {
            r.isThreat -> ThreatCatalog.threatType(r.findings)
            r.status.isIssue -> ThreatCatalog.statusLabel(r.status)
            else -> "Temiz"
        }
        val file = File(r.targetPath)
        val canModify = file.isFile && file.canRead() && file.parentFile?.canWrite() != false
        ScanDetailDialog(
            result = r,
            threatType = type,
            handled = r.targetPath in state.handledPaths,
            canModify = canModify,
            onQuarantine = { vm.quarantine(r); selected = null },
            onDelete = { selected = null; confirmDelete = r },
            onDismiss = { selected = null }
        )
    }

    confirmDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            icon = { Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Kalıcı olarak silinsin mi?") },
            text = {
                Text(
                    "${File(r.targetPath).name} geri alınamaz şekilde silinecek. " +
                        "Dosya bir tehdit değilse önce yedeklemeyi düşünün."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.deleteFile(r); confirmDelete = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Kalıcı sil") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Vazgeç") }
            }
        )
    }
}

@Composable
private fun FeedRow(result: ScanResult, handled: Boolean, onClick: () -> Unit) {
    val file = File(result.targetPath)
    val color = resultColor(result)
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (result.isCleanFile && !result.isWarning) MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
            else color.copy(alpha = 0.28f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(color.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    fileTypeIcon(file.extension),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    file.name.ifBlank { result.targetPath },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(
                        file.parentFile?.name,
                        humanBytes(result.sizeBytes),
                        if (result.cached) "önbellek" else null,
                        if (handled) "işlem yapıldı" else null
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                ResultVerdictPill(result)
                Spacer(Modifier.height(3.dp))
                Text(
                    if (result.status.isIssue || result.isWarning) "—" else "${result.score}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }
    }
}

@Composable
private fun ScanDetailDialog(
    result: ScanResult,
    threatType: String,
    handled: Boolean,
    canModify: Boolean,
    onQuarantine: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val file = File(result.targetPath)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    file.name.ifBlank { "Dosya" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ResultVerdictPill(result)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            result.status.isIssue -> threatType
                            result.isWarning -> "Tehdit değil · inceleme önerilir"
                            else -> "$threatType · ${result.score}/100"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = resultColor(result),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        text = {
            Column(
                Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    result.targetPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                InfoRow("Tür", if (result.isWarning) "Uyarı (tehdit değil)" else threatType)
                InfoRow("Boyut", humanBytes(result.sizeBytes))
                InfoRow("Süre", "${result.durationMs} ms")
                InfoRow("Kaynak", sourceLabel(result.source))
                if (result.sha256.isNotBlank()) {
                    InfoRow("SHA-256", result.sha256.take(32) + if (result.sha256.length > 32) "…" else "", mono = true)
                }
                if (result.cached) {
                    InfoRow("Önbellek", "Değişmemiş dosya; yeniden okunmadı")
                }
                if (result.status.isIssue) {
                    Spacer(Modifier.height(8.dp))
                    InfoBanner(
                        text = result.error ?: ThreatCatalog.statusAdvice(result.status),
                        icon = Icons.Outlined.Info,
                        container = scanStatusColor(result.status).copy(alpha = 0.10f),
                        contentColor = scanStatusColor(result.status)
                    )
                }
                if (handled) {
                    Spacer(Modifier.height(8.dp))
                    InfoBanner(
                        "Bu dosya için işlem yapıldı.",
                        icon = Icons.Outlined.CheckCircle,
                        container = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.tertiary
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (result.isThreat) {
                    InfoBanner(
                        text = ThreatCatalog.advice(result.findings),
                        icon = Icons.Outlined.Warning,
                        container = verdictColor(result.verdict).copy(alpha = 0.10f),
                        contentColor = verdictColor(result.verdict)
                    )
                    Spacer(Modifier.height(12.dp))
                } else if (result.isWarning) {
                    val isPython = file.extension.lowercase() in setOf("py", "pyw", "ipynb")
                    InfoBanner(
                        text = buildString {
                            if (isPython) {
                                append("Python betiği: geliştirici dosyası olarak değerlendirildi, tehdit sayılmadı. ")
                            } else {
                                append("Bu dosya tehdit eşiğinde değil; bulgular yalnızca uyarı niteliğinde. ")
                            }
                            append(ThreatCatalog.advice(result.findings))
                        },
                        icon = Icons.Outlined.Info,
                        container = Color(0xFFE08A00).copy(alpha = 0.12f),
                        contentColor = Color(0xFFE08A00)
                    )
                    Spacer(Modifier.height(12.dp))
                }

                if (result.findings.isEmpty()) {
                    Text(
                        when {
                            result.isCleanFile -> "Tehdit bulgusu yok. Dosya temiz."
                            result.status.isIssue -> "Bu dosya için güvenilir sonuç üretilemedi."
                            else -> "Bulgular kaydedilmedi."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        if (result.isWarning) "Uyarılar (${result.findings.size})"
                        else "Tespitler (${result.findings.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    result.findings.forEach { f ->
                        val info = ThreatCatalog.info(f.ruleId)
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    info.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                SeverityPill(f.severity)
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
                            if (f.evidence.isNotEmpty()) {
                                f.evidence.entries.take(5).forEach { (k, v) ->
                                    Text(
                                        "$k: $v",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Divider(Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                        }
                    }
                }

                if (!canModify && result.isThreat) {
                    Spacer(Modifier.height(8.dp))
                    WarningBanner("Bu dosya salt okunur bir konumda (ör. sistem uygulaması). Karantina uygulanamaz.")
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (result.isThreat && !handled && canModify) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Kalıcı sil") }
                    TextButton(onClick = onQuarantine) { Text("Karantinaya al") }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Kapat") }
        }
    )
}
