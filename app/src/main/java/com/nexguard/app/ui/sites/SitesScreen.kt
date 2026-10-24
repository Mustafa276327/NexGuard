package com.nexguard.app.ui.sites

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexguard.app.ui.components.*
import com.nexguard.app.util.Prefs
import com.nexguard.app.util.formatDateTime
import com.nexguard.core.common.ThreatCatalog
import com.nexguard.core.common.UrlRiskAnalyzer
import com.nexguard.core.common.Verdict
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

data class RecentCheck(
    val url: String,
    val host: String,
    val score: Int,
    val verdict: Verdict,
    val time: Long
)

@HiltViewModel
class SitesViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    data class UiState(
        val input: String = "",
        val assessment: UrlRiskAnalyzer.Assessment? = null,
        val blocked: List<String> = emptyList(),
        val recent: List<RecentCheck> = emptyList(),
        val message: String? = null
    )

    private val _state = MutableStateFlow(
        UiState(
            blocked = Prefs.blockedSites(context),
            recent = readRecent()
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun setInput(value: String) {
        _state.update { it.copy(input = value) }
    }

    fun check(input: String = _state.value.input) {
        val value = input.trim()
        if (value.isBlank()) {
            _state.update { it.copy(message = "Bir bağlantı girin") }
            return
        }
        val a = UrlRiskAnalyzer.analyze(value, _state.value.blocked.toSet())
        _state.update { st ->
            val recent = if (!a.valid) st.recent else {
                (listOf(
                    RecentCheck(
                        url = value,
                        host = a.host,
                        score = a.score,
                        verdict = a.verdict,
                        time = System.currentTimeMillis()
                    )
                ) + st.recent.filter { it.url != value }).take(12)
            }
            if (a.valid) saveRecent(recent)
            st.copy(input = value, assessment = a, recent = recent, message = null)
        }
    }

    fun paste() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val text = try {
            clipboard?.primaryClip?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.coerceToText(context)?.toString()
        } catch (e: Exception) { null }
        if (text.isNullOrBlank()) {
            _state.update { it.copy(message = "Pano boş") }
        } else {
            _state.update { it.copy(input = text.trim().take(2048)) }
        }
    }

    fun addBlocked(raw: String): Boolean {
        val host = UrlRiskAnalyzer.analyze(raw).host
            .ifBlank { raw.trim().lowercase().removePrefix("https://").removePrefix("http://").substringBefore('/') }
        if (!host.contains('.') || host.contains(' ')) {
            _state.update { it.copy(message = "Geçerli bir alan adı girin") }
            return false
        }
        if (_state.value.blocked.any { it == host }) {
            _state.update { it.copy(message = "Bu alan adı zaten engelli") }
            return false
        }
        val list = (_state.value.blocked + host).take(500)
        Prefs.saveBlockedSites(context, list)
        val a = _state.value.assessment?.let {
            UrlRiskAnalyzer.analyze(it.input, list.toSet())
        }
        _state.update { it.copy(blocked = list, assessment = a, message = "$host engellendi") }
        return true
    }

    fun removeBlocked(host: String) {
        val list = _state.value.blocked - host
        Prefs.saveBlockedSites(context, list)
        val a = _state.value.assessment?.let {
            UrlRiskAnalyzer.analyze(it.input, list.toSet())
        }
        _state.update { it.copy(blocked = list, assessment = a, message = "$host listeden çıkarıldı") }
    }

    fun clearRecent() {
        saveRecent(emptyList())
        _state.update { it.copy(recent = emptyList(), message = "Son kontroller temizlendi") }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun readRecent(): List<RecentCheck> = try {
        val raw = Prefs.get(context).getString(Prefs.RECENT_CHECKS, "[]") ?: "[]"
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val verdict = try { Verdict.valueOf(o.optString("verdict", "SUSPICIOUS")) } catch (e: Exception) { Verdict.SUSPICIOUS }
            RecentCheck(
                url = o.optString("url"),
                host = o.optString("host"),
                score = o.optInt("score", 0),
                verdict = verdict,
                time = o.optLong("time", 0)
            )
        }
    } catch (e: Exception) { emptyList() }

    private fun saveRecent(items: List<RecentCheck>) {
        try {
            val arr = JSONArray()
            items.forEach { item ->
                arr.put(JSONObject().apply {
                    put("url", item.url)
                    put("host", item.host)
                    put("score", item.score)
                    put("verdict", item.verdict.name)
                    put("time", item.time)
                })
            }
            Prefs.get(context).edit().putString(Prefs.RECENT_CHECKS, arr.toString()).apply()
        } catch (e: Exception) { }
    }
}

