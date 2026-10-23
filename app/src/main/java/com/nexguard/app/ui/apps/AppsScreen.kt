package com.nexguard.app.ui.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppRow(
    val label: String,
    val packageName: String,
    val version: String,
    val isSystem: Boolean,
    val dangerousPerms: Int,
    val icon: ImageBitmap?
)

@HiltViewModel
class AppsViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _apps = MutableStateFlow<List<AppRow>>(emptyList())
    val apps: StateFlow<List<AppRow>> = _apps.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            val pm = context.packageManager
            val list = try {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(PackageManager.GET_PERMISSIONS).mapNotNull { pi ->
                    val ai = pi.applicationInfo ?: return@mapNotNull null
                    val label = try { pm.getApplicationLabel(ai).toString() } catch (e: Exception) { pi.packageName }
                    val dangerous = pi.requestedPermissions?.count { isDangerous(pm, it) } ?: 0
                    val icon = try {
                        pm.getApplicationIcon(ai).toBitmap(96, 96).asImageBitmap()
                    } catch (e: Exception) { null }
                    AppRow(
                        label = label,
                        packageName = pi.packageName,
                        version = pi.versionName ?: "—",
                        isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        dangerousPerms = dangerous,
                        icon = icon
                    )
                }.sortedBy { it.label.lowercase() }
            } catch (e: Exception) {
                emptyList()
            }
            _apps.value = list
            _loading.value = false
        }
    }

    private fun isDangerous(pm: PackageManager, perm: String): Boolean = try {
        val level = pm.getPermissionInfo(perm, 0).protectionLevel
        (level and 0xF) == PermissionInfo.PROTECTION_DANGEROUS
    } catch (e: Exception) {
        false
    }
}

@Composable
fun AppsRoute(onBack: () -> Unit) {
    val vm: AppsViewModel = hiltViewModel()
    val apps by vm.apps.collectAsState()
    val loading by vm.loading.collectAsState()
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<AppRow?>(null) }

    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Geri") }
            Spacer(Modifier.width(4.dp))
            Column {
                Text("Uygulamalar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${apps.size} uygulama", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { vm.load() }) { Text("Yenile") }
        }
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Ara") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { selected = app }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (app.icon != null) {
                                Image(
                                    bitmap = app.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp).clip(CircleShape)
                                )
                            } else {
                                Box(
                                    Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        app.label.take(1).uppercase(),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    app.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                if (app.isSystem) {
                                    Text("Sistem", style = MaterialTheme.typography.labelSmall)
                                }
                                if (app.dangerousPerms > 0) {
                                    Text(
                                        "${app.dangerousPerms} izin",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { app ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(app.label) },
            text = {
                Column {
                    Text("Paket: ${app.packageName}", style = MaterialTheme.typography.bodySmall)
                    Text("Sürüm: ${app.version}", style = MaterialTheme.typography.bodySmall)
                    Text("Tür: ${if (app.isSystem) "Sistem uygulaması" else "Kullanıcı uygulaması"}", style = MaterialTheme.typography.bodySmall)
                    Text("Tehlikeli izin: ${app.dangerousPerms}", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                        )
                    } catch (e: Exception) { }
                    selected = null
                }) { Text("Uygulama Bilgisi") }
            },
            dismissButton = {
                TextButton(onClick = { selected = null }) { Text("Kapat") }
            }
        )
    }
}
