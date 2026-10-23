package com.nexguard.app.ui.settings

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nexguard.app.admin.DeviceAdminReceiver
import com.nexguard.app.service.MonitorService

@Composable
fun SettingsRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("nexguard_prefs", Context.MODE_PRIVATE) }
    val dpm = remember { context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager }
    val admin = remember { ComponentName(context, DeviceAdminReceiver::class.java) }

    var monitorOn by remember { mutableStateOf(prefs.getBoolean("monitor_on", false)) }
    var adminActive by remember { mutableStateOf(dpm.isAdminActive(admin)) }
    var notificationsOn by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }

    fun setMonitor(on: Boolean) {
        prefs.edit().putBoolean("monitor_on", on).apply()
        monitorOn = on
        try {
            if (on) {
                ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
            } else {
                context.stopService(Intent(context, MonitorService::class.java))
            }
        } catch (e: Exception) {
            // Android 12+ arka plandan başlatma kısıtı olabilir; ayar yine de kaydedildi
        }
    }

    fun requestAdmin() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "NexGuard'ın koruma özellikleri için cihaz yöneticisi izni.")
        }
        try { context.startActivity(intent) } catch (e: Exception) { }
    }

    fun openAppSettings() {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            )
        } catch (e: Exception) { }
    }

    val version = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—" } catch (e: Exception) { "—" }
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Geri") }
            Spacer(Modifier.width(4.dp))
            Text("Ayarlar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))

        // Koruma
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Koruma", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Gerçek zamanlı izleme", style = MaterialTheme.typography.bodyLarge)
                        Text("İndirilen dosyaları otomatik tara", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = monitorOn, onCheckedChange = { setMonitor(it) })
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        // Cihaz yöneticisi
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Cihaz Yöneticisi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (adminActive) "Durum: Etkin" else "Durum: Devre dışı",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (adminActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(8.dp))
                if (adminActive) {
                    OutlinedButton(onClick = {
                        try { dpm.removeActiveAdmin(admin); adminActive = false } catch (e: Exception) { }
                    }) { Text("Devre Dışı Bırak") }
                } else {
                    Button(onClick = { requestAdmin() }) { Text("Etkinleştir") }
                }
                TextButton(onClick = { adminActive = dpm.isAdminActive(admin) }) { Text("Durumu Yenile") }
            }
        }
        Spacer(Modifier.height(10.dp))

        // İzinler
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("İzinler", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (notificationsOn) "Bildirimler: Açık" else "Bildirimler: Kapalı",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { openAppSettings() }) { Text("İzinleri Yönet") }
                    OutlinedButton(onClick = {
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            )
                        } catch (e: Exception) { }
                    }) { Text("Bildirimler") }
                }
                TextButton(onClick = {
                    notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
                }) { Text("Durumu Yenile") }
            }
        }
        Spacer(Modifier.height(10.dp))

        // Hakkında
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Hakkında", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("NexGuard sürüm $version", style = MaterialTheme.typography.bodyMedium)
                Text("Çevrimdışı tarama motoru · 9 katmanlı dedektör", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
