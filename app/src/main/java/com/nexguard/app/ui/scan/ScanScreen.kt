package com.nexguard.app.ui.scan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexguard.core.common.ScanResult
import com.nexguard.core.common.Verdict
import java.io.File

private fun hasStorageAccess(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

private fun verdictColor(v: Verdict): Color = when (v) {
    Verdict.CLEAN -> Color(0xFF2E7D32)
    Verdict.SUSPICIOUS -> Color(0xFFF9A825)
    Verdict.DANGEROUS -> Color(0xFFC62828)
}

private fun verdictLabel(v: Verdict): String = when (v) {
    Verdict.CLEAN -> "TEMİZ"
    Verdict.SUSPICIOUS -> "ŞÜPHELİ"
    Verdict.DANGEROUS -> "TEHLİKELİ"
}

@Composable
fun ScanRoute(onBack: () -> Unit) {
    val vm: ScanViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(hasStorageAccess(context)) }

    // İzin verildiğinde taramayı otomatik başlat
    LaunchedEffect(hasAccess) {
        if (hasAccess && !state.running && !state.finished && !state.stopped) vm.scan()
    }

    fun openPermissionSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        }
        try { context.startActivity(intent) } catch (e: Exception) { }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // Başlık
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Geri") }
            Spacer(Modifier.width(4.dp))
            Column {
                Text("Dosya Tarama", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    state.root.ifEmpty { vm.defaultRoot() },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        if (!hasAccess) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Depolama erişimi gerekli", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Dosyaları tarayabilmek için NexGuard'a tüm dosyalara erişim izni verin.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { openPermissionSettings() }) { Text("İzin Ver") }
                        OutlinedButton(onClick = { hasAccess = hasStorageAccess(context) }) { Text("İzni Kontrol Et") }
                    }
                    TextButton(onClick = { vm.scan(context.filesDir.absolutePath) }) {
                        Text("Yine de uygulama içi dosyaları tara")
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // İlerleme kartı
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                val fraction = if (state.total > 0) state.scanned.toFloat() / state.total else 0f
                LinearProgressIndicator(
                    progress = fraction,
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Taranan: ${state.scanned} / ${state.total}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Tehdit: ${state.threats}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.threats > 0) Color(0xFFC62828) else Color(0xFF2E7D32),
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.running) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        when {
                            state.running -> "Taranıyor: ${state.current}"
                            state.stopped -> "Tarama durduruldu"
                            state.finished -> "Tarama tamamlandı"
                            else -> "Hazır"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        // Canlı akış (en yeni en altta)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Canlı Akış", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("${state.feed.size} kayıt", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(4.dp))
        LazyColumn(Modifier.weight(1f), reverseLayout = true) {
            items(state.feed, key = { it.id }) { r ->
                FeedRow(r)
            }
        }

        Spacer(Modifier.height(10.dp))
        if (state.running) {
            Button(
                onClick = { vm.stop() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("DURDUR", fontWeight = FontWeight.Bold)
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.scan() }, modifier = Modifier.weight(1f).height(52.dp)) {
                    Text("Tekrar Tara")
                }
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).height(52.dp)) {
                    Text("Geri")
                }
            }
        }

        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text("Hata: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FeedRow(r: ScanResult) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(verdictColor(r.verdict)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                File(r.targetPath).name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                File(r.targetPath).parent ?: "",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                verdictLabel(r.verdict),
                style = MaterialTheme.typography.labelSmall,
                color = verdictColor(r.verdict),
                fontWeight = FontWeight.Bold
            )
            Text("${r.score}", style = MaterialTheme.typography.labelSmall)
        }
    }
}
