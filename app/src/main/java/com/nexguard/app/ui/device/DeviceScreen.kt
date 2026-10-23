package com.nexguard.app.ui.device

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexguard.app.admin.DeviceAdminReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private fun detectRoot(): Boolean {
    val paths = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
        "/system/sd/xbin/su", "/system/app/Superuser.apk", "/data/local/xbin/su"
    )
    if (paths.any { File(it).exists() }) return true
    return try {
        val p = ProcessBuilder("which", "su").start()
        val out = p.inputStream.bufferedReader().readText().trim()
        p.waitFor()
        out.isNotEmpty()
    } catch (e: Exception) {
        false
    }
}

private fun selinuxStatus(): String = try {
    File("/sys/fs/selinux/enforce").readText().trim().let { if (it == "1") "Enforcing" else "Permissive" }
} catch (e: Exception) {
    "Bilinmiyor"
}

@Composable
fun DeviceRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val dpm = remember { context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager }
    val admin = remember { ComponentName(context, DeviceAdminReceiver::class.java) }
    var adminActive by remember { mutableStateOf(dpm.isAdminActive(admin)) }
    var rooted by remember { mutableStateOf<Boolean?>(null) }
    var selinux by remember { mutableStateOf("Kontrol ediliyor…") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            rooted = detectRoot()
            selinux = selinuxStatus()
        }
    }

    val adbEnabled = remember {
        try { Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1 } catch (e: Exception) { false }
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Geri") }
            Spacer(Modifier.width(4.dp))
            Text("Cihaz Güvenliği", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { adminActive = dpm.isAdminActive(admin) }) { Text("Yenile") }
        }
        Spacer(Modifier.height(12.dp))

        InfoCard("Cihaz", "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}")
        InfoCard("Android", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        InfoCard("Güvenlik yaması", Build.VERSION.SECURITY_PATCH.ifBlank { "—" })
        InfoCard("SELinux", selinux)
        InfoCard(
            "Root durumu",
            when (rooted) {
                null -> "Kontrol ediliyor…"
                true -> "Root izi bulundu (riskli)"
                false -> "Root izi yok"
            }
        )
        InfoCard(
            "Geliştirici seçenekleri",
            if (adbEnabled) "USB hata ayıklama açık" else "Kapalı"
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Cihaz Yöneticisi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (adminActive) "Etkin — koruma özellikleri kullanılabilir" else "Devre dışı",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (adminActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(8.dp))
                if (adminActive) {
                    OutlinedButton(onClick = {
                        try { dpm.removeActiveAdmin(admin); adminActive = false } catch (e: Exception) { }
                    }) { Text("Devre Dışı Bırak") }
                } else {
                    Button(onClick = {
                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "NexGuard'ın koruma özellikleri için cihaz yöneticisi izni.")
                        }
                        try { context.startActivity(intent) } catch (e: Exception) { }
                    }) { Text("Cihaz Yöneticisini Etkinleştir") }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun InfoCard(label: String, value: String) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
    Spacer(Modifier.height(8.dp))
}
