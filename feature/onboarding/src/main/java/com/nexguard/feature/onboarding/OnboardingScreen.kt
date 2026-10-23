package com.nexguard.feature.onboarding

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexguard.feature.onboarding.PermissionManager

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    adminReceiver: ComponentName,
    context: Context
) {
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    var step by remember { mutableStateOf(0) }

    // API 33+ gerektiren medya izinlerini düşük API'de atla
    val permissionsToRequest = remember {
        PermissionManager.runtimePermissions.filter { p ->
            when (p) {
                "android.permission.READ_MEDIA_IMAGES",
                "android.permission.READ_MEDIA_VIDEO",
                "android.permission.READ_MEDIA_AUDIO" -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                else -> true
            }
        }
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) {
            step = 2
        }
    }

    val isAdminActive = dpm.isAdminActive(adminReceiver)
    val grantedCount = permissionsToRequest.count { PermissionManager.status(context, it) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("NexGuard Kurulum", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        when (step) {
            0 -> {
                // Adım 1: Cihaz Yöneticisi
                Text("Adım 1/3: Cihaz Yöneticisi", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Tarama ve koruma için cihaz yönetici izni gereklidir.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(24.dp))

                if (isAdminActive) {
                    Text("✓ Cihaz yöneticisi etkin.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { step = 1 }) { Text("İzinlere Geç") }
                } else {
                    Button(onClick = {
                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminReceiver)
                            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "NexGuard'ın dosyaları tarayıp koruyabilmesi için cihaz yöneticisi etkinleştirilmelidir.")
                        }
                        context.startActivity(intent)
                    }) { Text("Cihaz Yöneticisini Etkinleştir") }
                }
            }
            1 -> {
                // Adım 2: Çalışma zamanı izinleri
                Text("Adım 2/3: Çalışma Zamanı İzinleri", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("${permissionsToRequest.size} izin isteniyor ($grantedCount verildi)", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(24.dp))

                if (grantedCount == permissionsToRequest.size) {
                    Text("✓ Tüm izinler verildi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { step = 2 }) { Text("Özeti Geç") }
                } else {
                    Button(onClick = { requestPermissionLauncher.launch(permissionsToRequest.toTypedArray()) }) {
                        Text("İzinleri İste")
                    }
                }
            }
            else -> {
                // Adım 3: Tamamlandı
                Text("Adım 3/3: Kurulum Tamamlandı", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(24.dp))
                Button(onClick = onFinished) { Text("Bitir") }
            }
        }
    }
}
