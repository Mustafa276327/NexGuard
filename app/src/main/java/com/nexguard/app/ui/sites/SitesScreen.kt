package com.nexguard.app.ui.sites

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val PREFS = "nexguard_prefs"
private const val KEY_SITES = "blocked_sites"

private fun loadSites(context: Context): List<String> {
    val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SITES, "") ?: ""
    return raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
}

private fun saveSites(context: Context, sites: List<String>) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putString(KEY_SITES, sites.joinToString("\n")).apply()
}

private fun normalizeDomain(input: String): String {
    var d = input.trim().lowercase()
    d = d.removePrefix("https://").removePrefix("http://")
    d = d.substringBefore("/")
    return d
}

private fun isValidDomain(d: String): Boolean =
    d.contains(".") && !d.contains(" ") && d.length in 4..253 &&
        d.all { it.isLetterOrDigit() || it == '.' || it == '-' }

@Composable
fun SitesRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    var sites by remember { mutableStateOf(loadSites(context)) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun add() {
        val d = normalizeDomain(input)
        if (!isValidDomain(d)) {
            error = "Geçerli bir alan adı girin (örn: ornek.com)"
            return
        }
        if (sites.contains(d)) {
            error = "Bu alan adı zaten listede"
            return
        }
        sites = sites + d
        saveSites(context, sites)
        input = ""
        error = null
    }

    fun remove(d: String) {
        sites = sites - d
        saveSites(context, sites)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Geri") }
            Spacer(Modifier.width(4.dp))
            Column {
                Text("Siteler", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${sites.size} alan adı engelleniyor", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(12.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Engel listesi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Bu alan adları güvenli olmayan siteler listesine eklenir ve tarayıcı uyarılarında kullanılır.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; error = null },
                    label = { Text("Alan adı ekle (örn: ornek.com)") },
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { add() }, enabled = input.isNotBlank()) { Text("Ekle") }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (sites.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Henüz engellenen site yok.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(sites, key = { it }) { d ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(d, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            TextButton(onClick = { remove(d) }) { Text("Kaldır") }
                        }
                    }
                }
            }
        }
    }
}
