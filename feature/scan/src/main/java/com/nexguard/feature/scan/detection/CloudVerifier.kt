package com.nexguard.feature.scan.detection

import com.nexguard.core.common.Finding
import com.nexguard.core.common.Severity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class CloudVerifier(
    private val vtApiKey: String? = null,
    private val mbEnabled: Boolean = true
) {
    private val cache = ConcurrentHashMap<String, List<Finding>>()
    private val lastCall = ConcurrentHashMap<String, Long>()

    suspend fun check(hash: String): List<Finding> = withContext(Dispatchers.IO) {
        cache[hash]?.let { return@withContext it }
        // rate limit: min 15s between calls per provider
        val now = System.currentTimeMillis()
        if (now - (lastCall["mb"] ?: 0L) < 15000) return@withContext emptyList()
        lastCall["mb"] = now
        val out = mutableListOf<Finding>()
        // MalwareBazaar hash lookup (no key)
        if (mbEnabled) {
            try {
                val conn = URL("https://mb-api.abuse.ch/api/v1/").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.outputStream.use { it.write("query=get_info&hash=$hash".toByteArray()) }
                val text = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(text)
                if (json.optString("query_status") == "ok") {
                    val data = json.optJSONArray("data")
                    if (data != null && data.length() > 0) {
                        val first = data.getJSONObject(0)
                        val sig = first.optString("signature", "unknown")
                        out += Finding(detector = "cloud", ruleId = "MALWAREBAZAAR_HIT", severity = Severity.CRITICAL, weight = 80, evidence = mapOf("signature" to sig, "source" to "MalwareBazaar"))
                    }
                }
                conn.disconnect()
            } catch (e: Exception) { /* network failure: skip */ }
        }
        // VirusTotal (key required)
        if (!vtApiKey.isNullOrBlank()) {
            try {
                val conn = URL("https://www.virustotal.com/api/v3/files/$hash").openConnection() as HttpURLConnection
                conn.setRequestProperty("x-apikey", vtApiKey)
                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(text)
                    val stats = json.optJSONObject("data")?.optJSONObject("attributes")?.optJSONObject("last_analysis_stats")
                    val malicious = stats?.optInt("malicious", 0) ?: 0
                    if (malicious >= 3) out += Finding(detector = "cloud", ruleId = "VIRUSTOTAL_HIT", severity = Severity.CRITICAL, weight = 85, evidence = mapOf("malicious" to malicious.toString()))
                }
                conn.disconnect()
            } catch (e: Exception) { }
        }
        cache[hash] = out
        out
    }
}

class CloudDetector(private val verifier: CloudVerifier) : Detector {
    override val name = "cloud"
    override val priority = 20
    override suspend fun inspect(target: ScanTarget): List<Finding> = verifier.check(target.sha256)
}