package com.nexguard.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    lastScan: String = "—",
    score: Int = 100,
    threats: Int = 0,
    onScan: () -> Unit = {},
    onNavigate: (String) -> Unit = {}
) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))
        Text("NexGuard", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Güvenlik merkezi", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Güvenlik durumu", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Skor", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "$score / 100",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (score >= 70) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Son tarama", style = MaterialTheme.typography.bodyMedium)
                    Text(lastScan, style = MaterialTheme.typography.bodyMedium)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Tehdit kaydı", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (threats == 0) "Yok" else "$threats",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (threats == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onScan,
            modifier = Modifier.size(150.dp),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Text("TARA", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))

        val actions = listOf(
            "Dosya Tara" to "scan",
            "Wi-Fi" to "wifi",
            "Uygulamalar" to "apps",
            "Cihaz" to "device",
            "Siteler" to "sites",
            "Geçmiş" to "history",
            "Karantina" to "quarantine",
            "Ayarlar" to "settings"
        )
        actions.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, route) ->
                    OutlinedButton(
                        onClick = { onNavigate(route) },
                        modifier = Modifier.weight(1f)
                    ) { Text(label) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun PlaceholderScreen(title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
