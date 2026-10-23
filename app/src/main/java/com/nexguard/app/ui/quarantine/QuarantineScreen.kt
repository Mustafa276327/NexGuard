package com.nexguard.app.ui.quarantine

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexguard.core.database.AppDatabase
import com.nexguard.core.database.QuarantineEntity
import com.nexguard.feature.quarantine.QuarantineManager
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

@HiltViewModel
class QuarantineViewModel @Inject constructor(
    private val manager: QuarantineManager,
    private val db: AppDatabase
) : ViewModel() {

    private val _items = MutableStateFlow<List<QuarantineEntity>>(emptyList())
    val items: StateFlow<List<QuarantineEntity>> = _items.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _items.value = try { db.quarantineDao().all() } catch (e: Exception) { emptyList() }
        }
    }

    fun restore(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = try { manager.restore(id) } catch (e: Exception) { false }
            _message.value = if (ok) "Dosya geri yüklendi" else "Geri yüklenemedi"
            _items.value = try { db.quarantineDao().all() } catch (e: Exception) { emptyList() }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = try { manager.delete(id) } catch (e: Exception) { false }
            _message.value = if (ok) "Dosya silindi" else "Silinemedi"
            _items.value = try { db.quarantineDao().all() } catch (e: Exception) { emptyList() }
        }
    }
}

@Composable
fun QuarantineRoute(onBack: () -> Unit) {
    val vm: QuarantineViewModel = hiltViewModel()
    val items by vm.items.collectAsState()
    val message by vm.message.collectAsState()
    var confirmDelete by remember { mutableStateOf<QuarantineEntity?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Geri") }
            Spacer(Modifier.width(4.dp))
            Text("Karantina", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("${items.size}", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))

        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
        }

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Karantina boş. Şüpheli dosyalar burada saklanır.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.id }) { e ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                File(e.originalPath).name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                e.originalPath,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("Sebep: ${e.reason}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(e.createdAt)),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { vm.restore(e.id) }) { Text("Geri Yükle") }
                                OutlinedButton(onClick = { confirmDelete = e }) { Text("Sil") }
                            }
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Dosyayı sil?") },
            text = { Text("${File(target.originalPath).name} kalıcı olarak silinecek.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(target.id); confirmDelete = null }) { Text("Sil") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Vazgeç") }
            }
        )
    }
}
