package com.nexguard.app.ui.settings

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexguard.app.admin.DeviceAdminReceiver
import com.nexguard.app.service.MonitorService
import com.nexguard.app.ui.components.*
import com.nexguard.app.util.NgLog
import com.nexguard.app.util.Prefs
import com.nexguard.feature.onboarding.PermissionManager
import com.nexguard.feature.scan.Scanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val scanner: Scanner
) : ViewModel() {
    data class Info(
        val cacheCount: Int = 0,
        val clearing: Boolean = false
    )

    private val _info = MutableStateFlow(Info())
    val info: StateFlow<Info> = _info.asStateFlow()

    val engineVersion: Int get() = scanner.engineVersion
    val detectorCount: Int get() = scanner.detectorCount
    val signatureCount: Int get() = scanner.signatureCount

    init { refreshCache() }

    fun refreshCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val count = try { scanner.cacheCount() } catch (e: Exception) { 0 }
            _info.update { it.copy(cacheCount = count) }
        }
    }

    fun clearCache(onDone: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _info.update { it.copy(clearing = true) }
            try {
                scanner.clearCache()
                _info.update { it.copy(clearing = false, cacheCount = 0) }
                onDone("Tarama önbelleği temizlendi")
            } catch (e: Exception) {
                _info.update { it.copy(clearing = false) }
                onDone("Önbellek temizlenemedi")
            }
        }
    }
}

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenLog: () -> Unit = {},
    onOpenSites: () -> Unit = {},
    onOpenHistory: () -> Unit = {}
) {
    val context = LocalContext.current
    val vm: SettingsViewModel = hiltViewModel()
    val info by vm.info.collectAsState()
    val prefs = remember { Prefs.get(context) }
    val dpm = remember { context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager }
    val admin = remember { ComponentName(context, DeviceAdminReceiver::class.java) }
    val lifecycleOwner = LocalLifecycleOwner.current

    var refreshKey by remember { mutableIntStateOf(0) }
    var monitorOn by remember { mutableStateOf(Prefs.monitorOn(context)) }
    var autoQuarantine by remember { mutableStateOf(Prefs.autoQuarantine(context)) }
    var notifyThreats by remember { mutableStateOf(Prefs.notifyThreats(context)) }
    var useCache by remember { mutableStateOf(Prefs.useCache(context)) }
    var cloudLookup by remember { mutableStateOf(Prefs.cloudLookup(context)) }
    var scanApps by remember { mutableStateOf(Prefs.scanInstalledApps(context)) }
    var defaultMode by remember { mutableStateOf(Prefs.defaultMode(context)) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmClearCache by remember { mutableStateOf(false) }
    var confirmRemoveAdmin by remember { mutableStateOf(false) }

    val storageOk = remember(refreshKey) { PermissionManager.storageAccess(context) }
    val notificationsOk = remember(refreshKey) { PermissionManager.notificationsGranted(context) }
    val adminOk = remember(refreshKey) { PermissionManager.deviceAdminActive(context, admin) }
    val batteryOk = remember(refreshKey) { PermissionManager.batteryOptimizationIgnored(context) }
    val locationOk = remember(refreshKey) { PermissionManager.locationEnabled(context) }
    val accessibilityOk = remember(refreshKey) { PermissionManager.accessibilityEnabled(context) }
    val wifiOk = remember(refreshKey) { PermissionManager.wifiScanPermissionGranted(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshKey++ }

    val adminLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshKey++ }

    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshKey++ }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshKey++
                vm.refreshCache()
                monitorOn = Prefs.monitorOn(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(4000)
            message = null
        }
    }

    fun saveBool(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun setMonitor(on: Boolean) {
        monitorOn = on
        saveBool(Prefs.MONITOR_ON, on)
        try {
            if (on) {
                if (Build.VERSION.SDK_INT >= 33 && !notificationsOk) {
                    permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                }
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, MonitorService::class.java)
                )
                message = "Gerçek zamanlı izleme açıldı"
                NgLog.i("Ayarlar", "Gerçek zamanlı izleme açıldı")
            } else {
                context.stopService(Intent(context, MonitorService::class.java))
                message = "Gerçek zamanlı izleme kapatıldı"
                NgLog.i("Ayarlar", "Gerçek zamanlı izleme kapatıldı")
            }
        } catch (e: Exception) {
            message = "Servis başlatılamadı; ayar kaydedildi"
            NgLog.e("Ayarlar", "İzleme servisi başlatılamadı: ${e.message}")
        }
    }

    fun requestWifi() {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }.filter { !PermissionManager.status(context, it) }
        if (needed.isEmpty()) {
            refreshKey++
            try { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
            catch (e: Exception) { message = "Konum ayarları açılamadı" }
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    fun requestAdmin() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "NexGuard'ın cihazı kilitleme ve güvenlik durumunu izleme özellikleri için."
            )
        }
        try { adminLauncher.launch(intent) } catch (e: Exception) {
            message = "Cihaz yöneticisi ekranı açılamadı"
        }
    }

    fun requestBattery() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        try {
            batteryLauncher.launch(intent)
        } catch (e: Exception) {
            try { batteryLauncher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            catch (e2: Exception) { message = "Pil ayarları açılamadı" }
        }
    }

    val version = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—" }
        catch (e: Exception) { "—" }
    }

    NgBackground {
        Column(Modifier.fillMaxSize()) {
            NgTopBar(
                title = "Ayarlar",
                subtitle = "Koruma, tarama ve izinler",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { refreshKey++; vm.refreshCache() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Yenile")
                    }
                }
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                message?.let {
                    InfoBanner(
                        it,
                        icon = Icons.Outlined.CheckCircle,
                        container = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(Modifier.height(12.dp))
                }

                SectionCard(title = "Koruma", icon = Icons.Outlined.Shield) {
                    ToggleRow(
                        icon = Icons.Outlined.Radar,
                        title = "Gerçek zamanlı izleme",
                        subtitle = "İndirilenleri ve yeni uygulamaları otomatik tara",
                        checked = monitorOn,
                        onCheckedChange = { setMonitor(it) }
                    )
                    ToggleRow(
                        icon = Icons.Outlined.DeleteSweep,
                        title = "Tehlikeleri otomatik karantinaya al",
                        subtitle = "Yalnızca indirme izlemede tehlikeli bulunan dosyalar",
                        checked = autoQuarantine,
                        onCheckedChange = {
                            autoQuarantine = it
                            saveBool(Prefs.AUTO_QUARANTINE, it)
                        }
                    )
                    ToggleRow(
                        icon = Icons.Outlined.Notifications,
                        title = "Tehdit bildirimleri",
                        subtitle = "Arka planda bulunan tehditler için uyarı göster",
                        checked = notifyThreats,
                        onCheckedChange = {
                            notifyThreats = it
                            saveBool(Prefs.NOTIFY_THREATS, it)
                        }
                    )
                }

                Spacer(Modifier.height(12.dp))

                SectionCard(title = "Tarama", icon = Icons.Outlined.Tune) {
                    Text(
                        "Varsayılan tarama modu",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = defaultMode == "full",
                            onClick = {
                                defaultMode = "full"
                                prefs.edit().putString(Prefs.DEFAULT_MODE, "full").apply()
                            },
                            label = { Text("Tam") }
                        )
                        FilterChip(
                            selected = defaultMode == "quick",
                            onClick = {
                                defaultMode = "quick"
                                prefs.edit().putString(Prefs.DEFAULT_MODE, "quick").apply()
                            },
                            label = { Text("Hızlı") }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    ToggleRow(
                        icon = Icons.Outlined.Cached,
                        title = "Dosya önbelleği",
                        subtitle = "Değişmemiş temiz dosyaları yeniden taramadan geçir",
                        checked = useCache,
                        onCheckedChange = {
                            useCache = it
                            saveBool(Prefs.USE_CACHE, it)
                        }
                    )
                    ToggleRow(
                        icon = Icons.Outlined.Apps,
                        title = "Kurulu uygulamaları tara",
                        subtitle = "Tam taramada kurulu APK'ları da analiz et",
                        checked = scanApps,
                        onCheckedChange = {
                            scanApps = it
                            saveBool(Prefs.SCAN_INSTALLED_APPS, it)
                        }
                    )
                    ToggleRow(
                        icon = Icons.Outlined.Cloud,
                        title = "Bulut doğrulama (deneysel)",
                        subtitle = "Yalnızca şüpheli dosyanın SHA-256 hash'i; topluluk API'si erişim anahtarı gerektirebilir",
                        checked = cloudLookup,
                        onCheckedChange = {
                            cloudLookup = it
                            saveBool(Prefs.CLOUD_LOOKUP, it)
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Önbellek", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                "${info.cacheCount} kayıt",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { confirmClearCache = true },
                            enabled = info.cacheCount > 0 && !info.clearing,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (info.clearing) "Temizleniyor…" else "Temizle")
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                SectionCard(title = "İzinler ve erişim", icon = Icons.Outlined.Key) {
                    NavRow(
                        icon = Icons.Outlined.Folder,
                        title = "Tüm dosyalara erişim",
                        subtitle = "Tam depolama taraması için gerekli",
                        trailing = if (storageOk) "Açık" else "Kapalı",
                        onClick = { openStorageSettings(context) }
                    )
                    NavRow(
                        icon = Icons.Outlined.Notifications,
                        title = "Bildirim izni",
                        subtitle = "Tehdit uyarılarını göster",
                        trailing = if (notificationsOk) "Açık" else "Kapalı",
                        onClick = {
                            if (Build.VERSION.SDK_INT >= 33 && !notificationsOk) {
                                permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                            } else {
                                openNotificationSettings(context)
                            }
                        }
                    )
                    NavRow(
                        icon = Icons.Outlined.Wifi,
                        title = "Konum ve yakındaki ağlar",
                        subtitle = when {
                            !wifiOk -> "Wi-Fi taraması için izin gerekli"
                            Build.VERSION.SDK_INT < 33 && !locationOk ->
                                "Konum servisi kapalı; sonuçlar boş olabilir"
                            else -> "Wi-Fi taraması kullanılabilir"
                        },
                        trailing = if (wifiOk) "Açık" else "Eksik",
                        onClick = {
                            when {
                                !wifiOk -> requestWifi()
                                Build.VERSION.SDK_INT < 33 -> {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                    } catch (e: Exception) {
                                        message = "Konum ayarları açılamadı"
                                    }
                                }
                                else -> {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                                    } catch (e: Exception) {
                                        message = "Wi-Fi ayarları açılamadı"
                                    }
                                }
                            }
                        }
                    )
                    NavRow(
                        icon = Icons.Outlined.AdminPanelSettings,
                        title = "Cihaz yöneticisi",
                        subtitle = "Cihazı kilitleme ve yönetim",
                        trailing = if (adminOk) "Etkin" else "Kapalı",
                        onClick = {
                            if (adminOk) confirmRemoveAdmin = true else requestAdmin()
                        }
                    )
                    NavRow(
                        icon = Icons.Outlined.BatterySaver,
                        title = "Pil optimizasyonu",
                        subtitle = "Arka plan korumasının durdurulmasını önle",
                        trailing = if (batteryOk) "İstisna" else "Kısıtlı",
                        onClick = { if (!batteryOk) requestBattery() else openBatterySettings(context) }
                    )
                }

                Spacer(Modifier.height(12.dp))

                SectionCard(title = "Araçlar", icon = Icons.Outlined.Build) {
                    NavRow(
                        icon = Icons.Outlined.BugReport,
                        title = "Canlı log",
                        subtitle = "Tarama ve servis olaylarını izle",
                        onClick = onOpenLog
                    )
                    NavRow(
                        icon = Icons.Outlined.Language,
                        title = "Engellenen siteler",
                        subtitle = "URL kontrolü ve alan adı listesi",
                        onClick = onOpenSites
                    )
                    NavRow(
                        icon = Icons.Outlined.Visibility,
                        title = "Tarayıcı site kontrolü",
                        subtitle = if (accessibilityOk) "Aktif; adres çubuğu denetleniyor"
                        else "Erişilebilirlik izni gerekli",
                        trailing = if (accessibilityOk) "Açık" else "Kapalı",
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            } catch (e: Exception) {
                                message = "Erişilebilirlik ayarları açılamadı"
                            }
                        }
                    )
                    NavRow(
                        icon = Icons.Outlined.History,
                        title = "Tarama geçmişi",
                        subtitle = "Oturumlar ve tehdit kayıtları",
                        onClick = onOpenHistory
                    )
                }

                Spacer(Modifier.height(12.dp))

                SectionCard(title = "Hakkında", icon = Icons.Outlined.Info) {
                    InfoRow("Sürüm", version)
                    InfoRow("Tarama motoru", "v${vm.engineVersion}")
                    InfoRow("Dedektör sayısı", "${vm.detectorCount}")
                    InfoRow("İmza sayısı", "${vm.signatureCount}")
                    InfoRow("Veri politikası", "Cihazda kalır")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "NexGuard dosyalarınızı buluta yüklemez. Bulut doğrulama açıkça etkinleştirilirse " +
                            "yalnızca dosya hash'i sorgulanır. Karantina AES-256-GCM ile şifrelenir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(28.dp))
            }
        }
    }

    if (confirmClearCache) {
        AlertDialog(
            onDismissRequest = { confirmClearCache = false },
            title = { Text("Önbellek temizlensin mi?") },
            text = { Text("${info.cacheCount} dosya kaydı silinecek. Sonraki tarama daha uzun sürebilir.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearCache { msg -> message = msg }
                    confirmClearCache = false
                    NgLog.i("Ayarlar", "Tarama önbelleği temizlendi")
                }) { Text("Temizle") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearCache = false }) { Text("Vazgeç") }
            }
        )
    }

    if (confirmRemoveAdmin) {
        AlertDialog(
            onDismissRequest = { confirmRemoveAdmin = false },
            icon = { Icon(Icons.Outlined.AdminPanelSettings, contentDescription = null) },
            title = { Text("Cihaz yöneticisi kaldırılsın mı?") },
            text = {
                Text(
                    "NexGuard artık cihazı kilitleyemez ve cihaz yöneticisi gerektiren " +
                        "güvenlik işlemlerini yapamaz. Diğer koruma özellikleri çalışmaya devam eder."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        try {
                            dpm.removeActiveAdmin(admin)
                            refreshKey++
                            message = "Cihaz yöneticisi devre dışı bırakıldı"
                            NgLog.i("Ayarlar", "Cihaz yöneticisi kaldırıldı")
                        } catch (e: Exception) {
                            message = "Cihaz yöneticisi kaldırılamadı"
                            NgLog.e("Ayarlar", "Cihaz yöneticisi kaldırılamadı: ${e.message}")
                        }
                        confirmRemoveAdmin = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Kaldır") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveAdmin = false }) { Text("Vazgeç") }
            }
        )
    }
}

private fun openStorageSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= 30) {
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
            )
            return
        } catch (e: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                return
            } catch (e2: Exception) { }
        }
    }
    openAppSettings(context)
}

private fun openAppSettings(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        )
    } catch (e: Exception) { }
}

private fun openNotificationSettings(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        )
    } catch (e: Exception) {
        openAppSettings(context)
    }
}

private fun openBatterySettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    } catch (e: Exception) { }
}
