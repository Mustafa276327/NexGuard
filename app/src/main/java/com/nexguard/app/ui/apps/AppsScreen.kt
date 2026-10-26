package com.nexguard.app.ui.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexguard.app.ui.components.*
import com.nexguard.core.common.ScanResult
import com.nexguard.core.common.ThreatCatalog
import com.nexguard.core.common.Verdict
import com.nexguard.feature.scan.Scanner
import com.nexguard.feature.scan.detection.ScanOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private enum class AppFilter { ALL, USER, RISKY }

data class AppRow(
    val label: String,
    val packageName: String,
    val version: String,
    val targetSdk: Int,
    val isSystem: Boolean,
    val dangerousPerms: List<String>,
    val permissions: List<String>,
    val installer: String?,
    val debuggable: Boolean,
    val sourceDir: String,
    val icon: ImageBitmap?,
    val riskScore: Int,
    val riskReasons: List<String>
)

@HiltViewModel
class AppsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanner: Scanner
) : ViewModel() {

    data class UiState(
        val apps: List<AppRow> = emptyList(),
        val loading: Boolean = true,
        val selected: AppRow? = null,
        val scanning: Boolean = false,
        val scanResult: ScanResult? = null,
        val scanError: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(loading = true) }
            val pm = context.packageManager
            val list = try {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(PackageManager.GET_PERMISSIONS).mapNotNull { pi ->
                    val ai = pi.applicationInfo ?: return@mapNotNull null
                    val label = try { pm.getApplicationLabel(ai).toString() } catch (e: Exception) { pi.packageName }
                    val requested = pi.requestedPermissions ?: emptyArray()
                    val dangerous = requested.filter { isDangerous(pm, it) }
                    val installer = try {
                        if (Build.VERSION.SDK_INT >= 30) {
                            pm.getInstallSourceInfo(pi.packageName).installingPackageName
                        } else {
                            @Suppress("DEPRECATION")
                            pm.getInstallerPackageName(pi.packageName)
                        }
                    } catch (e: Exception) { null }
                    val icon = try {
                        pm.getApplicationIcon(ai).toBitmap(64, 64).asImageBitmap()
                    } catch (e: Exception) { null }
                    val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    // NexGuard kendi paketini riskli göstermez; güçlü izinleri işlevi gereğidir.
                    val isSelf = pi.packageName == context.packageName
                    val debuggable = (ai.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
                    val reasons = mutableListOf<String>()
                    var risk = 0
                    if (!isSystem && !isSelf) {
                        risk += (dangerous.size * 5).coerceAtMost(40)
                        if (dangerous.any { it.contains("SMS") }) {
                            risk += 18
                            reasons += "SMS izinleri"
                        }
                        if (requested.any { it.endsWith("REQUEST_INSTALL_PACKAGES") }) {
                            risk += 8
                            reasons += "Paket kurma izni"
                        }
                        if (requested.any { it.endsWith("SYSTEM_ALERT_WINDOW") }) {
                            risk += 8
                            reasons += "Ekran üstü çizim"
                        }
                        if (dangerous.size >= 6) reasons += "${dangerous.size} tehlikeli izin"
                        if (ai.targetSdkVersion < 29) {
                            risk += 15
                            reasons += "Eski Android hedefi (SDK ${ai.targetSdkVersion})"
                        }
                        if (debuggable) {
                            risk += 15
                            reasons += "Debug yapısı"
                        }
                        if (installer == null) {
                            risk += 8
                            reasons += "Bilinmeyen kaynak (sideload)"
                        }
                    }
                    if (!isSelf && !isSystem && reasons.isEmpty() && dangerous.isNotEmpty()) {
                        reasons += "${dangerous.size} hassas izin"
                    }
                    AppRow(
                        label = label,
                        packageName = pi.packageName,
                        version = pi.versionName ?: "—",
                        targetSdk = ai.targetSdkVersion,
                        isSystem = isSystem,
                        dangerousPerms = dangerous,
                        permissions = requested.sorted(),
                        installer = installer,
                        debuggable = debuggable,
                        sourceDir = ai.sourceDir ?: "",
                        icon = icon,
                        riskScore = risk.coerceIn(0, 100),
                        riskReasons = reasons
                    )
                }.sortedWith(compareByDescending<AppRow> { it.riskScore }.thenBy { it.label.lowercase() })
            } catch (e: Exception) {
                emptyList()
            }
            _state.update { it.copy(apps = list, loading = false) }
        }
    }

    fun open(app: AppRow) {
        _state.update { it.copy(selected = app, scanResult = null, scanError = null) }
    }

    fun close() {
        _state.update { it.copy(selected = null, scanResult = null, scanError = null) }
    }

    fun scanSelected() {
        val app = _state.value.selected ?: return
        if (_state.value.scanning) return
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(scanning = true, scanError = null, scanResult = null) }
            try {
                val result = scanner.scanFile(
                    app.sourceDir,
                    ScanOptions(
                        source = "app",
                        deep = true,
                        useCache = false,
                        mode = "app",
                        label = "Uygulama analizi"
                    )
                )
                _state.update { it.copy(scanning = false, scanResult = result) }
            } catch (e: Exception) {
                _state.update { it.copy(scanning = false, scanError = e.message ?: "APK taranamadı") }
            }
        }
    }

    private fun isDangerous(pm: PackageManager, perm: String): Boolean = try {
        val level = pm.getPermissionInfo(perm, 0).protectionLevel
        (level and 0xF) == PermissionInfo.PROTECTION_DANGEROUS
    } catch (e: Exception) {
        false
    }
}

