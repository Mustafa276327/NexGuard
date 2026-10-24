package com.nexguard.app.ui.device

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexguard.app.admin.DeviceAdminReceiver
import com.nexguard.app.ui.components.*
import com.nexguard.feature.onboarding.PermissionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

enum class CheckStatus { PASS, WARN, FAIL, INFO }

data class DeviceCheck(
    val id: String,
    val title: String,
    val detail: String,
    val status: CheckStatus,
    val advice: String? = null,
    val icon: ImageVector
)

@HiltViewModel
class DeviceViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    data class UiState(
        val deviceName: String = "",
        val android: String = "",
        val patch: String = "—",
        val patchDays: Long = -1,
        val score: Int = 100,
        val checks: List<DeviceCheck> = emptyList(),
        val adminActive: Boolean = false,
        val loading: Boolean = true,
        val root: Boolean? = null
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            val admin = ComponentName(context, DeviceAdminReceiver::class.java)
            val adminActive = try { dpm?.isAdminActive(admin) == true } catch (e: Exception) { false }
            val root = detectRoot()
            val selinux = try {
                File("/sys/fs/selinux/enforce").readText().trim()
            } catch (e: Exception) { "" }
            val adb = try {
                Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
            } catch (e: Exception) { false }
            val devOptions = try {
                Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
            } catch (e: Exception) { false }
            val secureLock = try {
                (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure
            } catch (e: Exception) { false }
            val encrypted = try {
                when (dpm?.storageEncryptionStatus) {
                    DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE,
                    DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY,
                    DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER -> true
                    DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE -> false
                    else -> null
                }
            } catch (e: Exception) { null }
            val batteryOk = PermissionManager.batteryOptimizationIgnored(context)

            val patch = Build.VERSION.SECURITY_PATCH.ifBlank { "—" }
            val patchDays = try {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(patch)
                if (date != null) TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - date.time) else -1
            } catch (e: Exception) { -1 }

            val checks = mutableListOf<DeviceCheck>()
            var penalty = 0

            checks += if (root) {
                penalty += 30
                DeviceCheck(
                    "root", "Root erişimi",
                    "Cihazda root/su izi bulundu. Bu, güvenlik modelini zayıflatır.",
                    CheckStatus.FAIL,
                    "Root kaldırılmadan güvenli ödeme ve bankacılık işlemi yapmayın.",
                    Icons.Outlined.Warning
                )
            } else {
                DeviceCheck(
                    "root", "Root erişimi",
                    "Bilinen root/su konumlarında iz bulunmadı.",
                    CheckStatus.PASS,
                    icon = Icons.Outlined.VerifiedUser
                )
            }

            checks += when (selinux) {
                "1" -> DeviceCheck(
                    "selinux", "SELinux",
                    "Enforcing modunda; çekirdek düzeyinde zorunlu erişim denetimi açık.",
                    CheckStatus.PASS,
                    icon = Icons.Outlined.Security
                )
                "0" -> {
                    penalty += 20
                    DeviceCheck(
                        "selinux", "SELinux",
                        "Permissive modunda; uygulamalar sistem kaynaklarına daha geniş erişebilir.",
                        CheckStatus.FAIL,
                        "Cihaz yöneticinizden SELinux'u enforcing yapmasını isteyin.",
                        Icons.Outlined.GppBad
                    )
                }
                else -> DeviceCheck(
                    "selinux", "SELinux",
                    "Durum okunamadı.",
                    CheckStatus.INFO,
                    icon = Icons.Outlined.Info
                )
            }

            checks += if (secureLock) {
                DeviceCheck(
                    "lock", "Ekran kilidi",
                    "PIN, desen veya biyometri ile cihaz korunuyor.",
                    CheckStatus.PASS,
                    icon = Icons.Outlined.Lock
                )
            } else {
                penalty += 20
                DeviceCheck(
                    "lock", "Ekran kilidi",
                    "Cihazda güvenli ekran kilidi tanımlı değil.",
                    CheckStatus.FAIL,
                    "Ayarlar > Güvenlik bölümünden PIN veya güçlü bir ekran kilidi belirleyin.",
                    Icons.Outlined.LockOpen
                )
            }

            checks += when (encrypted) {
                true -> DeviceCheck(
                    "encryption", "Depolama şifrelemesi",
                    "Cihaz depolaması şifreli.",
                    CheckStatus.PASS,
                    icon = Icons.Outlined.EnhancedEncryption
                )
                false -> {
                    penalty += 15
                    DeviceCheck(
                        "encryption", "Depolama şifrelemesi",
                        "Depolama şifrelemesi etkin görünmüyor.",
                        CheckStatus.FAIL,
                        "Cihaz destekliyorsa ayarlardan şifrelemeyi etkinleştirin.",
                        Icons.Outlined.NoEncryption
                    )
                }
                null -> DeviceCheck(
                    "encryption", "Depolama şifrelemesi",
                    "Şifreleme durumu bu cihazda okunamadı.",
                    CheckStatus.INFO,
                    icon = Icons.Outlined.Info
                )
            }

            checks += when {
                adb -> {
                    penalty += 12
                    DeviceCheck(
                        "adb", "USB hata ayıklama",
                        "Geliştirici seçeneklerinde USB hata ayıklama açık.",
                        CheckStatus.WARN,
                        "Kullanmıyorsanız geliştirici seçeneklerini kapatın.",
                        Icons.Outlined.Usb
                    )
                }
                devOptions -> DeviceCheck(
                    "adb", "Geliştirici seçenekleri",
                    "Geliştirici seçenekleri açık; USB hata ayıklama kapalı.",
                    CheckStatus.INFO,
                    icon = Icons.Outlined.Build
                )
                else -> DeviceCheck(
                    "adb", "Geliştirici seçenekleri",
                    "Geliştirici seçenekleri ve USB hata ayıklama kapalı.",
                    CheckStatus.PASS,
                    icon = Icons.Outlined.UsbOff
                )
            }

            checks += when {
                patchDays < 0 -> DeviceCheck(
                    "patch", "Güvenlik yaması",
                    "Yama tarihi okunamadı.",
                    CheckStatus.INFO,
                    icon = Icons.Outlined.Info
                )
                patchDays > 180 -> {
                    penalty += 15
                    DeviceCheck(
                        "patch", "Güvenlik yaması",
                        "$patch · ${patchDays} gün önce yayınlandı.",
                        CheckStatus.FAIL,
                        "Sistem güncellemelerini kontrol edin; yama seviyesi eski.",
                        Icons.Outlined.SystemUpdate
                    )
                }
                patchDays > 90 -> {
                    penalty += 8
                    DeviceCheck(
                        "patch", "Güvenlik yaması",
                        "$patch · ${patchDays} gün önce yayınlandı.",
                        CheckStatus.WARN,
                        "Güncelleme denetimi önerilir.",
                        Icons.Outlined.SystemUpdate
                    )
                }
                else -> DeviceCheck(
                    "patch", "Güvenlik yaması",
                    "$patch · ${patchDays} gün önce yayınlandı.",
                    CheckStatus.PASS,
                    icon = Icons.Outlined.SystemUpdate
                )
            }

            checks += if (adminActive) {
                DeviceCheck(
                    "admin", "Cihaz yöneticisi",
                    "NexGuard cihaz yöneticisi olarak etkin. Cihaz uzaktan kilitlenebilir.",
                    CheckStatus.PASS,
                    icon = Icons.Outlined.AdminPanelSettings
                )
            } else {
                penalty += 5
                DeviceCheck(
                    "admin", "Cihaz yöneticisi",
                    "Etkin değil. Cihazı NexGuard üzerinden kilitleme özelliği kullanılamaz.",
                    CheckStatus.WARN,
                    "İsteğe bağlı: yönetici yetkisini etkinleştirin.",
                    Icons.Outlined.AdminPanelSettings
                )
            }

            checks += if (batteryOk) {
                DeviceCheck(
                    "battery", "Pil optimizasyonu",
                    "NexGuard için istisna tanımlı; arka plan koruması daha stabil.",
                    CheckStatus.PASS,
                    icon = Icons.Outlined.BatterySaver
                )
            } else {
                penalty += 3
                DeviceCheck(
                    "battery", "Pil optimizasyonu",
                    "Sistem, arka plan izlemesini kısıtlayabilir.",
                    CheckStatus.WARN,
                    "Ayarlar'dan pil optimizasyonu istisnası verebilirsiniz.",
                    Icons.Outlined.BatteryAlert
                )
            }

            _ui.value = UiState(
                deviceName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
                android = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                patch = patch,
                patchDays = patchDays,
                score = (100 - penalty).coerceIn(0, 100),
                checks = checks,
                adminActive = adminActive,
                loading = false,
                root = root
            )
        }
    }

    private fun detectRoot(): Boolean {
        val paths = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
            "/system/sd/xbin/su", "/system/app/Superuser.apk", "/data/local/xbin/su"
        )
        if (paths.any { File(it).exists() }) return true
        return try {
            val p = ProcessBuilder("which", "su").start()
            val out = p.inputStream.bufferedReader().readText().trim()
            p.waitFor()
            out.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
}

