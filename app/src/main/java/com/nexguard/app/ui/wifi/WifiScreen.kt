package com.nexguard.app.ui.wifi

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

private data class WifiNetwork(val ssid: String, val security: String, val level: Int)

private data class WifiSnapshot(
    val enabled: Boolean = false,
    val ssid: String = "—",
    val security: String = "—",
    val ip: String = "—",
    val linkSpeed: Int = -1,
    val frequency: Int = -1,
    val permissionMissing: Boolean = false,
    val networks: List<WifiNetwork> = emptyList()
)

private fun securityOf(capabilities: String?): String {
    if (capabilities == null) return "Bilinmiyor"
    return when {
        capabilities.contains("WPA3") -> "WPA3"
        capabilities.contains("WPA2") -> "WPA2"
        capabilities.contains("WPA") -> "WPA"
        capabilities.contains("WEP") -> "WEP (zayıf)"
        capabilities.contains("OPEN") -> "Açık ağ (riskli)"
        else -> "Bilinmiyor"
    }
}

@SuppressLint("MissingPermission")
private fun readWifi(context: Context): WifiSnapshot = try {
    val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    if (wm == null) {
        WifiSnapshot(permissionMissing = true)
    } else {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val nearby = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        } else true
        val canRead = fine || nearby

        val enabled = try { wm.isWifiEnabled } catch (e: Exception) { false }
        if (!enabled) {
            WifiSnapshot(enabled = false, permissionMissing = !canRead)
        } else {
            val ci = try { wm.connectionInfo } catch (e: Exception) { null }
            val ssid = if (canRead && ci != null) {
                try {
                    ci.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" } ?: "—"
                } catch (e: Exception) { "—" }
            } else "—"
            val ip = try {
                if (ci != null && ci.ipAddress != 0) Formatter.formatIpAddress(ci.ipAddress) else "—"
            } catch (e: Exception) { "—" }

            val networks = if (canRead) {
                try {
                    wm.scanResults
                        .sortedByDescending { it.level }
                        .mapNotNull { sr ->
                            val name = sr.SSID?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                            WifiNetwork(name, securityOf(sr.capabilities), sr.level)
                        }
                        .distinctBy { it.ssid }
                        .take(20)
                } catch (e: Exception) { emptyList() }
            } else emptyList()

            val currentSecurity = networks.firstOrNull { it.ssid == ssid }?.security
                ?: if (ssid != "—") "Bilinmiyor" else "—"

            WifiSnapshot(
                enabled = true,
                ssid = ssid,
                security = currentSecurity,
                ip = ip,
                linkSpeed = try { ci?.linkSpeed ?: -1 } catch (e: Exception) { -1 },
                frequency = try { ci?.frequency ?: -1 } catch (e: Exception) { -1 },
                permissionMissing = !canRead,
                networks = networks
            )
        }
    }
} catch (e: Exception) {
    WifiSnapshot(permissionMissing = true)
}

@Composable
fun WifiRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    val snapshot = remember(refresh) { readWifi(context) }

    fun scanNetworks() {
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wm.startScan()
        } catch (e: Exception) { }
        refresh++
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Geri") }
            Spacer(Modifier.width(4.dp))
            Text("Wi-Fi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { refresh++ }) { Text("Yenile") }
        }
        Spacer(Modifier.height(12.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Wi-Fi durumu", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        if (snapshot.enabled) "Açık" else "Kapalı",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (snapshot.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(8.dp))
                InfoLine("Ağ (SSID)", snapshot.ssid)
                InfoLine("Güvenlik", snapshot.security)
                InfoLine("IP adresi", snapshot.ip)
                if (snapshot.linkSpeed > 0) InfoLine("Bağlantı hızı", "${snapshot.linkSpeed} Mbps")
                if (snapshot.frequency > 0) InfoLine("Frekans", "${snapshot.frequency} MHz")
            }
        }
        Spacer(Modifier.height(10.dp))

        if (snapshot.permissionMissing) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Konum / yakın cihaz izni gerekli", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Ağ adlarını ve güvenlik bilgisini görmek için izin verin.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                            )
                        } catch (e: Exception) { }
                    }) { Text("İzin Ver") }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Button(onClick = { scanNetworks() }, modifier = Modifier.fillMaxWidth()) {
            Text("Ağları Tara")
        }
        Spacer(Modifier.height(10.dp))

        if (snapshot.networks.isNotEmpty()) {
            Text("Yakındaki Ağlar", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            snapshot.networks.forEach { n ->
                Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(n.ssid, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(n.security, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            when {
                                n.level >= -55 -> "Güçlü"
                                n.level >= -70 -> "Orta"
                                else -> "Zayıf"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Güvenlik Önerisi", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "WPA2 veya WPA3 şifreleme kullanan ağları tercih edin. " +
                        "Açık veya WEP korumalı ağlarda hassas işlem yapmayın.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = {
                try { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) } catch (e: Exception) { }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Wi-Fi Ayarlarını Aç") }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