private fun riskColor(score: Int): Color = when {
    score >= 60 -> Color(0xFFC62828)
    score >= 30 -> Color(0xFFE08A00)
    else -> Color(0xFF2E7D32)
}

private fun riskLabel(score: Int): String = when {
    score >= 60 -> "YÜKSEK RİSK"
    score >= 30 -> "ORTA RİSK"
    else -> "DÜŞÜK RİSK"
}

private fun installerLabel(installer: String?): String = when (installer) {
    null -> "Bilinmeyen kaynak"
    "com.android.vending" -> "Google Play"
    "com.sec.android.app.samsungapps" -> "Samsung Galaxy Store"
    "com.huawei.appmarket" -> "Huawei AppGallery"
    "com.amazon.venezia" -> "Amazon Appstore"
    else -> installer ?: "Bilinmeyen kaynak"
}

@Composable
fun AppsRoute(onBack: () -> Unit) {
    val vm: AppsViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(AppFilter.ALL) }
    var confirmUninstall by remember { mutableStateOf<AppRow?>(null) }

    val userApps = remember(state.apps) { state.apps.count { !it.isSystem } }
    val risky = remember(state.apps) { state.apps.count { it.riskScore >= 30 && !it.isSystem } }
    val filtered = remember(state.apps, query, filter) {
        state.apps.filter { app ->
            val matchesQuery = query.isBlank() ||
                app.label.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true)
            val matchesFilter = when (filter) {
                AppFilter.ALL -> true
                AppFilter.USER -> !app.isSystem
                AppFilter.RISKY -> !app.isSystem && app.riskScore >= 30
            }
            matchesQuery && matchesFilter
        }
    }

    NgBackground {
        Column(Modifier.fillMaxSize()) {
            NgTopBar(
                title = "Uygulamalar",
                subtitle = "$userApps kullanıcı uygulaması · $risky riskli",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { vm.load() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Yenile")
                    }
                }
            )

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Uygulama veya paket ara") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotBlank()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Temizle")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = filter == AppFilter.ALL,
                            onClick = { filter = AppFilter.ALL },
                            label = { Text("Tümü ${state.apps.size}") }
                        )
                        FilterChip(
                            selected = filter == AppFilter.USER,
                            onClick = { filter = AppFilter.USER },
                            label = { Text("Kullanıcı $userApps") }
                        )
                        FilterChip(
                            selected = filter == AppFilter.RISKY,
                            onClick = { filter = AppFilter.RISKY },
                            label = { Text("Riskli $risky") }
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
                } else if (filtered.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.Apps,
                            title = "Uygulama bulunamadı",
                            text = if (query.isBlank()) "Cihazda listelenecek uygulama yok."
                            else "Arama kriterine uyan uygulama yok."
                        )
                    }
                } else {
                    items(filtered, key = { it.packageName }) { app ->
                        AppCard(app) { vm.open(app) }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    state.selected?.let { app ->
        AppDetailDialog(
            app = app,
            scanning = state.scanning,
            result = state.scanResult,
            scanError = state.scanError,
            onScan = { vm.scanSelected() },
            onOpenInfo = {
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                    )
                } catch (e: Exception) { }
            },
            onUninstall = { confirmUninstall = app },
            onDismiss = { vm.close() }
        )
    }

    confirmUninstall?.let { app ->
        AlertDialog(
            onDismissRequest = { confirmUninstall = null },
            icon = { Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Uygulama kaldırılsın mı?") },
            text = { Text("${app.label} cihazdan kaldırılacak. Sistem onay ekranı açılacak.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
                        } catch (e: Exception) { }
                        confirmUninstall = null
                        vm.close()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Kaldır") }
            },
            dismissButton = {
                TextButton(onClick = { confirmUninstall = null }) { Text("Vazgeç") }
            }
        )
    }
}

