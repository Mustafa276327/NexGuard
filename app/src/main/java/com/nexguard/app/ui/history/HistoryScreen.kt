package com.nexguard.app.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexguard.core.database.AppDatabase
import com.nexguard.core.database.ScanResultEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

private fun verdictColor(v: String): Color = when (v) {
    "DANGEROUS" -> Color(0xFFC62828)
    "SUSPICIOUS" -> Color(0xFFF9A825)
    else -> Color(0xFF2E7D32)
}

private fun verdictLabel(v: String): String = when (v) {
    "DANGEROUS" -> "TEHLİKELİ"
    "SUSPICIOUS" -> "ŞÜPHELİ"
    else -> "TEMİZ"
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val db: AppDatabase
) : ViewModel() {
    private val _items = MutableStateFlow<List<ScanResultEntity>>(emptyList())
    val items: StateFlow<List<ScanResultEntity>> = _items.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _items.value = try { db.scanResultDao().recent() } catch (e: Exception) { emptyList() }
        }
    }
}

@Composable
fun HistoryRoute(onBack: () -> Unit) {
    val vm: HistoryViewModel = hiltViewModel()
    val items by vm.items.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Geri") }
            Spacer(Modifier.width(4.dp))
            Column {
                Text("Tarama Geçmişi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${items.size} kayıt", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { vm.refresh() }) { Text("Yenile") }
        }
        Spacer(Modifier.height(8.dp))

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Henüz tarama yapılmadı.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.id }) { r ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    File(r.targetPath).name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    verdictLabel(r.verdict),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = verdictColor(r.verdict),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                r.targetPath,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(r.startedAt)),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text("Skor: ${r.score} · ${r.durationMs} ms", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
