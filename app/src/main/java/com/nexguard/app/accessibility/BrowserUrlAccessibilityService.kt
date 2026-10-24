package com.nexguard.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.nexguard.app.util.NgLog
import com.nexguard.core.common.UrlRiskAnalyzer
import com.nexguard.core.common.Verdict
import com.nexguard.feature.alerts.AlertManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Tarayıcı adres çubuğunu okuyarak ziyaret edilen URL'leri çevrimdışı risk
 * kurallarıyla denetler. Yalnızca kullanıcı erişilebilirlik ayarlarından
 * açıkça etkinleştirirse çalışır.
 */
@AndroidEntryPoint
class BrowserUrlAccessibilityService : AccessibilityService() {

    @Inject lateinit var alertManager: AlertManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastUrl: String? = null
    private var lastCheckMs: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return
        val now = System.currentTimeMillis()
        if (now - lastCheckMs < 1500L) return
        val root = rootInActiveWindow ?: return
        val url = findUrl(root)
        root.recycle()
        if (url.isNullOrBlank()) return
        if (url == lastUrl) return
        lastUrl = url
        lastCheckMs = now
        scope.launch {
            try {
                val a = UrlRiskAnalyzer.analyze(url)
                if (a.verdict != Verdict.CLEAN) {
                    NgLog.w("SiteKontrol", "${a.host} · ${a.verdict} · ${a.score}/100")
                    alertManager.showInfo(
                        "Şüpheli site",
                        "${a.host.ifBlank { url }} riskli bulundu (${a.score}/100). Bağlantıyı açmayın."
                    )
                }
            } catch (e: Exception) {
                NgLog.w("SiteKontrol", "URL analizi başarısız: ${e.message}")
            }
        }
    }

    private fun findUrl(node: AccessibilityNodeInfo?, depth: Int = 0): String? {
        if (node == null || depth > 12) return null
        val text = node.text?.toString()?.trim().orEmpty()
        if (text.contains("://") && text.length in 8..2048) {
            val candidate = text.substringBefore(' ').trim()
            if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
                return candidate
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findUrl(child, depth + 1)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    override fun onInterrupt() { }
}
