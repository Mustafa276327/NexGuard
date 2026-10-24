package com.nexguard.app.ui.log

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexguard.app.ui.components.*
import com.nexguard.app.util.LogLevel
import com.nexguard.app.util.NgLog
import com.nexguard.app.util.NgLogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LogFilter { ALL, INFO, WARN, ERROR }

private fun levelColor(level: LogLevel): Color = when (level) {
    LogLevel.INFO -> Color(0xFF2E7D32)
    LogLevel.WARN -> Color(0xFFE08A00)
    LogLevel.ERROR -> Color(0xFFC62828)
}

private fun levelLabel(level: LogLevel): String = when (level) {
    LogLevel.INFO -> "BİLGİ"
    LogLevel.WARN -> "UYARI"
    LogLevel.ERROR -> "HATA"
}

@Composable
fun LogRoute(onBack: () -> Unit) {
    val entries by NgLog.entries.collectAsState()
    val context = LocalContext.current
    var filter by remember { mutableStateOf(LogFilter.ALL) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    val filtered = remember(entries, filter) {
        when (filter) {
            LogFilter.ALL -> entries
            LogFilter.INFO -> entries.filter { it.level == LogLevel.INFO }
            LogFilter.WARN -> entries.filter { it.level == LogLevel.WARN }
            LogFilter.ERROR -> entries.filter { it.level == LogLevel.ERROR }
        }.asReversed()
    }

    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(3000)
            message = null
        }
    }

    fun copyAll() {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("NexGuard log", NgLog.asText()))
            message = "Log panoya kopyalandı"
        } catch (e: Exception) {
            message = "Log kopyalanamadı"
        }
    }

    NgBackground {
        Column(Modifier.fillMaxSize()) {
            NgTopBar(
                title = "Canlı Log",
                subtitle = "${entries.size} kayıt · yalnızca bellekte",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { copyAll() }, enabled = entries.isNotEmpty()) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Kopyala")
                    }
                    IconButton(onClick = { confirmClear = true }, enabled = entries.isNotEmpty()) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = "Temizle")
                    }
                }
            )

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                message?.let {
                    item {
                        InfoBanner(
                            it,
                            icon = Icons.Outlined.CheckCircle,
                            container = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                            contentColor = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = filter == LogFilter.ALL,
                            onClick = { filter = LogFilter.ALL },
                            label = { Text("Tümü") }
                        )
                        FilterChip(
                            selected = filter == LogFilter.INFO,
                            onClick = { filter = LogFilter.INFO },
                            label = { Text("Bilgi") }
                        )
                        FilterChip(
                            selected = filter == LogFilter.WARN,
                            onClick = { filter = LogFilter.WARN },
                            label = { Text("Uyarı") }
                        )
                        FilterChip(
                            selected = filter == LogFilter.ERROR,
                            onClick = { filter = LogFilter.ERROR },
                            label = { Text("Hata") }
                        )
                    }
                }

                item {
                    Text(
                        "Log kayıtları süreç kapanınca silinir. Dosya yolu ve kişisel içerik yazılmaz; " +
                            "yalnızca dosya adı ve işlem sonucu tutulur.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (filtered.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.BugReport,
                            title = if (entries.isEmpty()) "Henüz log yok" else "Bu filtrede kayıt yok",
                            text = if (entries.isEmpty())
                                "Tarama yaptığınızda ve izleme servisi çalıştığında olaylar burada görünür."
                            else "Başka bir önem düzeyi seçin."
                        )
                    }
                } else {
                    items(filtered, key = { it.id }) { entry ->
                        LogCard(entry)
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Log temizlensin mi?") },
            text = { Text("Bellekteki ${entries.size} kayıt silinecek. Bu işlem geri alınamaz.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        NgLog.clear()
                        confirmClear = false
                        message = "Log temizlendi"
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Temizle") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Vazgeç") }
            }
        )
    }
}

@Composable
private fun LogCard(entry: NgLogEntry) {
    val color = levelColor(entry.level)
    val time = remember(entry.time) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(entry.time))
    }
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    levelLabel(entry.level),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.tag,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        time,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    entry.message,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
