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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nexguard.app.ui.components.*
import com.nexguard.feature.onboarding.PermissionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class WifiRisk { SAFE, WARNING, DANGER }

private data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val security: String,
    val level: Int,
    val frequency: Int,
    val risk: WifiRisk,
    val notes: List<String>
)

private data class WifiSnapshot(
    val enabled: Boolean = false,
    val connected: Boolean = false,
    val ssid: String = "—",
    val bssid: String = "",
    val security: String = "—",
    val ip: String = "—",
    val linkSpeed: Int = -1,
    val frequency: Int = -1,
    val permissionMissing: Boolean = false,
    val risk: WifiRisk = WifiRisk.SAFE,
    val notes: List<String> = emptyList(),
    val networks: List<WifiNetwork> = emptyList()
)

private fun bandOf(freq: Int): String = when {
    freq in 2400..2500 -> "2.4 GHz"
    freq in 4900..5900 -> "5 GHz"
    freq >= 5925 -> "6 GHz"
    else -> "—"
}

private fun securityOf(capabilities: String?): String {
    val caps = capabilities ?: return "Bilinmiyor"
    return when {
        caps.contains("WPA3") || caps.contains("SAE") -> "WPA3"
        caps.contains("WPA2") -> "WPA2"
        caps.contains("WPA") -> "WPA"
        caps.contains("WEP") -> "WEP"
        caps.contains("OWE") -> "OWE (şifreli açık)"
        caps.contains("EAP") -> "Kurumsal"
        else -> "Açık"
    }
}

private fun riskOf(security: String): WifiRisk = when (security) {
    "Açık", "WEP" -> WifiRisk.DANGER
    "WPA", "Bilinmiyor" -> WifiRisk.WARNING
    else -> WifiRisk.SAFE
}

private fun riskLabel(risk: WifiRisk): String = when (risk) {
    WifiRisk.SAFE -> "GÜVENLİ"
    WifiRisk.WARNING -> "DİKKAT"
    WifiRisk.DANGER -> "RİSKLİ"
}

@Composable
private fun riskColor(risk: WifiRisk): Color = when (risk) {
    WifiRisk.SAFE -> Color(0xFF2E7D32)
    WifiRisk.WARNING -> Color(0xFFE08A00)
    WifiRisk.DANGER -> MaterialTheme.colorScheme.error
}

@SuppressLint("MissingPermission")
private fun readWifi(context: Context): WifiSnapshot {
    return try {
    val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    if (wm == null) {
        WifiSnapshot(permissionMissing = true)
    } else {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val nearby = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else true
        val canRead = if (Build.VERSION.SDK_INT >= 33) (nearby || fine) else fine

        val enabled = try { wm.isWifiEnabled } catch (e: Exception) { false }
        if (!enabled) {
            return WifiSnapshot(enabled = false, permissionMissing = !canRead)
        }
        val ci = try { wm.connectionInfo } catch (e: Exception) { null }
        // Android 13+ NEARBY_WIFI_DEVICES (neverForLocation) ile SSID gizlenebilir;
        // bu durumda bağlantı yine de IP/ağ kimliği üzerinden tespit edilir.
        val connected = ci != null && (
            ci.ipAddress != 0 ||
                ci.networkId != -1 ||
                (ci.linkSpeed ?: -1) > 0
        )
        val ssid = if (canRead && ci != null) {
            try {
                ci.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
                    ?: if (connected) "Bağlı ağ (ad gizli)" else "—"
            } catch (e: Exception) { if (connected) "Bağlı ağ (ad gizli)" else "—" }
        } else if (connected) "Bağlı ağ (ad gizli)" else "—"
        val ip = try {
            if (ci != null && ci.ipAddress != 0) Formatter.formatIpAddress(ci.ipAddress) else "—"
        } catch (e: Exception) { "—" }
        val bssid = try {
            if (canRead) ci?.bssid?.takeIf { it.isNotBlank() && it != "02:00:00:00:00:00" } ?: "" else ""
        } catch (e: Exception) { "" }

        val raw = if (canRead) {
            try {
                wm.scanResults
            } catch (e: Exception) {
                emptyList()
            }
        } else emptyList()

        val secGroups = raw.groupBy { it.SSID?.trim('"') ?: "" }
            .filter { it.key.isNotBlank() }
            .filter { group -> group.value.map { securityOf(it.capabilities) }.distinct().size > 1 }

        val networks = raw
            .mapNotNull { sr ->
                val name = sr.SSID?.trim('"')?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val security = securityOf(sr.capabilities)
                var risk = riskOf(security)
                val notes = mutableListOf<String>()
                if (security == "Açık") notes += "Şifresiz ağ; trafik dinlenebilir."
                if (security == "WEP") notes += "WEP şifrelemesi kırılmıştır; güvenli değildir."
                if (security == "WPA") notes += "Eski WPA; WPA2/WPA3 tercih edin."
                if (secGroups.any { it.key == name }) {
                    risk = WifiRisk.DANGER
                    notes += "Aynı adlı ağ farklı güvenlik türleriyle yayın yapıyor (evil-twin şüphesi)."
                } else if (raw.count { (it.SSID ?: "").trim('"') == name } > 1) {
                    notes += "Birden fazla erişim noktası aynı adı yayınlıyor."
                }
                WifiNetwork(
                    ssid = name,
                    bssid = sr.BSSID ?: "",
                    security = security,
                    level = sr.level,
                    frequency = sr.frequency,
                    risk = risk,
                    notes = notes
                )
            }
            .distinctBy { it.ssid to it.bssid }
            .sortedWith(compareBy<WifiNetwork> { it.risk.ordinal }.reversed().thenByDescending { it.level })
            .take(25)

        val current = networks.firstOrNull { it.ssid == ssid }
        val currentSecurity = current?.security ?: if (connected) "Bilinmiyor" else "—"
        val currentRisk = when {
            current != null -> current.risk
            connected -> WifiRisk.WARNING
            else -> WifiRisk.SAFE
        }
        val notes = current?.notes ?: if (connected && current == null)
            listOf("Bağlı ağın güvenlik bilgisi taranmadı; ağları tarayın.") else emptyList()

        WifiSnapshot(
            enabled = true,
            connected = connected,
            ssid = ssid,
            bssid = bssid,
            security = currentSecurity,
            ip = ip,
            linkSpeed = try { ci?.linkSpeed ?: -1 } catch (e: Exception) { -1 },
            frequency = try { ci?.frequency ?: -1 } catch (e: Exception) { -1 },
            permissionMissing = !canRead,
            risk = currentRisk,
            notes = notes,
            networks = networks
        )
    }
    } catch (e: Exception) {
        WifiSnapshot(permissionMissing = true)
    }
}

