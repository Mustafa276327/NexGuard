package com.nexguard.app

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nexguard.app.admin.DeviceAdminReceiver
import com.nexguard.app.service.MonitorService
import com.nexguard.app.ui.apps.AppsRoute
import com.nexguard.app.ui.components.InfoBanner
import com.nexguard.app.ui.components.SectionCard
import com.nexguard.app.ui.components.VerdictPill
import com.nexguard.app.ui.device.DeviceRoute
import com.nexguard.app.ui.history.HistoryRoute
import com.nexguard.app.ui.home.HomeRoute
import com.nexguard.app.ui.log.LogRoute
import com.nexguard.app.ui.quarantine.QuarantineRoute
import com.nexguard.app.ui.scan.ScanRoute
import com.nexguard.app.ui.settings.SettingsRoute
import com.nexguard.app.ui.sites.SitesRoute
import com.nexguard.app.ui.theme.NexGuardTheme
import com.nexguard.app.ui.wifi.WifiRoute
import com.nexguard.app.util.NgLog
import com.nexguard.app.util.Prefs
import com.nexguard.core.common.Verdict
import com.nexguard.core.eventbus.EventBus
import com.nexguard.core.eventbus.NexEvent
import com.nexguard.feature.onboarding.OnboardingScreen
import com.nexguard.feature.quarantine.QuarantineManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

private data class ThreatInfo(
    val path: String,
    val verdict: Verdict,
    val score: Int,
    val threatType: String,
    val source: String
)

private val ALLOWED_ROUTES = setOf(
    "home", "scan", "history", "quarantine", "settings",
    "wifi", "apps", "device", "sites", "log"
)

