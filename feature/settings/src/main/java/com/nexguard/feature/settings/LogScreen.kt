package com.nexguard.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LogScreen(lines: List<String>) {
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Text("Canlı Log", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(lines) { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}