@Composable
fun DeviceRoute(onBack: () -> Unit) {
    val vm: DeviceViewModel = hiltViewModel()
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    val dpm = remember { context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager }
    val admin = remember { ComponentName(context, DeviceAdminReceiver::class.java) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmLock by remember { mutableStateOf(false) }
    var confirmRemoveAdmin by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
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

    fun openSettings(action: String) {
        try { context.startActivity(Intent(action)) } catch (e: Exception) {
            message = "Ayar ekranı açılamadı"
        }
    }

    fun requestAdmin() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "NexGuard'ın cihazı kilitleme özelliği için.")
        }
        try { context.startActivity(intent) } catch (e: Exception) { message = "Ekran açılamadı" }
    }

    NgBackground {
        Column(Modifier.fillMaxSize()) {
            NgTopBar(
                title = "Cihaz Güvenliği",
                subtitle = ui.deviceName.ifBlank { "Cihaz kontrolleri" },
                onBack = onBack,
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Yenile")
                    }
                }
            )

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        shape = RoundedCornerShape(26.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            Modifier
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.secondary
                                        )
                                    )
                                )
                                .padding(20.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (ui.score >= 80) "Cihaz korumalı" else "İyileştirme gerekli",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        ui.android,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                    Text(
                                        "Yama: ${ui.patch}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = Color.White.copy(alpha = 0.16f),
                                        contentColor = Color.White
                                    ) {
                                        Text(
                                            when {
                                                ui.score >= 80 -> "GÜVENLİK SKORU İYİ"
                                                ui.score >= 55 -> "ORTA RİSK"
                                                else -> "YÜKSEK RİSK"
                                            },
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                ScoreRing(
                                    score = ui.score,
                                    modifier = Modifier.size(108.dp),
                                    stroke = 9.dp,
                                    label = "Skor",
                                    progressColor = Color.White,
                                    trackColor = Color.White.copy(alpha = 0.22f),
                                    textColor = Color.White,
                                    labelColor = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                message?.let {
                    item { InfoBanner(it, icon = Icons.Outlined.Info) }
                }

                item {
                    SectionCard(title = "Güvenlik kontrolleri", icon = Icons.Outlined.FactCheck) {
                        if (ui.loading) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("Kontroller çalışıyor…", style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            ui.checks.forEachIndexed { index, check ->
                                CheckRow(check)
                                if (index != ui.checks.lastIndex) {
                                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f))
                                }
                            }
                        }
                    }
                }

                item {
                    SectionCard(title = "Cihaz yöneticisi", icon = Icons.Outlined.AdminPanelSettings) {
                        Text(
                            if (ui.adminActive)
                                "Etkin. Cihazı buradan kilitleyebilir veya yönetici yetkisini kaldırabilirsiniz."
                            else
                                "Cihaz yöneticisi, cihazı uzaktan kilitlemek için kullanılır. İsteğe bağlıdır.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        if (ui.adminActive) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { confirmLock = true },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Cihazı kilitle")
                                }
                                OutlinedButton(
                                    onClick = { confirmRemoveAdmin = true },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) { Text("Kaldır") }
                            }
                        } else {
                            Button(onClick = { requestAdmin() }, shape = RoundedCornerShape(12.dp)) {
                                Icon(Icons.Outlined.AdminPanelSettings, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Etkinleştir")
                            }
                        }
                    }
                }

                item {
                    SectionCard(title = "Sistem ayarları", icon = Icons.Outlined.Settings) {
                        NavRow(
                            icon = Icons.Outlined.Security,
                            title = "Güvenlik ayarları",
                            subtitle = "Ekran kilidi ve şifreleme",
                            onClick = { openSettings(Settings.ACTION_SECURITY_SETTINGS) }
                        )
                        NavRow(
                            icon = Icons.Outlined.SystemUpdate,
                            title = "Sistem güncellemesi",
                            subtitle = "Güvenlik yamalarını denetle",
                            onClick = { openSettings(Settings.ACTION_DEVICE_INFO_SETTINGS) }
                        )
                        NavRow(
                            icon = Icons.Outlined.Build,
                            title = "Geliştirici seçenekleri",
                            subtitle = "USB hata ayıklama durumu",
                            onClick = { openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) }
                        )
                    }
                }

                item {
                    InfoBanner(
                        "Root ve SELinux kontrolleri sezgiseldir; kesin sonuç için cihaz üreticisinin güvenlik araçlarına da başvurun.",
                        icon = Icons.Outlined.Info
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (confirmLock) {
        AlertDialog(
            onDismissRequest = { confirmLock = false },
            icon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
            title = { Text("Cihaz kilitlensin mi?") },
            text = { Text("Cihaz hemen kilitlenecek. Kilidi açmak için ekran kilidinizi kullanmanız gerekir.") },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        dpm?.lockNow()
                        message = "Cihaz kilitlendi"
                    } catch (e: Exception) {
                        message = "Cihaz kilitlenemedi"
                    }
                    confirmLock = false
                }) { Text("Kilitle") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLock = false }) { Text("Vazgeç") }
            }
        )
    }

    if (confirmRemoveAdmin) {
        AlertDialog(
            onDismissRequest = { confirmRemoveAdmin = false },
            icon = { Icon(Icons.Outlined.AdminPanelSettings, contentDescription = null) },
            title = { Text("Cihaz yöneticisi kaldırılsın mı?") },
            text = { Text("NexGuard artık cihazı kilitleyemez. Diğer koruma özellikleri çalışmaya devam eder.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        try {
                            dpm?.removeActiveAdmin(admin)
                            message = "Cihaz yöneticisi devre dışı bırakıldı"
                        } catch (e: Exception) {
                            message = "İşlem başarısız"
                        }
                        confirmRemoveAdmin = false
                        vm.refresh()
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

@Composable
private fun CheckRow(check: DeviceCheck) {
    val color = when (check.status) {
        CheckStatus.PASS -> Color(0xFF2E7D32)
        CheckStatus.WARN -> Color(0xFFE08A00)
        CheckStatus.FAIL -> MaterialTheme.colorScheme.error
        CheckStatus.INFO -> MaterialTheme.colorScheme.primary
    }
    val label = when (check.status) {
        CheckStatus.PASS -> "GEÇTİ"
        CheckStatus.WARN -> "DİKKAT"
        CheckStatus.FAIL -> "RİSK"
        CheckStatus.INFO -> "BİLGİ"
    }
    Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(34.dp)
                .background(color.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(check.icon, contentDescription = null, tint = color, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    check.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                StatusPill(label, color.copy(alpha = 0.12f), color)
            }
            Text(
                check.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            check.advice?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = color)
            }
        }
    }
}
