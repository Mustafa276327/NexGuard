package com.nexguard.feature.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    var step by remember { mutableStateOf(0) }
    val steps = listOf("Kısıtlı ayarlar", "Çalışma zamanı izinleri", "Özet")
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("NexGuard Kurulum", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text("Adım ${step + 1}/${steps.size}: ${steps[step]}")
        Spacer(Modifier.height(24.dp))
        Button(onClick = {
            if (step < steps.lastIndex) step++ else onFinished()
        }) { Text(if (step < steps.lastIndex) "İleri" else "Bitir") }
    }
}