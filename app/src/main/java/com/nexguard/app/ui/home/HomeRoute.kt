package com.nexguard.app.ui.home

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexguard.core.database.AppDatabase
import com.nexguard.feature.home.HomeScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class HomeUi(
    val lastScan: String = "—",
    val score: Int = 100,
    val threats: Int = 0,
    val total: Int = 0
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val db: AppDatabase
) : ViewModel() {

    private val _ui = MutableStateFlow(HomeUi())
    val ui: StateFlow<HomeUi> = _ui.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val recent = try { db.scanResultDao().recent() } catch (e: Exception) { emptyList() }
            val last = recent.firstOrNull()
            _ui.value = HomeUi(
                lastScan = last?.let {
                    SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(it.startedAt))
                } ?: "—",
                score = last?.score ?: 100,
                threats = recent.count { it.verdict != "CLEAN" },
                total = recent.size
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
    LaunchedEffect(Unit) { vm.refresh() }
    HomeScreen(
        lastScan = ui.lastScan,
        score = ui.score,
        threats = ui.threats,
        onScan = onScan,
        onNavigate = onNavigate
    )
}
