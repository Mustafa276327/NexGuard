package com.nexguard.feature.scan.detection

import com.nexguard.core.common.Finding
import com.nexguard.core.common.Severity
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class ApkAnalyzer : Detector {
    override val name = "apk"
    override val priority = 60

    private val dangerousPerms = setOf(
        "android.permission.READ_SMS", "android.permission.SEND_SMS", "android.permission.RECEIVE_SMS",
        "android.permission.READ_CALL_LOG", "android.permission.PROCESS_OUTGOING_CALLS",
        "android.permission.READ_CONTACTS", "android.permission.RECORD_AUDIO",
        "android.permission.CAMERA", "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.SYSTEM_ALERT_WINDOW", "android.permission.BIND_ACCESSIBILITY_SERVICE",
        "android.permission.REQUEST_INSTALL_PACKAGES", "android.permission.MANAGE_EXTERNAL_STORAGE"
    )
    private val dynamicCodeTokens = listOf("DexClassLoader", "PathClassLoader", "dalvik.system", "loadDex", "Runtime.getRuntime().exec", "ProcessBuilder")
    private val packerHints = listOf("bangcle", "ijiami", "qihoo", "tencent", "360", "liapp", "secneo", "kony")
    private val urlRegex = Regex("""https?://[A-Za-z0-9.\-_/]+""")

    override suspend fun inspect(target: ScanTarget): List<Finding> {
        if (!target.file.name.endsWith(".apk", true)) return emptyList()
        val out = mutableListOf<Finding>()
        val maxEntries = 4096
        val maxTotalUncompressed = 200L * 1024 * 1024
        var totalUncompressed = 0L
        val embeddedUrls = mutableSetOf<String>()
        val embeddedIps = mutableSetOf<String>()
        var hasDynamicCode = false
        var nativeLibs = 0
        var packerHit: String? = null
        val permissions = mutableListOf<String>()
        try {
            ZipFile(target.file).use { zf ->
                val enumeration = zf.entries()
                var entryCountChecked = 0
                while (enumeration.hasMoreElements()) {
                    val e = enumeration.nextElement()
                    entryCountChecked++
                    if (entryCountChecked > maxEntries) {
                        out += Finding(detector = name, ruleId = "ZIP_TOO_MANY_ENTRIES", severity = Severity.MEDIUM, weight = 20)
                        break
                    }
                    // zip slip protection
                    if (e.name.contains("..") || e.name.startsWith("/")) continue
                    totalUncompressed += e.size
                    if (totalUncompressed > maxTotalUncompressed) {
                        out += Finding(detector = name, ruleId = "ZIP_BOMB_SUSPECT", severity = Severity.HIGH, weight = 60)
                        break
                    }
                    val lower = e.name.lowercase()
                    if (lower.endsWith(".so")) nativeLibs++
                    packerHints.forEach { if (lower.contains(it)) packerHit = it }
                    if (lower == "androidmanifest.xml") {
                        val bytes = readEntry(zf, e, 2 * 1024 * 1024)
                        val text = bytes.toString(Charsets.UTF_8)
                        // crude permission extraction
                        dangerousPerms.forEach { p -> if (text.contains(p)) permissions += p }
                        // components exported
                        if (text.contains("exported=\"true\"")) out += Finding(detector = name, ruleId = "EXPORTED_COMPONENT", severity = Severity.LOW, weight = 10)
                    }
                    if (lower.endsWith(".dex") || lower.endsWith(".class")) {
                        val bytes = readEntry(zf, e, 4 * 1024 * 1024)
                        val text = bytes.toString(Charsets.ISO_8859_1)
                        if (dynamicCodeTokens.any { text.contains(it) }) hasDynamicCode = true
                        urlRegex.findAll(text).forEach { embeddedUrls += it.value }
                        Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""").findAll(text).forEach { embeddedIps += it.value }
                    }
                }
            }
        } catch (e: Exception) {
            out += Finding(detector = name, ruleId = "APK_PARSE_ERROR", severity = Severity.LOW, weight = 5, evidence = mapOf("err" to (e.message ?: "")))
        }
        // permission combo scoring
        val dangerousCount = permissions.size
        if (dangerousCount >= 4) out += Finding(detector = name, ruleId = "DANGEROUS_PERM_COMBO", severity = Severity.HIGH, weight = 40, evidence = mapOf("count" to dangerousCount.toString(), "perms" to permissions.joinToString()))
        if (hasDynamicCode) out += Finding(detector = name, ruleId = "DYNAMIC_CODE_LOADING", severity = Severity.HIGH, weight = 45)
        if (nativeLibs > 0 && packerHit != null) out += Finding(detector = name, ruleId = "PACKER_DETECTED", severity = Severity.MEDIUM, weight = 35, evidence = mapOf("packer" to packerHit!!, "libs" to nativeLibs.toString()))
        if (embeddedUrls.size > 5) out += Finding(detector = name, ruleId = "MANY_EMBEDDED_URLS", severity = Severity.MEDIUM, weight = 25, evidence = mapOf("count" to embeddedUrls.size.toString()))
        if (embeddedIps.isNotEmpty()) out += Finding(detector = name, ruleId = "EMBEDDED_IPS", severity = Severity.MEDIUM, weight = 20, evidence = mapOf("ips" to embeddedIps.take(10).joinToString()))
        return out
    }

    private fun readEntry(zf: ZipFile, e: ZipEntry, max: Int): ByteArray {
        if (e.size > max || e.size < 0) return byteArrayOf()
        val bos = ByteArrayOutputStream()
        zf.getInputStream(e).use { ins ->
            val buf = ByteArray(8192); var n: Int; var total = 0
            while (ins.read(buf).also { n = it } > 0 && total < max) { bos.write(buf, 0, n); total += n }
        }
        return bos.toByteArray()
    }
}