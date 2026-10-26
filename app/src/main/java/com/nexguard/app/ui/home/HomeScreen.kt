package com.nexguard.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexguard.app.ui.components.ScoreRing
import com.nexguard.app.ui.components.SectionCard
import com.nexguard.app.scan.ScanMode
import com.nexguard.app.scan.ScanUiState
import com.nexguard.app.util.relativeTime

private data class QuickAction(
    val title: String,
    val subtitle: String,
    val route: String,
    val icon: ImageVector
)

@Composable
fun HomeScreen(
    ui: HomeUi,
    scanState: ScanUiState = ScanUiState(),
    onScan: () -> Unit,
    onStopScan: () -> Unit = {},
    onNavigate: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val actions = listOf(
        QuickAction("Dosya Tara", "Tam veya hızlı tarama", "scan", Icons.Outlined.Radar),
        QuickAction("Wi-Fi", "Ağ güvenlik kontrolü", "wifi", Icons.Outlined.Wifi),
        QuickAction("Uygulamalar", "İzin ve risk analizi", "apps", Icons.Outlined.Apps),
        QuickAction("Cihaz", "Güvenlik durumu", "device", Icons.Outlined.Smartphone),
        QuickAction("Site Kontrolü", "Şüpheli bağlantılar", "sites", Icons.Outlined.Language),
        QuickAction("Geçmiş", "Tarama kayıtları", "history", Icons.Outlined.History),
        QuickAction("Karantina", "İzole edilen dosyalar", "quarantine", Icons.Outlined.Lock),
        QuickAction("Ayarlar", "Koruma tercihleri", "settings", Icons.Outlined.Settings)
    )

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "NexGuard",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Cihaz güvenlik merkezi",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Yenile")
                }
                IconButton(onClick = { onNavigate("settings") }) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Ayarlar")
                }
            }
        }

        if (scanState.running || scanState.preparing) {
            item {
                SectionCard(title = "Tarama devam ediyor", icon = Icons.Outlined.Radar) {
                    Text(
                        "${scanState.label}: ${scanState.scanned}/${scanState.total} dosya",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    val progress = if (scanState.total > 0) {
                        scanState.scanned.toFloat() / scanState.total
                    } else 0f
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (scanState.current.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            scanState.current,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onStopScan, shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Durdur")
                    }
                }
            }
        }

        item { HeroCard(ui) }

        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onScan)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Radar,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "TARA",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "Tüm cihazı tara",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        }

        item {
            SectionCard(title = "Koruma durumu", icon = Icons.Outlined.GppGood) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusTile(
                        "İzleme",
                        if (ui.monitorOn) "Açık" else "Kapalı",
                        ui.monitorOn,
                        Icons.Outlined.Wifi,
                        Modifier.weight(1f)
                    ) { onNavigate("settings") }
                    StatusTile(
                        "Depolama",
                        if (ui.storageOk) "Tam erişim" else "Eksik",
                        ui.storageOk,
                        Icons.Outlined.Folder,
                        Modifier.weight(1f)
                    ) { onNavigate("settings") }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusTile(
                        "Yönetici",
                        if (ui.adminOk) "Etkin" else "İsteğe bağlı",
                        ui.adminOk,
                        Icons.Outlined.AdminPanelSettings,
                        Modifier.weight(1f)
                    ) { onNavigate("settings") }
                    StatusTile(
                        "Bildirim",
                        if (ui.notifyOk) "Açık" else "Kapalı",
                        ui.notifyOk,
                        Icons.Outlined.Notifications,
                        Modifier.weight(1f)
                    ) { onNavigate("settings") }
                }
            }
        }

        if (ui.recentThreats.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Son tespitler",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onNavigate("history") }) {
                        Text("Tümü")
                    }
                }
            }
            items(ui.recentThreats) { threat ->
                RecentThreatRow(threat) { onNavigate("history") }
            }
        }

        item {
            Text(
                "Araçlar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        items(actions.chunked(2)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { action ->
                    ActionCard(
                        action = action,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(action.route) }
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "NexGuard ${ui.version}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Veriler cihazda kalır · çevrimdışı koruma",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HeroCard(ui: HomeUi) {
    val gradient = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary
        )
    )
    Card(
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(Modifier.background(gradient).padding(20.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.16f),
                            contentColor = Color.White
                        ) {
                            Text(
                                if (ui.monitorOn) "KORUMA AKTİF" else "KORUMA KAPALI",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            when {
                                ui.threats > 0 -> "${ui.threats} tehdit bulundu"
                                ui.issues > 0 -> "Bazı dosyalar taranamadı"
                                ui.hasSession -> "Cihazın güvende"
                                else -> "İlk taramanı yap"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            when {
                                !ui.monitorOn -> "Gerçek zamanlı izleme kapalı"
                                ui.storageOk -> "Gerçek zamanlı izleme açık"
                                else -> "Tam tarama için depolama izni gerekli"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
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
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroMetric("Son tarama", ui.lastScan, Modifier.weight(1f))
                    HeroMetric(
                        "Taranan",
                        if (ui.total > 0) "${ui.total} dosya" else "—",
                        Modifier.weight(1f)
                    )
                    HeroMetric(
                        "Tehdit",
                        if (ui.threats > 0) "${ui.threats}" else "Yok",
                        Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.75f),
            maxLines = 1
        )
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StatusTile(
    label: String,
    value: String,
    ok: Boolean,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val color = if (ok) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (ok) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = color)
        }
        Icon(
            if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun RecentThreatRow(threat: HomeThreat, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.error.copy(alpha = 0.22f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    threat.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${threat.threatType} · ${relativeTime(threat.time)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "${threat.score}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ActionCard(
    action: QuickAction,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
        ),
        modifier = modifier
            .height(112.dp)
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(14.dp)) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    action.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(21.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                action.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                action.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
