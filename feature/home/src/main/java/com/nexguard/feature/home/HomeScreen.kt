package com.nexguard.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    lastScan: String = "—",
    score: Int = 100,
    onScan: () -> Unit = {},
    onNavigate: (String) -> Unit = {}
) {
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(32.dp))
        Button(onClick = onScan, modifier = Modifier.size(160.dp)) { Text("TARA", style = MaterialTheme.typography.headlineMedium) }
        Spacer(Modifier.height(16.dp))
        Text("Son tarama: $lastScan")
        Text("Güvenlik skoru: $score")
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onNavigate("scan") }) { Text("Dosya Tara") }
            Button(onClick = { onNavigate("wifi") }) { Text("Wi-Fi") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onNavigate("apps") }) { Text("Uygulamalar") }
            Button(onClick = { onNavigate("device") }) { Text("Cihaz") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onNavigate("sites") }) { Text("Siteler") }
            Button(onClick = { onNavigate("history") }) { Text("Geçmiş") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onNavigate("quarantine") }) { Text("Karantina") }
            Button(onClick = { onNavigate("settings") }) { Text("Ayarlar") }
        }
    }
}