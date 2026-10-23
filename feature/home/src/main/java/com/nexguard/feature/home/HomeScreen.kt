package com.nexguard.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
    val gradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary
        )
    )
    val onHero = MaterialTheme.colorScheme.onPrimary

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))

        // Hero kart
        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().background(gradient).padding(20.dp)
            ) {
                Text(
                    "NexGuard",
                    style = MaterialTheme.typography.headlineMedium,
                    color = onHero,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Güvenlik merkezi",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onHero.copy(alpha = 0.85f)
                )
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "$score",
                        style = MaterialTheme.typography.headlineLarge,
                        color = onHero,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        " / 100",
                        style = MaterialTheme.typography.titleMedium,
                        color = onHero.copy(alpha = 0.85f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Text(
                    "Güvenlik skoru",
                    style = MaterialTheme.typography.bodySmall,
                    color = onHero.copy(alpha = 0.85f)
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "Son tarama: $lastScan",
                        style = MaterialTheme.typography.bodySmall,
                        color = onHero.copy(alpha = 0.85f)
                    )
                    Text(
                        if (threats == 0) "Tehdit yok" else "$threats tehdit",
                        style = MaterialTheme.typography.bodySmall,
                        color = onHero,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onScan,
            modifier = Modifier.size(150.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("TARA", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("tüm cihaz", style = MaterialTheme.typography.bodySmall)
            }
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
                    FilledTonalButton(
                        onClick = { onNavigate(route) },
                        modifier = Modifier.weight(1f).height(48.dp)
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
