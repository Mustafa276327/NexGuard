package com.nexguard.app

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import com.nexguard.app.ui.device.DeviceRoute
import com.nexguard.app.ui.history.HistoryRoute
import com.nexguard.app.ui.home.HomeRoute
import com.nexguard.app.ui.quarantine.QuarantineRoute
import com.nexguard.app.ui.scan.ScanRoute
import com.nexguard.app.ui.settings.SettingsRoute
import com.nexguard.app.ui.sites.SitesRoute
import com.nexguard.app.ui.theme.NexGuardTheme
import com.nexguard.app.ui.wifi.WifiRoute
import com.nexguard.core.common.Verdict
import com.nexguard.core.eventbus.EventBus
import com.nexguard.core.eventbus.NexEvent
import com.nexguard.feature.onboarding.OnboardingScreen
import com.nexguard.feature.quarantine.QuarantineManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

private data class ThreatInfo(val path: String, val verdict: Verdict, val score: Int)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var eventBus: EventBus
    @Inject lateinit var quarantineManager: QuarantineManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("nexguard_prefs", MODE_PRIVATE)
        // Gerçek zamanlı izleme varsayılan olarak açık
        if (prefs.getBoolean("monitor_on", true)) {
            try {
                ContextCompat.startForegroundService(this, Intent(this, MonitorService::class.java))
            } catch (e: Exception) { /* kısıtlamalar */ }
        }

        setContent {
            NexGuardTheme {
                val nav = rememberNavController()
                val start = remember {
                    if (prefs.getBoolean("onboarding_done", false)) "home" else "onboarding"
                }
                var threat by remember { mutableStateOf<ThreatInfo?>(null) }

                LaunchedEffect(Unit) {
                    eventBus.events.collect { e ->
                        if (e is NexEvent.ThreatDetected && e.verdict == Verdict.DANGEROUS && threat == null) {
                            threat = ThreatInfo(e.path, e.verdict, e.score)
                        }
                    }
                }

                fun back() {
                    if (!nav.popBackStack()) nav.navigate("home")
                }

                NavHost(navController = nav, startDestination = start) {
                    composable("onboarding") {
                        OnboardingScreen(
                            onFinished = {
                                prefs.edit().putBoolean("onboarding_done", true).apply()
                                nav.navigate("home") { popUpTo("onboarding") { inclusive = true } }
                            },
                            adminReceiver = ComponentName(this@MainActivity, DeviceAdminReceiver::class.java),
                            context = this@MainActivity
                        )
                    }
                    composable("home") {
                        HomeRoute(
                            onScan = { nav.navigate("scan") },
                            onNavigate = { route -> nav.navigate(route) }
                        )
                    }
                    composable("scan") { ScanRoute(onBack = { back() }) }
                    composable("quarantine") { QuarantineRoute(onBack = { back() }) }
                    composable("history") { HistoryRoute(onBack = { back() }) }
                    composable("settings") { SettingsRoute(onBack = { back() }) }
                    composable("wifi") { WifiRoute(onBack = { back() }) }
                    composable("apps") { AppsRoute(onBack = { back() }) }
                    composable("device") { DeviceRoute(onBack = { back() }) }
                    composable("sites") { SitesRoute(onBack = { back() }) }
                }

                threat?.let { t ->
                    AlertDialog(
                        onDismissRequest = { threat = null },
                        title = { Text("Tehdit tespit edildi", fontWeight = FontWeight.Bold) },
                        text = {
                            Column {
                                Text(
                                    File(t.path).name.ifEmpty { t.path },
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(t.path, style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Durum: TEHLİKELİ · Skor: ${t.score}/100",
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Dosyayı karantinaya alarak zararlı içeriği izole edebilirsiniz.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                lifecycleScope.launch {
                                    try {
                                        quarantineManager.quarantine(t.path, "Tehdit: ${t.verdict} (${t.score})")
                                    } catch (e: Exception) { }
                                }
                                threat = null
                            }) { Text("Karantinaya Al") }
                        },
                        dismissButton = {
                            TextButton(onClick = { threat = null }) { Text("Yoksay") }
                        }
                    )
                }
            }
        }
    }
}
