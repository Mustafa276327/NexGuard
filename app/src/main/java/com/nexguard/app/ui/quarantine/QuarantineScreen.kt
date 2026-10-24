package com.nexguard.app.ui.quarantine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexguard.app.ui.components.*
import com.nexguard.app.util.fileTypeIcon
import com.nexguard.app.util.formatDateTime
import com.nexguard.app.util.humanBytes
import com.nexguard.core.database.AppDatabase
import com.nexguard.core.database.QuarantineEntity
import com.nexguard.feature.quarantine.QuarantineManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class QuarantineViewModel @Inject constructor(
    private val manager: QuarantineManager,
    private val db: AppDatabase
) : ViewModel() {

    data class UiState(
        val items: List<QuarantineEntity> = emptyList(),
        val busyId: String? = null,
        val message: String? = null,
        val loading: Boolean = true
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val items = try { db.quarantineDao().all() } catch (e: Exception) { emptyList() }
            _state.update { it.copy(items = items, loading = false) }
        }
    }

    fun restore(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(busyId = id) }
            val outcome = manager.restore(id)
            _state.update {
                it.copy(
                    busyId = null,
                    message = outcome.message.ifBlank { if (outcome.success) "Dosya geri yüklendi" else "Geri yüklenemedi" }
                )
            }
            refresh()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(busyId = id) }
            val outcome = manager.delete(id)
            _state.update {
                it.copy(
                    busyId = null,
                    message = outcome.message.ifBlank { if (outcome.success) "Dosya silindi" else "Silinemedi" }
                )
            }
            refresh()
        }
    }

    fun deleteAll() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(loading = true) }
            var failed = 0
            _state.value.items.forEach { entity ->
                if (!manager.delete(entity.id).success) failed++
            }
            _state.update {
                it.copy(
                    loading = false,
                    message = if (failed == 0) "Tüm karantina dosyaları silindi"
                    else "$failed dosya silinemedi"
                )
            }
            refresh()
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }
}

@Composable
fun QuarantineRoute(onBack: () -> Unit) {
    val vm: QuarantineViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    var confirmDelete by remember { mutableStateOf<QuarantineEntity?>(null) }
    var confirmRestore by remember { mutableStateOf<QuarantineEntity?>(null) }
    var confirmAll by remember { mutableStateOf(false) }

    val totalSize = remember(state.items) { state.items.sumOf { it.sizeBytes } }
    val encryptedCount = remember(state.items) { state.items.count { it.encrypted } }

    NgBackground {
        Column(Modifier.fillMaxSize()) {
            NgTopBar(
                title = "Karantina",
                subtitle = "${state.items.size} dosya · ${humanBytes(totalSize)} · $encryptedCount şifreli",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Yenile")
                    }
                    if (state.items.isNotEmpty()) {
                        IconButton(onClick = { confirmAll = true }) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = "Tümünü sil")
                        }
                    }
                }
            )

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                state.message?.let { msg ->
                    item {
                        InfoBanner(
                            msg,
                            icon = if (msg.contains("başarısız", true) || msg.contains("silinemedi", true)) Icons.Outlined.Warning
                            else Icons.Outlined.CheckCircle,
                            container = if (msg.contains("başarısız", true) || msg.contains("silinemedi", true))
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                            contentColor = if (msg.contains("başarısız", true) || msg.contains("silinemedi", true))
                                MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                item {
                    InfoBanner(
                        "Karantinadaki dosyalar uygulamanın özel alanında AES-256-GCM ile şifrelenir; " +
                            "diğer uygulamalar ve dosya yöneticileri erişemez. Geri yüklerken bütünlük doğrulanır.",
                        icon = Icons.Outlined.Lock,
                        container = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                }

                if (state.loading && state.items.isEmpty()) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 60.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }
                    }
                } else if (state.items.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.VerifiedUser,
                            title = "Karantina boş",
                            text = "Tehdit bulunduğunda dosyaları buraya taşıyarak izole edebilirsiniz. " +
                                "Karantina, dosyayı silmez; güvenli biçimde saklar."
                        )
                    }
                } else {
                    items(state.items, key = { it.id }) { entity ->
                        QuarantineCard(
                            entity = entity,
                            busy = state.busyId == entity.id,
                            onRestore = { confirmRestore = entity },
                            onDelete = { confirmDelete = entity }
                        )
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    confirmRestore?.let { entity ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            icon = { Icon(Icons.Outlined.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Dosya geri yüklensin mi?") },
            text = {
                Text(
                    "${File(entity.originalPath).name} özgün konumuna geri yüklenecek. " +
                        "Karantinaya alınma nedeni: ${entity.reason.ifBlank { "bilinmiyor" }}. " +
                        "Dosya zararlıysa açmayın; geri yüklemek cihazı riske atabilir."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.restore(entity.id)
                        confirmRestore = null
                    }
                ) { Text("Geri yükle") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = null }) { Text("Vazgeç") }
            }
        )
    }

    confirmDelete?.let { entity ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            icon = { Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Karantinadan silinsin mi?") },
            text = {
                Text(
                    "${File(entity.originalPath).name} kalıcı olarak silinecek. " +
                        "Geri yükleme artık mümkün olmayacak."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.delete(entity.id); confirmDelete = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Kalıcı sil") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Vazgeç") }
            }
        )
    }

    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            icon = { Icon(Icons.Outlined.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Tüm karantina silinsin mi?") },
            text = { Text("${state.items.size} dosya kalıcı olarak silinecek. Bu işlem geri alınamaz.") },
            confirmButton = {
                TextButton(
                    onClick = { vm.deleteAll(); confirmAll = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Tümünü sil") }
            },
            dismissButton = {
                TextButton(onClick = { confirmAll = false }) { Text("Vazgeç") }
            }
        )
    }
}

@Composable
private fun QuarantineCard(
    entity: QuarantineEntity,
    busy: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val file = File(entity.originalPath)
    val color = Color(0xFFE08A00)
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        fileTypeIcon(file.extension),
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(21.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        file.name.ifBlank { entity.originalPath },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOfNotNull(
                            entity.threatType.ifBlank { null },
                            humanBytes(entity.sizeBytes),
                            formatDateTime(entity.createdAt)
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (entity.encrypted) {
                    StatusPill(
                        "AES-256",
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                entity.originalPath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (entity.reason.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Sebep: ${entity.reason}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRestore,
                    enabled = !busy,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Geri yükle")
                    }
                }
                OutlinedButton(
                    onClick = onDelete,
                    enabled = !busy,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Sil")
                }
            }
            if (entity.encrypted) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Şifreli saklanıyor · ${entity.storedName.take(8)}…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
