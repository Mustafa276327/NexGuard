package com.nexguard.feature.onboarding

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    adminReceiver: ComponentName,
    context: Context
) {
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    var step by rememberSaveable { mutableStateOf(0) }
    var refresh by remember { mutableStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val storageOk = remember(refresh) { PermissionManager.storageAccess(context) }
    val adminOk = remember(refresh) { PermissionManager.deviceAdminActive(context, adminReceiver) }
    val batteryOk = remember(refresh) { PermissionManager.batteryOptimizationIgnored(context) }
    val runtimeOk = remember(refresh) { PermissionManager.allRuntimeGranted(context) }
    val notificationOk = remember(refresh) { PermissionManager.notificationsGranted(context) }
    val wifiOk = remember(refresh) { PermissionManager.wifiScanPermissionGranted(context) }
    val locationOk = remember(refresh) { PermissionManager.locationEnabled(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh++ }

    val storageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh++ }

    val adminLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh++ }

    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh++ }

    fun openStorageSettings() {
        if (Build.VERSION.SDK_INT >= 30) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            try {
                storageLauncher.launch(intent)
            } catch (e: Exception) {
                storageLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
        }
    }

    fun requestRuntime() {
        val missing = PermissionManager.missingRuntime(context)
        if (missing.isEmpty()) refresh++ else permissionLauncher.launch(missing.toTypedArray())
    }

    fun openAdmin() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminReceiver)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Cihazı uzaktan kilitlemek ve güvenlik durumunu izlemek için NexGuard'a yönetici yetkisi verin."
            )
        }
        try { adminLauncher.launch(intent) } catch (e: Exception) { }
    }

    fun openBattery() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        try {
            batteryLauncher.launch(intent)
        } catch (e: Exception) {
            batteryLauncher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    BackHandler(enabled = step > 0) { step-- }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("NexGuard", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Kurulum",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${step + 1} / 5",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(18.dp))
            LinearProgressIndicator(
                progress = (step + 1) / 5f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
            )
            Spacer(Modifier.height(28.dp))

            when (step) {
                0 -> IntroStep()
                1 -> StorageStep(storageOk, ::openStorageSettings)
                2 -> PermissionStep(
                    notificationOk = notificationOk,
                    locationOk = locationOk,
                    wifiOk = wifiOk,
                    onRequest = ::requestRuntime,
                    onOpenLocation = {
                        try { storageLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } catch (e: Exception) { }
                    }
                )
                3 -> OptionalStep(
                    adminOk = adminOk,
                    batteryOk = batteryOk,
                    onAdmin = ::openAdmin,
                    onBattery = ::openBattery
                )
                else -> SummaryStep(
                    storageOk = storageOk,
                    runtimeOk = runtimeOk,
                    notificationOk = notificationOk,
                    adminOk = adminOk,
                    batteryOk = batteryOk,
                    onStorage = ::openStorageSettings,
                    onRuntime = ::requestRuntime
                )
            }

            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (step > 0) {
                    OutlinedButton(onClick = { step-- }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Geri")
                    }
                }
                Spacer(Modifier.weight(1f))
                if (step < 4) {
                    Button(
                        onClick = { step++ },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(50.dp)
                    ) {
                        Text(if (step == 0) "Başla" else "Devam")
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Outlined.ArrowForward, contentDescription = null)
                    }
                } else {
                    Button(
                        onClick = {
                            context.getSharedPreferences("nexguard_prefs", Context.MODE_PRIVATE)
                                .edit().putBoolean("onboarding_done", true).apply()
                            onFinished()
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(50.dp)
                    ) {
                        Icon(Icons.Outlined.Check, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Korumayı başlat")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (step in 1..3) {
                TextButton(
                    onClick = {
                        if (step == 3) step = 4 else step++
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Şimdilik atla")
                }
            }
        }
    }
}

@Composable
private fun StepIcon(icon: ImageVector, tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary) {
    Box(
        Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(36.dp))
    }
}

