package com.nexguard.app

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nexguard.feature.home.HomeScreen
import com.nexguard.feature.home.PlaceholderScreen
import com.nexguard.feature.onboarding.OnboardingScreen
import com.nexguard.feature.scan.Scanner
import com.nexguard.feature.settings.LogScreen
import com.nexguard.app.admin.DeviceAdminReceiver
import com.nexguard.app.ui.theme.NexGuardTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var scanner: javax.inject.Provider<Scanner>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NexGuardTheme {
                val nav = rememberNavController()
                var logLines by remember { mutableStateOf(listOf<String>()) }
                NavHost(navController = nav, startDestination = "onboarding") {
                    composable("onboarding") {
                        OnboardingScreen(
                            onFinished = { nav.navigate("home") { popUpTo("onboarding") { inclusive = true } } },
                            adminReceiver = ComponentName(this@MainActivity, DeviceAdminReceiver::class.java),
                            context = this@MainActivity
                        )
                    }
                    composable("home") {
                        HomeScreen(
                            onScan = { nav.navigate("scan") },
                            onNavigate = { route -> nav.navigate(route) }
                        )
                    }
                    composable("scan") {
                        LaunchedEffect(Unit) {
                            val r = scanner.get().scanDirectory("/storage/emulated/0/Download")
                            logLines = r.map { "${it.targetPath} -> ${it.verdict} (${it.score})" }
                            nav.popBackStack()
                        }
                    }
                    composable("quarantine") {
                        PlaceholderScreen(title = "Karantina", body = "Karantinalı dosyalar buraya listelenecek.")
                    }
                    composable("settings") { LogScreen(lines = logLines) }
                    composable("history") { LogScreen(lines = logLines) }
                    composable("wifi") {
                        PlaceholderScreen(title = "Wi-Fi Tarama", body = "Ağ güvenliği taraması burada gösterilecek.")
                    }
                    composable("apps") {
                        PlaceholderScreen(title = "Uygulamalar", body = "Yüklü uygulama analizi burada gösterilecek.")
                    }
                    composable("device") {
                        PlaceholderScreen(title = "Cihaz Güvenliği", body = "Cihaz ayarları ve yönetici durumu burada gösterilecek.")
                    }
                    composable("sites") {
                        PlaceholderScreen(title = "Siteler", body = "Engellenen/güvenli site listesi burada gösterilecek.")
                    }
                }
            }
        }
    }
}