@Composable
private fun AppCard(app: AppRow, onClick: () -> Unit) {
    val color = riskColor(app.riskScore)
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (app.riskScore >= 30) color.copy(alpha = 0.25f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (app.icon != null) {
                Image(
                    bitmap = app.icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        app.label.take(1).uppercase(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        app.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (app.isSystem) {
                        Spacer(Modifier.width(6.dp))
                        StatusPill("SİSTEM", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    "${app.packageName} · v${app.version}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (app.riskReasons.isNotEmpty() && !app.isSystem) {
                    Text(
                        app.riskReasons.take(2).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (!app.isSystem) {
                    StatusPill(riskLabel(app.riskScore), color.copy(alpha = 0.12f), color)
                    Spacer(Modifier.height(3.dp))
                    Text("${app.riskScore}/100", style = MaterialTheme.typography.labelSmall, color = color)
                } else {
                    Text("—", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
private fun AppDetailDialog(
    app: AppRow,
    scanning: Boolean,
    result: ScanResult?,
    scanError: String?,
    onScan: () -> Unit,
    onOpenInfo: () -> Unit,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit
) {
    val color = riskColor(app.riskScore)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (app.icon != null) {
                    Image(
                        bitmap = app.icon,
                        contentDescription = null,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        app.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(if (app.isSystem) "SİSTEM UYGULAMASI" else riskLabel(app.riskScore),
                        if (app.isSystem) MaterialTheme.colorScheme.surfaceVariant else color.copy(alpha = 0.12f),
                        if (app.isSystem) MaterialTheme.colorScheme.onSurfaceVariant else color)
                    Spacer(Modifier.weight(1f))
                    if (!app.isSystem) Text("${app.riskScore}/100", fontWeight = FontWeight.Bold, color = color)
                }
                Spacer(Modifier.height(10.dp))
                InfoRow("Sürüm", app.version)
                InfoRow("Hedef SDK", "${app.targetSdk}")
                InfoRow("Kaynak", installerLabel(app.installer))
                InfoRow("Tehlikeli izin", "${app.dangerousPerms.size}")
                if (app.debuggable) InfoRow("İmza", "Debug yapısı")
                if (app.riskReasons.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    InfoBanner(
                        app.riskReasons.joinToString(" · "),
                        icon = Icons.Outlined.Warning,
                        container = color.copy(alpha = 0.10f),
                        contentColor = color
                    )
                }

                if (app.dangerousPerms.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Hassas izinler", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    app.dangerousPerms.take(20).forEach { perm ->
                        Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                perm.removePrefix("android.permission.").replace('_', ' ').lowercase()
                                    .replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("APK analizi", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                if (scanning) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("APK derinlemesine taranıyor…", style = MaterialTheme.typography.bodySmall)
                    }
                } else if (result != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (result.status.isIssue) ScanStatusPill(result.status) else VerdictPill(result.verdict)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                result.status.isIssue -> result.error ?: "Analiz tamamlanamadı"
                                result.isCleanFile -> "Tehdit yok · ${result.score}/100"
                                else -> "${ThreatCatalog.threatType(result.findings)} · ${result.score}/100"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = when {
                                result.status.isIssue -> Color(0xFFE08A00)
                                result.isCleanFile -> Color(0xFF2E7D32)
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                    }
                    if (result.findings.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        result.findings.take(8).forEach { f ->
                            Text(
                                "• ${ThreatCatalog.info(f.ruleId).title}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Text(
                        if (scanError != null) scanError
                        else "Kurulu APK'nın izin, imza ve DEX içeriğini derinlemesine analiz edin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (scanError != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!app.isSystem) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(
                        onClick = onUninstall,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Outlined.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Uygulamayı kaldır")
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onOpenInfo) { Text("Bilgi") }
                TextButton(onClick = onScan, enabled = !scanning) {
                    Text(if (result == null) "APK'yı tara" else "Yeniden tara")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Kapat") }
        }
    )
}
