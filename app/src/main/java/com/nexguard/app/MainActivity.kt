package com.nexguard.app

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nexguard.app.admin.DeviceAdminReceiver
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
import com.nexguard.feature.onboarding.OnboardingScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NexGuardTheme {
                val nav = rememberNavController()
                val prefs = remember { getSharedPreferences("nexguard_prefs", MODE_PRIVATE) }
                val start = remember {
                    if (prefs.getBoolean("onboarding_done", false)) "home" else "onboarding"
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
            }
        }
    }
}