@Composable
private fun StepTitle(title: String, text: String) {
    Spacer(Modifier.height(20.dp))
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun FeatureLine(icon: ImageVector, title: String, text: String) {
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun IntroStep() {
    StepIcon(Icons.Outlined.Shield)
    StepTitle(
        "Cihazın için sessiz koruma",
        "NexGuard; indirilen dosyaları, kurulu uygulamaları, ağ bağlantılarını ve cihaz ayarlarını tek yerde denetler. Verilerin cihazından çıkmaz."
    )
    Spacer(Modifier.height(16.dp))
    FeatureLine(Icons.Outlined.Radar, "Gerçek zamanlı tarama", "İndirilenler ve yeni uygulamalar otomatik incelenir.")
    FeatureLine(Icons.Outlined.Folder, "Tam depolama taraması", "Tüm dosyalar ve kurulu APK'lar isteğe bağlı taranır.")
    FeatureLine(Icons.Outlined.Lock, "Şifreli karantina", "Tehditler AES-256 ile şifrelenip izole edilir.")
    FeatureLine(Icons.Outlined.Public, "Site ve ağ kontrolü", "Şüpheli bağlantılar ve Wi-Fi ağları değerlendirilir.")
}

@Composable
private fun StorageStep(storageOk: Boolean, onOpen: () -> Unit) {
    StepIcon(Icons.Outlined.Folder)
    StepTitle(
        "Dosyalara erişim",
        "Tam tarama için Android'in \"Tüm dosyalara erişim\" izni gerekir. NexGuard dosyalarınızı yalnızca cihaz üzerinde analiz eder; hiçbir dosya yüklenmez."
    )
    Spacer(Modifier.height(20.dp))
    StatusCard(
        ok = storageOk,
        title = if (storageOk) "Depolama erişimi açık" else "Depolama erişimi gerekli",
        text = if (storageOk) "Tüm depolama taranabilir." else "İzin verilmezse yalnızca uygulama alanı ve kısmi klasörler taranır.",
        action = if (storageOk) null else "İzni aç",
        onAction = onOpen
    )
    Spacer(Modifier.height(12.dp))
    Text(
        "Not: Android 10 ve altında okuma izni yeterlidir. Android 11+ için ayarlar ekranı açılır.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PermissionStep(
    notificationOk: Boolean,
    locationOk: Boolean,
    wifiOk: Boolean,
    onRequest: () -> Unit,
    onOpenLocation: () -> Unit
) {
    StepIcon(Icons.Outlined.Notifications)
    StepTitle(
        "Bildirim ve ağ izinleri",
        "Tehdit uyarılarını görebilmek ve yakındaki Wi-Fi ağlarını tarayabilmek için birkaç izin gerekiyor. Bunlar isteğe bağlıdır; daha sonra Ayarlar'dan değiştirebilirsiniz."
    )
    Spacer(Modifier.height(20.dp))
    StatusCard(
        ok = notificationOk,
        title = "Bildirimler",
        text = if (notificationOk) "Tehdit uyarıları gösterilebilir." else "Uyarıları kaçırmamak için bildirimlere izin verin.",
        action = if (notificationOk) null else "İzinleri ver",
        onAction = onRequest
    )
    Spacer(Modifier.height(10.dp))
    // Android 13+ Wi-Fi taraması NEARBY_WIFI_DEVICES ile konum servisi
    // açık olmadan çalışır; konum yalnızca eski sürümlerde gerekir.
    val locationRequired = Build.VERSION.SDK_INT < 33
    StatusCard(
        ok = wifiOk && (!locationRequired || locationOk),
        title = "Konum ve yakındaki ağlar",
        text = when {
            !wifiOk -> "Wi-Fi taraması için yakın cihaz veya konum izni gerekir."
            locationRequired && !locationOk ->
                "Cihaz konum servisi kapalı; Wi-Fi listesi boş görünebilir."
            else -> "Ağ taraması kullanılabilir."
        },
        action = when {
            !wifiOk -> "İzinleri ver"
            locationRequired && !locationOk -> "Konum ayarları"
            else -> null
        },
        onAction = if (!wifiOk) onRequest else onOpenLocation
    )
    Spacer(Modifier.height(12.dp))
    Text(
        "SMS, kişiler, kamera ve mikrofon izinleri istenmez; NexGuard bu verilere erişmez.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun OptionalStep(
    adminOk: Boolean,
    batteryOk: Boolean,
    onAdmin: () -> Unit,
    onBattery: () -> Unit
) {
    StepIcon(Icons.Outlined.AdminPanelSettings)
    StepTitle(
        "İsteğe bağlı güçlendirmeler",
        "Bu adımlar zorunlu değildir. Cihaz yöneticisi, cihazı uzaktan kilitlemeyi sağlar. Pil optimizasyonu istisnası arka plan korumasını daha stabil yapar."
    )
    Spacer(Modifier.height(20.dp))
    StatusCard(
        ok = adminOk,
        title = "Cihaz yöneticisi",
        text = if (adminOk) "Etkin; cihazı kilitleyebilirsiniz." else "İsteğe bağlı: cihazı NexGuard üzerinden kilitleyin.",
        action = if (adminOk) null else "Etkinleştir",
        onAction = onAdmin
    )
    Spacer(Modifier.height(10.dp))
    StatusCard(
        ok = batteryOk,
        title = "Pil optimizasyonu istisnası",
        text = if (batteryOk) "Arka plan koruması için istisna verildi." else "İsteğe bağlı: izlemenin durdurulma olasılığını azaltın.",
        action = if (batteryOk) null else "İzin ver",
        onAction = onBattery
    )
}

@Composable
private fun SummaryStep(
    storageOk: Boolean,
    runtimeOk: Boolean,
    notificationOk: Boolean,
    adminOk: Boolean,
    batteryOk: Boolean,
    onStorage: () -> Unit,
    onRuntime: () -> Unit
) {
    StepIcon(Icons.Outlined.CheckCircle, MaterialTheme.colorScheme.tertiary)
    StepTitle(
        "Kuruluma hazır",
        "NexGuard'ı istediğiniz zaman Ayarlar ekranından yeniden yapılandırabilirsiniz. Eksik izinler varsa aşağıdan tamamlayabilirsiniz."
    )
    Spacer(Modifier.height(20.dp))
    StatusCard(
        ok = storageOk,
        title = "Tam depolama erişimi",
        text = if (storageOk) "Hazır" else "Tam tarama için önerilir.",
        action = if (storageOk) null else "Aç",
        onAction = onStorage
    )
    Spacer(Modifier.height(10.dp))
    StatusCard(
        ok = runtimeOk && notificationOk,
        title = "Bildirim ve ağ izinleri",
        text = if (runtimeOk && notificationOk) "Hazır" else "Uyarılar ve Wi-Fi taraması için önerilir.",
        action = if (runtimeOk && notificationOk) null else "Ver",
        onAction = onRuntime
    )
    Spacer(Modifier.height(10.dp))
    StatusCard(
        ok = adminOk,
        title = "Cihaz yöneticisi",
        text = if (adminOk) "Etkin" else "İsteğe bağlı",
        action = null,
        onAction = {}
    )
    Spacer(Modifier.height(10.dp))
    StatusCard(
        ok = batteryOk,
        title = "Pil optimizasyonu",
        text = if (batteryOk) "İstisna verildi" else "İsteğe bağlı",
        action = null,
        onAction = {}
    )
    Spacer(Modifier.height(16.dp))
    Text(
        "Başladığınızda gerçek zamanlı izleme açılır ve ana ekrana yönlendirilirsiniz.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun StatusCard(
    ok: Boolean,
    title: String,
    text: String,
    action: String?,
    onAction: () -> Unit
) {
    val container = if (ok) {
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                contentDescription = null,
                tint = if (ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (action != null) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onAction) { Text(action) }
            }
        }
    }
}
