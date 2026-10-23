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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import com.nexguard.core.common.Severity
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

private fun ruleLabel(rule: String): String = when (rule.uppercase()) {
    "POLYGLOT" -> "Çoklu format (polyglot)"
    "RTLO" -> "Sağdan sola gizleme (RTLO)"
    "EXECUTABLE_DISGUISED" -> "Gizlenmiş çalıştırılabilir"
    "HIGH_ENTROPY" -> "Yüksek entropi (şifrelenmiş/paketlenmiş)"
    "ENTROPY" -> "Yüksek entropi"
    "DANGEROUS_PERMISSION" -> "Tehlikeli izin"
    "KNOWN_MALWARE" -> "Bilinen zararlı yazılım"
    "SUSPICIOUS_URL" -> "Şüpheli bağlantı"
    "IOC" -> "Zararlı gösterge (IOC)"
    "APK_DEBUG" -> "Debug imzalı APK"
    else -> rule
}

private fun sevLabel(s: Severity): String = when (s) {
    Severity.CRITICAL -> "KRİTİK"
    Severity.HIGH -> "YÜKSEK"
    Severity.MEDIUM -> "ORTA"
    Severity.LOW -> "DÜŞÜK"
    Severity.INFO -> "BİLGİ"
}

private fun sevColor(s: Severity): Color = when (s) {
    Severity.CRITICAL -> Color(0xFFB71C1C)
    Severity.HIGH -> Color(0xFFE65100)
    Severity.MEDIUM -> Color(0xFFF9A825)
    Severity.LOW -> Color(0xFF1565C0)
    Severity.INFO -> Color(0xFF546E7A)
}

@Composable
fun ScanRoute(onBack: () -> Unit) {
    val vm: ScanViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(hasStorageAccess(context)) }
    var selected by remember { mutableStateOf<ScanResult?>(null) }

    LaunchedEffect(hasAccess) {
        if (hasAccess && !state.running && !state.finished && !state.stopped) vm.scan(ScanMode.FULL)
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
                        "Tüm dosyaları ve kurulu uygulamaları taramak için NexGuard'a tüm dosyalara erişim izni verin.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { openPermissionSettings() }) { Text("İzin Ver") }
                        OutlinedButton(onClick = { hasAccess = hasStorageAccess(context) }) { Text("İzni Kontrol Et") }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                val fraction = if (state.total > 0) state.scanned.toFloat() / state.total else 0f
                LinearProgressIndicator(
                    progress = fraction,
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (state.preparing) "Dosyalar hazırlanıyor…" else "Taranan: ${state.scanned} / ${state.total}",
                        style = MaterialTheme.typography.bodyMedium
                    )
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
                            state.preparing -> "Dosya listesi oluşturuluyor…"
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
                state.message?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Canlı Akış", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("${state.feed.size} kayıt · dokun ve incele", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(4.dp))
        LazyColumn(Modifier.weight(1f), reverseLayout = true) {
            items(state.feed, key = { it.id }) { r ->
                FeedRow(r, onClick = { selected = r })
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
                Button(onClick = { vm.scan(ScanMode.FULL) }, modifier = Modifier.weight(1f).height(52.dp)) {
                    Text("Tam Tara")
                }
                OutlinedButton(onClick = { vm.scan(ScanMode.QUICK) }, modifier = Modifier.weight(1f).height(52.dp)) {
                    Text("Hızlı Tara")
                }
            }
        }

        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text("Hata: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }

    selected?.let { r ->
        ScanDetailDialog(
            result = r,
            onQuarantine = { vm.quarantine(r); selected = null },
            onDismiss = { selected = null }
        )
    }
}

@Composable
private fun FeedRow(r: ScanResult, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(verdictColor(r.verdict)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                File(r.targetPath).name.ifEmpty { r.targetPath },
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

@Composable
private fun ScanDetailDialog(
    result: ScanResult,
    onQuarantine: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(File(result.targetPath).name.ifEmpty { "Dosya" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${verdictLabel(result.verdict)} · Skor ${result.score}/100",
                    style = MaterialTheme.typography.bodySmall,
                    color = verdictColor(result.verdict),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(result.targetPath, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "SHA-256: ${result.sha256.take(24)}${if (result.sha256.length > 24) "…" else ""}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text("Süre: ${result.durationMs} ms", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))

                if (result.findings.isEmpty()) {
                    Text(
                        if (result.verdict == Verdict.CLEAN) "Tehdit bulgusu yok." else "Bulgular kaydedilmedi.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text("Tespitler (${result.findings.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    result.findings.forEach { f ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Column(Modifier.padding(10.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        ruleLabel(f.ruleId),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        sevLabel(f.severity),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = sevColor(f.severity),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text("Dedektör: ${f.detector} · Ağırlık: ${f.weight}", style = MaterialTheme.typography.bodySmall)
                                if (f.evidence.isNotEmpty()) {
                                    Spacer(Modifier.height(2.dp))
                                    f.evidence.entries.take(4).forEach { (k, v) ->
                                        Text("$k: $v", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (result.verdict != Verdict.CLEAN) {
                TextButton(onClick = onQuarantine) { Text("Karantinaya Al") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Kapat") }
        }
    )
}