@Composable
fun WifiRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refresh by remember { mutableIntStateOf(0) }
    var scanning by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val snapshot = remember(refresh) { readWifi(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refresh++
        message = "İzin durumu güncellendi"
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun requestPermissions() {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }.filter { !PermissionManager.status(context, it) }
        if (perms.isEmpty()) {
            refresh++
            try { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } catch (e: Exception) { }
        } else {
            permissionLauncher.launch(perms.toTypedArray())
        }
    }

    fun openWifiSettings() {
        try { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) } catch (e: Exception) {
            message = "Wi-Fi ayarları açılamadı"
        }
    }

    fun scanNetworks() {
        scanning = true
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wm.startScan()
            message = "Tarama isteği gönderildi; sonuçlar birkaç saniye içinde güncellenir"
        } catch (e: Exception) {
            message = "Ağ taraması başlatılamadı (sistem sınırı olabilir)"
        }
        scope.launch {
            delay(1800)
            scanning = false
            refresh++
        }
    }

    // Sayfa açıldığında önbellekte sonuç yoksa otomatik tara.
    LaunchedEffect(snapshot.permissionMissing, snapshot.enabled) {
        if (!snapshot.permissionMissing && snapshot.enabled && snapshot.networks.isEmpty()) {
            scanNetworks()
        }
    }

    NgBackground {
        Column(Modifier.fillMaxSize()) {
            NgTopBar(
                title = "Wi-Fi Güvenliği",
                subtitle = "Bağlı ağ ve çevredeki erişim noktaları",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { refresh++ }) {
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
                    InfoBanner(it, icon = Icons.Outlined.Info)
                    Spacer(Modifier.height(10.dp))
                }

                SectionCard(title = "Bağlı ağ", icon = Icons.Outlined.Wifi) {
                    if (!snapshot.enabled) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.WifiOff, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Wi-Fi kapalı", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Ağ güvenliğini değerlendirmek için Wi-Fi'yi açın.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { openWifiSettings() }, shape = RoundedCornerShape(12.dp)) {
                            Text("Wi-Fi ayarlarını aç")
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(riskColor(snapshot.risk).copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.Wifi,
                                    contentDescription = null,
                                    tint = riskColor(snapshot.risk),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (snapshot.connected) snapshot.ssid else "Bağlı değil",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    if (snapshot.connected) snapshot.security else "Bir ağa bağlanın",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (snapshot.connected) {
                                StatusPill(
                                    riskLabel(snapshot.risk),
                                    riskColor(snapshot.risk).copy(alpha = 0.12f),
                                    riskColor(snapshot.risk)
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        InfoRow("IP adresi", snapshot.ip)
                        if (snapshot.bssid.isNotBlank()) InfoRow("BSSID", snapshot.bssid, mono = true)
                        if (snapshot.linkSpeed > 0) InfoRow("Bağlantı hızı", "${snapshot.linkSpeed} Mbps")
                        if (snapshot.frequency > 0) InfoRow("Frekans", "${snapshot.frequency} MHz · ${bandOf(snapshot.frequency)}")
                        if (snapshot.notes.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            InfoBanner(
                                snapshot.notes.joinToString(" "),
                                icon = Icons.Outlined.Warning,
                                container = riskColor(snapshot.risk).copy(alpha = 0.10f),
                                contentColor = riskColor(snapshot.risk)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                if (snapshot.permissionMissing) {
                    SectionCard(title = "İzin gerekli", icon = Icons.Outlined.Lock) {
                        Text(
                            "Ağ adlarını ve güvenlik türlerini görmek için konum/yakın cihaz izni gerekir. " +
                                "NexGuard konumunuzu kaydetmez; yalnızca Wi-Fi tarama sonuçlarını okur.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { requestPermissions() }, shape = RoundedCornerShape(12.dp)) {
                                Text("İzin ver")
                            }
                            OutlinedButton(
                                onClick = {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                    } catch (e: Exception) { }
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("Konum ayarları") }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                } else if (!snapshot.enabled) {
                    // Wi-Fi kapalı mesajı yukarıda gösterildi.
                } else if (Build.VERSION.SDK_INT < 33 && !PermissionManager.locationEnabled(context)) {
                    SectionCard(title = "Konum servisi kapalı", icon = Icons.Outlined.LocationOff) {
                        Text(
                            "Android'de Wi-Fi tarama sonuçları konum servisi açık değilken boş dönebilir. " +
                                "Sonuçları görmek için konum servisini açın.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                try { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                                catch (e: Exception) { }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Konum ayarlarını aç") }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                Button(
                    onClick = { scanNetworks() },
                    enabled = snapshot.enabled && !snapshot.permissionMissing && !scanning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (scanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Taranıyor…")
                    } else {
                        Icon(Icons.Outlined.Radar, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Ağları tara")
                    }
                }

                Spacer(Modifier.height(14.dp))

                if (snapshot.networks.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Yakındaki ağlar",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${snapshot.networks.size} ağ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    snapshot.networks.forEach { network ->
                        NetworkCard(
                            network = network,
                            connected = network.ssid == snapshot.ssid && snapshot.connected,
                            onClick = { openWifiSettings() }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                } else if (snapshot.enabled && !snapshot.permissionMissing) {
                    EmptyState(
                        icon = Icons.Outlined.WifiFind,
                        title = "Ağ bulunamadı",
                        text = "Ağları tara butonuna basın. Android, arka arkaya taramaları sınırlayabilir; " +
                            "birkaç saniye sonra tekrar deneyin."
                    )
                }

                Spacer(Modifier.height(8.dp))
                SectionCard(title = "Evil-twin koruması", icon = Icons.Outlined.Security) {
                    Text(
                        "Aynı SSID'yi farklı güvenlik ayarlarıyla yayan ağlar şüpheli işaretlenir. " +
                            "Halka açık alanlarda şifresiz ağlarda bankacılık ve giriş işlemi yapmayın. " +
                            "Mümkünse WPA3, en az WPA2 kullanan ağları tercih edin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun NetworkCard(network: WifiNetwork, connected: Boolean, onClick: () -> Unit) {
    val color = riskColor(network.risk)
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (network.risk == WifiRisk.SAFE) MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
            else color.copy(alpha = 0.28f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (network.risk == WifiRisk.DANGER) Icons.Outlined.WifiOff else Icons.Outlined.Wifi,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        network.ssid,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (connected) {
                        Spacer(Modifier.width(6.dp))
                        StatusPill("BAĞLI", MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    }
                }
                Text(
                    "${network.security} · ${bandOf(network.frequency)} · ${network.level} dBm",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (network.notes.isNotEmpty()) {
                    Text(
                        network.notes.first(),
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                StatusPill(riskLabel(network.risk), color.copy(alpha = 0.12f), color)
                Spacer(Modifier.height(3.dp))
                Text(
                    when {
                        network.level >= -55 -> "Güçlü"
                        network.level >= -70 -> "Orta"
                        else -> "Zayıf"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