private fun sourceLabel(source: String): String = when (source) {
    "download" -> "İndirme izleme"
    "install" -> "Yeni kurulan uygulama"
    "app" -> "Uygulama analizi"
    "manual" -> "Manuel tarama"
    else -> source.ifBlank { "Bilinmiyor" }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var eventBus: EventBus
    @Inject lateinit var quarantineManager: QuarantineManager

    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingRoute = intent?.getStringExtra("nexguard_route")

        setContent {
            NexGuardTheme {
                val context = this@MainActivity
                val nav = rememberNavController()
                var onboardingDone by remember { mutableStateOf(Prefs.onboardingDone(context)) }
                val startDestination = remember {
                    if (Prefs.onboardingDone(context)) "home" else "onboarding"
                }
                val threats = remember { mutableStateListOf<ThreatInfo>() }
                var busy by remember { mutableStateOf(false) }
                var confirmDelete by remember { mutableStateOf<ThreatInfo?>(null) }

                LaunchedEffect(Unit) {
                    eventBus.events.collect { e ->
                        if (e is NexEvent.ThreatDetected && e.verdict == Verdict.DANGEROUS) {
                            if (threats.none { it.path == e.path } && threats.size < 8) {
                                val type = e.threatType.ifBlank { "Şüpheli dosya" }
                                threats.add(
                                    ThreatInfo(
                                        path = e.path,
                                        verdict = e.verdict,
                                        score = e.score,
                                        threatType = type,
                                        source = e.source
                                    )
                                )
                                NgLog.w(
                                    "Tehdit",
                                    "Uyarı kuyruğa alındı: ${File(e.path).name} · $type · ${e.score}/100"
                                )
                            }
                        }
                    }
                }

                LaunchedEffect(onboardingDone) {
                    if (!onboardingDone) return@LaunchedEffect
                    ensureMonitor()
                }

                LaunchedEffect(onboardingDone, pendingRoute) {
                    val target = pendingRoute
                    if (onboardingDone && target != null) {
                        if (target in ALLOWED_ROUTES) {
                            nav.navigate(target) { launchSingleTop = true }
                        }
                        pendingRoute = null
                    }
                }

                fun back() {
                    if (!nav.popBackStack()) nav.navigate("home") { launchSingleTop = true }
                }

                NavHost(navController = nav, startDestination = startDestination) {
                    composable("onboarding") {
                        OnboardingScreen(
                            onFinished = {
                                Prefs.get(context).edit()
                                    .putBoolean(Prefs.ONBOARDING_DONE, true).apply()
                                onboardingDone = true
                                nav.navigate("home") {
                                    popUpTo("onboarding") { inclusive = true }
                                }
                            },
                            adminReceiver = ComponentName(context, DeviceAdminReceiver::class.java),
                            context = context
                        )
                    }
                    composable("home") {
                        HomeRoute(
                            onScan = { nav.navigate("scan") },
                            onNavigate = { route -> nav.navigate(route) { launchSingleTop = true } }
                        )
                    }
                    composable("scan") { ScanRoute(onBack = { back() }) }
                    composable("quarantine") { QuarantineRoute(onBack = { back() }) }
                    composable("history") { HistoryRoute(onBack = { back() }) }
                    composable("settings") {
                        SettingsRoute(
                            onBack = { back() },
                            onOpenLog = { nav.navigate("log") },
                            onOpenSites = { nav.navigate("sites") },
                            onOpenHistory = { nav.navigate("history") }
                        )
                    }
                    composable("wifi") { WifiRoute(onBack = { back() }) }
                    composable("apps") { AppsRoute(onBack = { back() }) }
                    composable("device") { DeviceRoute(onBack = { back() }) }
                    composable("sites") { SitesRoute(onBack = { back() }) }
                    composable("log") { LogRoute(onBack = { back() }) }
                }

                val current = threats.firstOrNull()
                if (current != null && onboardingDone) {
                    val file = File(current.path)
                    val fileExists = file.isFile
                    val canModify = fileExists && file.canRead() &&
                        file.parentFile?.canWrite() != false
                    AlertDialog(
                        onDismissRequest = {
                            if (!busy) threats.removeAt(0)
                        },
                        icon = {
                            Icon(
                                Icons.Outlined.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        title = {
                            Text(
                                "Tehdit tespit edildi",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Column(
                                Modifier
                                    .heightIn(max = 430.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    VerdictPill(current.verdict)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "${current.threatType} · ${current.score}/100",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    file.name.ifBlank { current.path },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    current.path,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Virüs türü: ${current.threatType}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Kaynak: ${sourceLabel(current.source)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (threats.size > 1) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "${threats.size - 1} tehdit daha sırada.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                if (!fileExists) {
                                    InfoBanner(
                                        "Dosya artık mevcut değil; karantinaya alınmış veya taşınmış olabilir. " +
                                            "Geçmişten kaydı inceleyebilirsiniz.",
                                        icon = Icons.Outlined.Info,
                                        container = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else if (!canModify) {
                                    InfoBanner(
                                        "Dosya salt okunur bir konumda (ör. sistem uygulaması). " +
                                            "Karantina uygulanamaz; uygulamayı sistem ayarlarından kaldırın.",
                                        icon = Icons.Outlined.Warning,
                                        container = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                } else {
                                    InfoBanner(
                                        "Karantinaya almak dosyayı AES-256 ile şifreleyip izole eder. " +
                                            "Gerekirse daha sonra geri yükleyebilirsiniz.",
                                        icon = Icons.Outlined.Shield
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                SectionCard(title = "Ne yapmalıyım?", icon = Icons.Outlined.Shield) {
                                    Text(
                                        "Dosyayı açmayın, çalıştırmayın ve paylaşmayın. Karantinaya alın; " +
                                            "güvenilir olduğundan emin değilseniz kalıcı olarak silin. " +
                                            "Bir uygulama kaynaklıysa uygulamayı da kaldırın.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (canModify) {
                                    TextButton(
                                        onClick = { confirmDelete = current },
                                        enabled = !busy,
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        )
                                    ) { Text("Kalıcı sil") }
                                    TextButton(
                                        onClick = {
                                            busy = true
                                            lifecycleScope.launch {
                                                try {
                                                    val outcome = quarantineManager.quarantine(
                                                        current.path,
                                                        "Ana ekran tehdit uyarısı",
                                                        current.threatType
                                                    )
                                                    val warning = outcome.sourceDeleteFailed
                                                    val text = when {
                                                        outcome.success && warning ->
                                                            "Karantinaya alındı ancak kaynak silinemedi: ${file.name}"
                                                        outcome.success -> "Karantinaya alındı: ${file.name}"
                                                        else -> "Karantina başarısız: ${outcome.message}"
                                                    }
                                                    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                                                    when {
                                                        outcome.success && !warning -> {
                                                            NgLog.i("Tehdit", "Karantinaya alındı: ${file.name}")
                                                            if (threats.isNotEmpty()) threats.removeAt(0)
                                                        }
                                                        outcome.success -> {
                                                            // Kaynak dosya duruyor; kullanıcı kalıcı silmeyi deneyebilsin.
                                                            NgLog.w("Tehdit", "Kaynak silinemedi: ${file.name}")
                                                        }
                                                        else -> NgLog.e(
                                                            "Tehdit",
                                                            "Karantina başarısız: ${outcome.message}"
                                                        )
                                                    }
                                                } catch (e: Exception) {
                                                    Toast.makeText(
                                                        context,
                                                        "Karantina hatası: ${e.message ?: "bilinmeyen hata"}",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                    NgLog.e("Tehdit", "Karantina hatası: ${e.message}")
                                                } finally {
                                                    busy = false
                                                }
                                            }
                                        },
                                        enabled = !busy
                                    ) {
                                        if (busy) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Text("Karantinaya al")
                                        }
                                    }
                                }
                            }
                        },
                        dismissButton = {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = {
                                        threats.removeAt(0)
                                        nav.navigate("history") { launchSingleTop = true }
                                    },
                                    enabled = !busy
                                ) { Text("Geçmişte göster") }
                                TextButton(
                                    onClick = { if (!busy) threats.removeAt(0) },
                                    enabled = !busy
                                ) { Text("Daha sonra") }
                            }
                        }
                    )
                }

                confirmDelete?.let { t ->
                    val name = File(t.path).name.ifBlank { t.path }
                    AlertDialog(
                        onDismissRequest = { confirmDelete = null },
                        icon = {
                            Icon(
                                Icons.Outlined.DeleteForever,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        title = { Text("Dosya kalıcı olarak silinsin mi?") },
                        text = {
                            Text(
                                "$name geri alınamaz şekilde silinecek. " +
                                    "Dosyanın güvenilir olduğundan emin değilseniz önce karantinaya alın."
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    val target = t
                                    confirmDelete = null
                                    busy = true
                                    lifecycleScope.launch {
                                        try {
                                            val ok = quarantineManager.deleteFile(target.path)
                                            Toast.makeText(
                                                context,
                                                if (ok) "Dosya silindi"
                                                else "Dosya silinemedi (salt okunur veya erişim yok)",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            if (ok) {
                                                NgLog.i("Tehdit", "Dosya silindi: ${File(target.path).name}")
                                                threats.removeAll { it.path == target.path }
                                            } else {
                                                NgLog.w("Tehdit", "Dosya silinemedi: ${File(target.path).name}")
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(
                                                context,
                                                "Silme hatası: ${e.message ?: "bilinmeyen hata"}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            NgLog.e("Tehdit", "Silme hatası: ${e.message}")
                                        } finally {
                                            busy = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) { Text("Kalıcı sil") }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmDelete = null }) { Text("Vazgeç") }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute = intent.getStringExtra("nexguard_route")
    }

    override fun onResume() {
        super.onResume()
        // Servis sistem tarafından durdurulmuşsa uygulamaya dönüşte yeniden başlat.
        if (Prefs.onboardingDone(this)) ensureMonitor()
    }

    private fun ensureMonitor() {
        if (!Prefs.onboardingDone(this) || !Prefs.monitorOn(this)) return
        try {
            ContextCompat.startForegroundService(this, Intent(this, MonitorService::class.java))
        } catch (e: Exception) {
            NgLog.w("İzleme", "Servis başlatılamadı: ${e.message}")
        }
    }
}
