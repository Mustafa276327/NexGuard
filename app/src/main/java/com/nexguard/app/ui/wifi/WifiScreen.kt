package com.nexguard.app.ui.wifi

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

private data class WifiSnapshot(
    val enabled: Boolean = false,
    val ssid: String = "—",
    val security: String = "—",
    val ip: String = "—",
    val linkSpeed: Int = -1,
    val frequency: Int = -1,
    val permissionMissing: Boolean = false
)

@SuppressLint("MissingPermission")
private fun readWifi(context: Context): WifiSnapshot {
    val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        ?: return WifiSnapshot()
    val locationGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val enabled = wm.isWifiEnabled
    if (!enabled) return WifiSnapshot(enabled = false)

    val ci = try { wm.connectionInfo } catch (e: Exception) { null }
        ?: return WifiSnapshot(enabled = true, permissionMissing = !locationGranted)

    val ssid = if (locationGranted) ci.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" } ?: "—" else "—"
    val ip = try {
        if (ci.ipAddress != 0) Formatter.formatIpAddress(ci.ipAddress) else "—"
    } catch (e: Exception) { "—" }

    val security = try {
        if (locationGranted) {
            wm.scanResults.firstOrNull { it.SSID == ci.ssid }?.capabilities?.let { cap ->
                when {
                    cap.contains("WPA3") -> "WPA3"
                    cap.contains("WPA2") -> "WPA2"
                    cap.contains("WPA") -> "WPA"
                    cap.contains("WEP") -> "WEP (zayıf)"
                    cap.contains("OPEN") -> "Açık ağ (riskli)"
                    else -> "Bilinmiyor"
                }
            } ?: "—"
        } else "İzin gerekli"
    } catch (e: Exception) { "—" }

    return WifiSnapshot(
        enabled = true,
        ssid = ssid,
        security = security,
        ip = ip,
        linkSpeed = ci.linkSpeed,
        frequency = ci.frequency,
        permissionMissing = !locationGranted
    )
}

@Composable
fun WifiRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    val snapshot = remember(refresh) { readWifi(context) }

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
                    Text("Konum izni gerekli", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Ağ adını ve güvenlik bilgisini görmek için konum izni verin.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.parse("package:${context.packageName}"))
                            )
                        } catch (e: Exception) { }
                    }) { Text("İzin Ver") }
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