@Composable
fun SitesRoute(onBack: () -> Unit) {
    val vm: SitesViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var blockedInput by remember { mutableStateOf("") }
    var confirmOpen by remember { mutableStateOf<UrlRiskAnalyzer.Assessment?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        if (state.message != null) {
            kotlinx.coroutines.delay(3500)
            vm.clearMessage()
        }
    }

    fun openUrl(url: String) {
        val normalized = if (url.contains("://")) url else "https://$url"
        // Yalnızca web protokolleri dış tarayıcıya aktarılır; intent:/javascript:
        // gibi şemaların başka uygulamaları tetiklemesine izin verilmez.
        val scheme = try {
            Uri.parse(normalized).scheme?.lowercase()
        } catch (e: Exception) {
            null
        }
        if (scheme != "http" && scheme != "https") return
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalized)))
        } catch (e: Exception) {
            // Uygun tarayıcı yok.
        }
    }

    NgBackground {
        Column(Modifier.fillMaxSize()) {
            NgTopBar(
                title = "Site Kontrolü",
                subtitle = "URL risk analizi · çevrimdışı sezgisel",
                onBack = onBack,
                actions = {
                    if (state.recent.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = "Son kontrolleri temizle")
                        }
                    }
                }
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                SectionCard(title = "Bağlantı kontrolü", icon = Icons.Outlined.Language) {
                    OutlinedTextField(
                        value = state.input,
                        onValueChange = { vm.setInput(it) },
                        label = { Text("Bağlantı veya alan adı") },
                        placeholder = { Text("https://ornek.com/giris") },
                        leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { vm.paste() }) {
                                Icon(Icons.Outlined.ContentPaste, contentDescription = "Panodan yapıştır")
                            }
                        },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onGo = { vm.check() }
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { vm.check() },
                            enabled = state.input.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Kontrol et")
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                state.assessment?.let { a ->
                    AssessmentCard(
                        assessment = a,
                        onBlock = {
                            if (a.host.isNotBlank()) {
                                vm.addBlocked(a.host)
                            }
                        },
                        onOpen = {
                            if (a.verdict == Verdict.CLEAN) openUrl(a.input)
                            else confirmOpen = a
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                }

                SectionCard(title = "Engellenen alan adları (${state.blocked.size})", icon = Icons.Outlined.Block) {
                    Text(
                        "Engel listesindeki alan adları URL kontrolünde her zaman tehlikeli sayılır. " +
                            "Bu liste tarayıcıyı otomatik engellemez; NexGuard içinde uyarı üretir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = blockedInput,
                            onValueChange = { blockedInput = it },
                            placeholder = { Text("ornek.com") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (vm.addBlocked(blockedInput)) blockedInput = ""
                            },
                            enabled = blockedInput.isNotBlank(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Ekle") }
                    }
                    if (state.blocked.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        state.blocked.forEach { host ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Block,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(host, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                IconButton(onClick = { vm.removeBlocked(host) }, modifier = Modifier.size(32.dp)) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "Kaldır",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f))
                        }
                    }
                }

                if (state.recent.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    SectionCard(title = "Son kontroller", icon = Icons.Outlined.History) {
                        state.recent.forEachIndexed { index, item ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        vm.setInput(item.url)
                                        vm.check(item.url)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Link,
                                    contentDescription = null,
                                    tint = verdictColor(item.verdict),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.url,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${item.host} · ${formatDateTime(item.time)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                VerdictPill(item.verdict)
                            }
                            if (index != state.recent.lastIndex) {
                                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                InfoBanner(
                    "Analiz yalnızca adres yapısına bakar: HTTP, IP adresi, punycode, kısa bağlantı, " +
                        "şüpheli alan adı ve kimlik avı kelimeleri. Sonuç kesin güvenlik garantisi değildir.",
                    icon = Icons.Outlined.Info
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    confirmOpen?.let { a ->
        AlertDialog(
            onDismissRequest = { confirmOpen = null },
            icon = { Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Riskli bağlantı açılsın mı?") },
            text = {
                Text(
                    "${a.host} adresi ${ThreatCatalog.verdictLabel(a.verdict).lowercase()} olarak işaretlendi " +
                        "(skor ${a.score}/100). Yine de tarayıcıda açmak istiyor musunuz?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        openUrl(a.input)
                        confirmOpen = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Yine de aç") }
            },
            dismissButton = {
                TextButton(onClick = { confirmOpen = null }) { Text("Vazgeç") }
            }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Son kontroller silinsin mi?") },
            text = { Text("Yalnızca son kontrol listesi temizlenir; engellenen alan adları korunur.") },
            confirmButton = {
                TextButton(onClick = { vm.clearRecent(); confirmClear = false }) { Text("Temizle") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Vazgeç") }
            }
        )
    }
}

@Composable
private fun AssessmentCard(
    assessment: UrlRiskAnalyzer.Assessment,
    onBlock: () -> Unit,
    onOpen: () -> Unit
) {
    val color = verdictColor(assessment.verdict)
    SectionCard(title = "Analiz sonucu", icon = Icons.Outlined.Security) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        !assessment.valid -> "Geçersiz bağlantı"
                        assessment.verdict == Verdict.DANGEROUS -> "Bu bağlantıya güvenmeyin"
                        assessment.verdict == Verdict.SUSPICIOUS -> "Dikkatli olun"
                        else -> "Belirgin risk yok"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    assessment.host.ifBlank { assessment.input },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            VerdictPill(assessment.verdict)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(
                score = assessment.score,
                modifier = Modifier.size(92.dp),
                stroke = 8.dp,
                label = "Risk",
                progressColor = color,
                textColor = color
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                InfoRow("Protokol", assessment.scheme.ifBlank { "—" })
                InfoRow("Alan adı", assessment.host.ifBlank { "—" })
                InfoRow("Risk skoru", "${assessment.score}/100")
            }
        }
        Spacer(Modifier.height(12.dp))
        assessment.reasons.forEach { reason ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                Icon(
                    if (assessment.verdict == Verdict.CLEAN) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(reason, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onOpen,
                enabled = assessment.valid &&
                    assessment.scheme.lowercase() in setOf("http", "https"),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (assessment.verdict == Verdict.CLEAN) "Tarayıcıda aç" else "Yine de aç")
            }
            if (assessment.host.isNotBlank() && assessment.verdict != Verdict.CLEAN) {
                OutlinedButton(
                    onClick = onBlock,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Outlined.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Engelle")
                }
            }
        }
    }
}
