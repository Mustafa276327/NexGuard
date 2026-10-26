package com.nexguard.app.ui.home

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.EntryPointAccessors
import com.nexguard.app.scan.ScanControllerEntryPoint
import com.nexguard.app.scan.ScanUiState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexguard.app.admin.DeviceAdminReceiver
import com.nexguard.app.util.Prefs
import com.nexguard.app.util.relativeTime
import com.nexguard.core.database.AppDatabase
import com.nexguard.feature.onboarding.PermissionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeThreat(
    val name: String,
    val threatType: String,
    val score: Int,
    val time: Long
)

data class HomeUi(
    val lastScan: String = "Henüz tarama yok",
    val score: Int = 100,
    val threats: Int = 0,
    /** Son taramada teknik olarak tamamlanamayan dosya sayısı. */
    val issues: Int = 0,
    val total: Int = 0,
    val recentThreats: List<HomeThreat> = emptyList(),
    val monitorOn: Boolean = true,
    val storageOk: Boolean = false,
    val adminOk: Boolean = false,
    val notifyOk: Boolean = false,
    val hasSession: Boolean = false,
    val version: String = ""
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val db: AppDatabase
) : ViewModel() {

    private val _ui = MutableStateFlow(HomeUi())
    val ui: StateFlow<HomeUi> = _ui.asStateFlow()

    fun refresh(
        monitorOn: Boolean,
        storageOk: Boolean,
        adminOk: Boolean,
        notifyOk: Boolean,
        version: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val session = try { db.scanSessionDao().latest() } catch (e: Exception) { null }
            val recent = try { db.scanResultDao().threats(3) } catch (e: Exception) { emptyList() }
            var score = session?.score ?: 100
            if (!monitorOn) score -= 20
            if (!storageOk) score -= 15
            if (!notifyOk) score -= 5
            if (!adminOk) score -= 5
            score = score.coerceIn(0, 100)

            val last = session?.finishedAt ?: session?.startedAt
            _ui.value = HomeUi(
                lastScan = if (last != null) relativeTime(last) else "Henüz tarama yok",
                score = score,
                threats = session?.threats ?: 0,
                issues = session?.errors ?: 0,
                total = session?.filesScanned ?: 0,
                recentThreats = recent.map {
                    HomeThreat(
                        name = java.io.File(it.targetPath).name.ifBlank { it.targetPath },
                        threatType = it.threatType.ifBlank { "Şüpheli dosya" },
                        score = it.score,
                        time = it.startedAt
                    )
                },
                monitorOn = monitorOn,
                storageOk = storageOk,
                adminOk = adminOk,
                notifyOk = notifyOk,
                hasSession = session != null,
                version = version
            )
        }
    }
}

@Composable
fun HomeRoute(
    onScan: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val vm: HomeViewModel = hiltViewModel()
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    val controller = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, ScanControllerEntryPoint::class.java).scanController()
    }
    val scanState by controller.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: Exception) { "" }
    }

    fun refreshNow() {
        val admin = ComponentName(context, DeviceAdminReceiver::class.java)
        vm.refresh(
            monitorOn = Prefs.monitorOn(context),
            storageOk = PermissionManager.storageAccess(context),
            adminOk = PermissionManager.deviceAdminActive(context, admin),
            notifyOk = PermissionManager.notificationsGranted(context),
            version = version
        )
    }

    LaunchedEffect(Unit) { refreshNow() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshNow()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    HomeScreen(
        ui = ui,
        scanState = scanState,
        onScan = onScan,
        onStopScan = { controller.stop() },
        onNavigate = onNavigate,
        onRefresh = ::refreshNow
    )
}
