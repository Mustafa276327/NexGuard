package com.nexguard.feature.scan.detection

import com.nexguard.core.common.Finding
import com.nexguard.core.common.Severity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * İsteğe bağlı bulut doğrulaması. Yalnızca SHA-256 hash gönderilir; dosya adı,
 * yolu veya içerik asla gönderilmez. Varsayılan kapalıdır ve yalnızca kullanıcı
 * ayarlardan açtığında, şüpheli/çalıştırılabilir dosyalar için çalışır.
 */
class CloudVerifier(
    private val vtApiKey: String? = null,
    private val mbEnabled: Boolean = true,
    private val timeoutMs: Int = 10_000
) {
    private val cache = ConcurrentHashMap<String, List<Finding>>()
    private val lastCall = ConcurrentHashMap<String, Long>()
    @Volatile private var disabledUntil = 0L

    suspend fun check(hash: String): List<Finding> = withContext(Dispatchers.IO) {
        if (hash.isBlank() || hash.contains('-')) return@withContext emptyList()
        cache[hash]?.let { return@withContext it }
        val now = System.currentTimeMillis()
        if (now < disabledUntil) return@withContext emptyList()
        if (now - (lastCall["mb"] ?: 0L) < 10_000) return@withContext emptyList()
        lastCall["mb"] = now
        val out = mutableListOf<Finding>()

        if (mbEnabled) {
            var failed = false
            try {
                val conn = URL("https://mb-api.abuse.ch/api/v1/").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = timeoutMs
                conn.readTimeout = timeoutMs
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.setRequestProperty("User-Agent", "NexGuard/1.0")
                conn.outputStream.use { it.write("query=get_info&hash=$hash".toByteArray()) }
                var text = ""
                try {
                    text = conn.inputStream.bufferedReader().readText()
                } catch (e: Exception) {
                    text = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                }
                val json = JSONObject(text)
                val status = json.optString("query_status")
                if (status == "ok") {
                    val data = json.optJSONArray("data")
                    if (data != null && data.length() > 0) {
                        val first = data.getJSONObject(0)
                        val sig = first.optString("signature", "unknown")
                        val family = first.optString("tags", "").ifBlank { sig }
                        out += Finding(
                            detector = "cloud", ruleId = "MALWAREBAZAAR_HIT", severity = Severity.CRITICAL, weight = 85,
                            evidence = mapOf("signature" to sig, "source" to "MalwareBazaar", "family" to family)
                        )
                    }
                } else if (status != "hash_not_found") {
                    // Yetkilendirme/API hatasında tekrar tekrar istek yapma.
                    failed = true
                }
                conn.disconnect()
            } catch (e: Exception) {
                failed = true
            }
            if (failed) disabledUntil = System.currentTimeMillis() + 10 * 60_000
        }

        if (!vtApiKey.isNullOrBlank()) {
            try {
                val conn = URL("https://www.virustotal.com/api/v3/files/$hash").openConnection() as HttpURLConnection
                conn.connectTimeout = timeoutMs
                conn.readTimeout = timeoutMs
                conn.setRequestProperty("x-apikey", vtApiKey)
                conn.setRequestProperty("User-Agent", "NexGuard/1.0")
                if (conn.responseCode == 200) {
                    val json = JSONObject(conn.inputStream.bufferedReader().readText())
                    val stats = json.optJSONObject("data")?.optJSONObject("attributes")?.optJSONObject("last_analysis_stats")
                    val malicious = stats?.optInt("malicious", 0) ?: 0
                    if (malicious >= 3) {
                        out += Finding(
                            detector = "cloud", ruleId = "VIRUSTOTAL_HIT", severity = Severity.CRITICAL, weight = 90,
                            evidence = mapOf("malicious" to malicious.toString())
                        )
                    }
                }
                conn.disconnect()
            } catch (e: Exception) { }
        }

        if (cache.size > 500) cache.clear()
        cache[hash] = out
        out
    }
}

class CloudDetector(private val verifier: CloudVerifier) : Detector {
    override val name = "cloud"
    override val priority = 10
    override val network = true

    override suspend fun inspect(target: ScanTarget): List<Finding> = verifier.check(target.sha256)
}
