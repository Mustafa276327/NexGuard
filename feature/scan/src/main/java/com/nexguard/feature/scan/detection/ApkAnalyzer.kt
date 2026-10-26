package com.nexguard.feature.scan.detection

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import com.nexguard.core.common.Finding
import com.nexguard.core.common.Severity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * APK statik analizi:
 * - PackageManager ile gerçek manifest izinleri ve imza bilgisi
 * - Tehlikeli izin kombinasyonları ve aile sınıflandırması
 * - Simgesiz/gizli uygulama, erişilebilirlik, cihaz yöneticisi, bildirim erişimi
 * - DEX içinde dinamik kod yükleme, gömülü URL/IP ve bot token'ı
 * - ZIP bomb / zip-slip sınırları
 * Kod çalıştırmaz; yalnızca paket içeriğini okur.
 */
class ApkAnalyzer(context: Context) : Detector {
    override val name = "apk"
    override val priority = 85

    private val pm: PackageManager = context.packageManager
    private val ownPackage: String = context.packageName

    /**
     * Uygulamanın kendi APK yolları. NexGuard kendi paketini tehdit olarak
     * işaretlemez (güçlü izinleri kendi işlevi için ister).
     */
    private val selfApkPaths: Set<String> = buildSet {
        val info = context.applicationInfo
        info?.sourceDir?.let { add(it) }
        info?.splitSourceDirs?.forEach { add(it) }
    }

    private fun isSelfApk(target: ScanTarget): Boolean {
        val path = try { target.file.canonicalPath } catch (e: Exception) { target.file.absolutePath }
        return selfApkPaths.any { self ->
            val canonicalSelf = try { java.io.File(self).canonicalPath } catch (e: Exception) { self }
            canonicalSelf == path
        }
    }

    private fun looksLikeApk(file: java.io.File): Boolean = try {
        java.util.zip.ZipFile(file).use { zf ->
            zf.getEntry("AndroidManifest.xml") != null || zf.getEntry("classes.dex") != null
        }
    } catch (e: Exception) {
        false
    }

    private val dangerousPerms = setOf(
        "android.permission.READ_SMS", "android.permission.SEND_SMS", "android.permission.RECEIVE_SMS",
        "android.permission.READ_CALL_LOG", "android.permission.WRITE_CALL_LOG",
        "android.permission.PROCESS_OUTGOING_CALLS", "android.permission.READ_CONTACTS",
        "android.permission.WRITE_CONTACTS", "android.permission.RECORD_AUDIO",
        "android.permission.CAMERA", "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION", "android.permission.READ_PHONE_STATE",
        "android.permission.SYSTEM_ALERT_WINDOW", "android.permission.REQUEST_INSTALL_PACKAGES",
        "android.permission.MANAGE_EXTERNAL_STORAGE", "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_MEDIA_VIDEO", "android.permission.READ_MEDIA_AUDIO",
        "android.permission.BODY_SENSORS", "android.permission.ANSWER_PHONE_CALLS"
    )

    private val dynamicCodeTokens = listOf(
        "DexClassLoader", "PathClassLoader", "InMemoryDexClassLoader", "loadDex",
        "Runtime.getRuntime().exec", "ProcessBuilder", "dalvik.system"
    )
    private val packerHints = listOf("bangcle", "ijiami", "qihoo", "tencent", "360", "liapp", "secneo", "kony", "upx")
    private val urlRegex = Regex("""https?://[A-Za-z0-9._~:/?#\[\]@!$&'()*+,;=%-]{4,200}""")
    private val ipRegex = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""")
    private val telegramBotRegex = Regex("""\b\d{8,10}:[A-Za-z0-9_-]{35}\b""")

    override suspend fun inspect(target: ScanTarget): List<Finding> {
        val ext = target.extension
        // .apk dışında, AndroidManifest.xml içeren .zip dosyaları da APK'dır
        // (yeniden adlandırılarak gizlenmiş olabilir).
        if (ext != "apk" && ext != "zip") return emptyList()
        if (isSelfApk(target)) return emptyList()
        if (ext == "zip" && !looksLikeApk(target.file)) return emptyList()
        val out = mutableListOf<Finding>()

        if (ext == "zip") {
            out += Finding(
                detector = name, ruleId = "APK_DISGUISED",
                severity = Severity.MEDIUM, weight = 40,
                evidence = mapOf("extension" to ext, "note" to "APK, .zip uzantısıyla gizlenmiş")
            )
        }

        var manifest: ByteArray? = null
        var dynamicCode = false
        var codeSms = false
        var codeContacts = false
        var codeAudio = false
        var codeCamera = false
        var telegramBot = false
        var packerHit: String? = null
        var nativeLibs = 0
        val urls = linkedSetOf<String>()
        val ips = linkedSetOf<String>()
        var zipIssue: String? = null

        try {
            ZipFile(target.file).use { zf ->
                val enumeration = zf.entries()
                var entryCount = 0
                var totalUncompressed = 0L
                var dexRead = 0L
                var dexCount = 0
                while (enumeration.hasMoreElements()) {
                    val e = enumeration.nextElement()
                    entryCount++
                    if (entryCount > 20_000) {
                        zipIssue = "entry_limit"
                        break
                    }
                    if (e.name.contains("..") || e.name.startsWith("/")) {
                        out += Finding(
                            detector = "apk-zip", ruleId = "ZIP_SLIP", severity = Severity.HIGH, weight = 55,
                            evidence = mapOf("entry" to e.name.take(120))
                        )
                        continue
                    }
                    val size = if (e.size > 0) e.size else 0L
                    totalUncompressed += size
                    if (totalUncompressed > 800L * 1024 * 1024) {
                        zipIssue = "size_limit"
                        break
                    }
                    if (e.compressedSize > 0 && size > 20L * 1024 * 1024 && size / e.compressedSize > 1000) {
                        out += Finding(
                            detector = "apk-zip", ruleId = "ZIP_BOMB_SUSPECT", severity = Severity.HIGH, weight = 60,
                            evidence = mapOf("entry" to e.name.take(120))
                        )
                    }

                    val lower = e.name.lowercase()
                    if (lower.endsWith(".so")) nativeLibs++
                    packerHints.forEach { if (lower.contains(it)) packerHit = it }

                    if (lower == "androidmanifest.xml") {
                        manifest = readEntry(zf, e, 8 * 1024 * 1024)
                    } else if (lower.endsWith(".dex") && dexCount < 8 && dexRead < 24L * 1024 * 1024) {
                        dexCount++
                        val bytes = readEntry(zf, e, 4 * 1024 * 1024)
                        dexRead += bytes.size
                        if (bytes.isNotEmpty()) {
                            val text = String(bytes, Charsets.ISO_8859_1)
                            if (dynamicCodeTokens.any { text.contains(it) }) dynamicCode = true
                            if (text.contains("SmsManager") || text.contains("sendTextMessage")) codeSms = true
                            if (text.contains("ContactsContract")) codeContacts = true
                            if (text.contains("MediaRecorder") || text.contains("AudioRecord")) codeAudio = true
                            if (text.contains("Camera.open") || text.contains("CameraManager")) codeCamera = true
                            if (telegramBotRegex.containsMatchIn(text)) telegramBot = true
                            urlRegex.findAll(text).take(200).forEach { urls += it.value }
                            ipRegex.findAll(text).take(100).forEach { ips += it.value }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            out += Finding(
                detector = "apk-zip", ruleId = "APK_PARSE_ERROR", severity = Severity.MEDIUM, weight = 25,
                evidence = mapOf("err" to (e.message ?: "").take(120))
            )
        }

        if (zipIssue != null) {
            out += Finding(
                detector = "apk-zip", ruleId = "ZIP_BOMB_SUSPECT", severity = Severity.HIGH, weight = 60,
                evidence = mapOf("reason" to zipIssue)
            )
        }

        val pi: PackageInfo? = try {
            @Suppress("DEPRECATION")
            val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNING_CERTIFICATES
            pm.getPackageArchiveInfo(target.path, flags)
        } catch (e: Exception) {
            null
        }

        // Kopyalanmış olsa bile NexGuard'ın kendi paketi tehdit sayılmaz.
        if (pi?.packageName == ownPackage) return emptyList()

        if (pi == null) {
            if (out.none { it.ruleId == "APK_PARSE_ERROR" }) {
                out += Finding(
                    detector = "apk-zip", ruleId = "APK_PARSE_ERROR", severity = Severity.MEDIUM, weight = 25,
                    evidence = mapOf("reason" to "manifest okunamadı")
                )
            }
            return out
        }

        val perms = pi.requestedPermissions?.toList() ?: emptyList()
        val dangerous = perms.filter { isDangerous(it) }
        val hasSms = perms.any { it == "android.permission.READ_SMS" || it == "android.permission.RECEIVE_SMS" || it == "android.permission.SEND_SMS" }
        val hasContacts = perms.any { it == "android.permission.READ_CONTACTS" || it == "android.permission.WRITE_CONTACTS" }
        val hasAudio = "android.permission.RECORD_AUDIO" in perms
        val hasCamera = "android.permission.CAMERA" in perms
        val hasOverlay = "android.permission.SYSTEM_ALERT_WINDOW" in perms
        val hasInstall = "android.permission.REQUEST_INSTALL_PACKAGES" in perms
        val hasStorageManager = "android.permission.MANAGE_EXTERNAL_STORAGE" in perms

        val m = manifest
        val hasAccessibility = m != null && manifestContains(m, "android.permission.BIND_ACCESSIBILITY_SERVICE")
        val hasAdmin = m != null && (
            manifestContains(m, "android.app.action.DEVICE_ADMIN_ENABLED") ||
                manifestContains(m, "android.permission.BIND_DEVICE_ADMIN")
            )
        val hasNotificationListener = m != null && manifestContains(m, "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE")
        val hasBoot = m != null && manifestContains(m, "android.intent.action.BOOT_COMPLETED")
        val hasLauncher = m != null &&
            manifestContains(m, "android.intent.category.LAUNCHER") &&
            manifestContains(m, "android.intent.action.MAIN")

        if (dangerous.size >= 6) {
            out += Finding(
                detector = "apk-perms", ruleId = "DANGEROUS_PERM_COMBO", severity = Severity.HIGH, weight = 35,
                evidence = mapOf(
                    "count" to dangerous.size.toString(),
                    "perms" to dangerous.take(10).joinToString(", ") { it.substringAfterLast('.') }
                )
            )
        }

        if (hasSms && hasAccessibility) {
            out += Finding(
                detector = "apk-perms", ruleId = "SMS_STEALER_COMBO", severity = Severity.CRITICAL, weight = 90,
                evidence = mapOf(
                    "family" to "SMS Hırsızı",
                    "perms" to "SMS + erişilebilirlik"
                )
            )
        }
        if (hasOverlay && hasAccessibility) {
            out += Finding(
                detector = "apk-perms", ruleId = "OVERLAY_ATTACK", severity = Severity.CRITICAL, weight = 80,
                evidence = mapOf("family" to "Banka Truvası", "perms" to "ekran üstü + erişilebilirlik")
            )
        }
        if (hasSms && hasAdmin) {
            out += Finding(
                detector = "apk-perms", ruleId = "BANKER_COMBO", severity = Severity.HIGH, weight = 70,
                evidence = mapOf("family" to "Banka Truvası", "perms" to "SMS + cihaz yöneticisi")
            )
        }
        if (hasStorageManager && hasAccessibility) {
            out += Finding(
                detector = "apk-perms", ruleId = "RANSOMWARE_COMBO", severity = Severity.HIGH, weight = 60,
                evidence = mapOf("family" to "Fidye Yazılımı", "perms" to "tüm dosyalar + erişilebilirlik")
            )
        }
        if (hasInstall && (hasAccessibility || dynamicCode)) {
            out += Finding(
                detector = "apk-perms", ruleId = "DROPPER_COMBO", severity = Severity.HIGH, weight = 65,
                evidence = mapOf("family" to "Dropper", "perms" to "paket kurma + dinamik kod")
            )
        }
        if (hasSms && !hasAccessibility) {
            out += Finding(
                detector = "apk-perms", ruleId = "SMS_PERMISSIONS", severity = Severity.MEDIUM, weight = 25,
                evidence = mapOf("family" to "SMS Hırsızı", "perms" to "SMS")
            )
        }
        if (hasAccessibility) {
            out += Finding(
                detector = "apk-perms", ruleId = "ACCESSIBILITY_ABUSE", severity = Severity.MEDIUM, weight = 30,
                evidence = mapOf("note" to "erişilebilirlik servisi")
            )
        }
        if (hasAdmin) {
            out += Finding(
                detector = "apk-perms", ruleId = "DEVICE_ADMIN_ABUSE", severity = Severity.MEDIUM, weight = 30
            )
        }
        if (hasBoot) {
            out += Finding(
                detector = "apk-perms", ruleId = "BOOT_PERSISTENCE", severity = Severity.LOW, weight = 15
            )
        }
        if (hasNotificationListener && !hasLauncher) {
            out += Finding(
                detector = "apk-perms", ruleId = "SPYWARE_COMBO", severity = Severity.HIGH, weight = 60,
                evidence = mapOf("family" to "Casus Yazılım", "note" to "bildirim erişimi + simgesiz")
            )
        }
        if (!hasLauncher && (hasAccessibility || hasAdmin || hasBoot || hasNotificationListener)) {
            out += Finding(
                detector = "apk-perms", ruleId = "HIDDEN_APP", severity = Severity.HIGH, weight = 65,
                evidence = mapOf(
                    "family" to "Casus Yazılım",
                    "note" to "başlatıcı simgesi yok; erişilebilirlik/yönetici/açılış bileşeni var"
                )
            )
        }
        if ((codeSms && codeContacts) || (codeAudio && codeCamera && !hasLauncher)) {
            out += Finding(
                detector = "apk-dex", ruleId = "SPYWARE_COMBO", severity = Severity.HIGH, weight = 60,
                evidence = mapOf("family" to "Casus Yazılım", "note" to "kod içinde SMS/kişi veya kamera/mikrofon erişimi")
            )
        }
        if (hasSms && hasContacts && hasAudio) {
            out += Finding(
                detector = "apk-perms", ruleId = "SPYWARE_COMBO", severity = Severity.HIGH, weight = 60,
                evidence = mapOf("family" to "Casus Yazılım", "perms" to "SMS + kişiler + mikrofon")
            )
        }
        if (dynamicCode) {
            out += Finding(
                detector = "apk-dex", ruleId = "DYNAMIC_CODE_LOADING", severity = Severity.HIGH, weight = 45
            )
        }
        if (telegramBot) {
            out += Finding(
                detector = "apk-dex", ruleId = "TELEGRAM_BOT", severity = Severity.HIGH, weight = 55,
                evidence = mapOf("family" to "Casus Yazılım", "note" to "gömülü Telegram bot token'ı")
            )
        }
        if (packerHit != null) {
            out += Finding(
                detector = "apk-zip", ruleId = "PACKER_DETECTED", severity = Severity.MEDIUM, weight = 35,
                evidence = mapOf("packer" to packerHit!!, "nativeLibs" to nativeLibs.toString())
            )
        }
        if (urls.size > 40) {
            out += Finding(
                detector = "apk-dex", ruleId = "MANY_EMBEDDED_URLS", severity = Severity.LOW, weight = 18,
                evidence = mapOf("count" to urls.size.toString())
            )
        }
        if (ips.size >= 3) {
            out += Finding(
                detector = "apk-dex", ruleId = "EMBEDDED_IPS", severity = Severity.MEDIUM, weight = 25,
                evidence = mapOf("count" to ips.size.toString(), "ips" to ips.take(5).joinToString(", "))
            )
        }

        analyzeSignature(pi, out)

        val targetSdk = try { pi.applicationInfo?.targetSdkVersion ?: 0 } catch (e: Exception) { 0 }
        if (targetSdk in 1..22) {
            out += Finding(
                detector = "apk-perms", ruleId = "OLD_TARGET", severity = Severity.LOW, weight = 12,
                evidence = mapOf("targetSdk" to targetSdk.toString())
            )
        }
        return out
    }

    private fun analyzeSignature(pi: PackageInfo, out: MutableList<Finding>) {
        try {
            val signers = pi.signingInfo?.apkContentsSigners ?: pi.signingInfo?.signingCertificateHistory
            val sig = signers?.firstOrNull() ?: return
            val cert = CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(sig.toByteArray())) as X509Certificate
            val subject = cert.subjectDN.name
            val issuer = cert.issuerDN.name
            val sha = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
                .joinToString("") { "%02x".format(it) }

            if (subject.contains("Android Debug", true) || issuer.contains("Android Debug", true)) {
                out += Finding(
                    detector = "apk-signature", ruleId = "DEBUG_CERT", severity = Severity.MEDIUM, weight = 35,
                    evidence = mapOf("subject" to subject.take(160), "sha256" to sha.take(32))
                )
            }
            if (cert.notAfter.before(Date())) {
                out += Finding(
                    detector = "apk-signature", ruleId = "EXPIRED_CERT", severity = Severity.MEDIUM, weight = 30,
                    evidence = mapOf("notAfter" to cert.notAfter.toString())
                )
            }
        } catch (e: Exception) { /* imza okunamadı */ }
    }

    private fun isDangerous(perm: String): Boolean {
        if (perm in dangerousPerms) return true
        return try {
            val info = pm.getPermissionInfo(perm, 0)
            (info.protectionLevel and 0xF) == PermissionInfo.PROTECTION_DANGEROUS
        } catch (e: Exception) {
            false
        }
    }

    private fun manifestContains(manifest: ByteArray, value: String): Boolean =
        manifest.containsAscii(value) || manifest.contains(value.toByteArray(Charsets.UTF_16LE))

    private fun readEntry(zf: ZipFile, e: ZipEntry, max: Int): ByteArray {
        if (e.size > max || e.size < 0) return byteArrayOf()
        val bos = ByteArrayOutputStream()
        zf.getInputStream(e).use { ins ->
            val buf = ByteArray(16 * 1024)
            var total = 0
            while (total < max) {
                val n = ins.read(buf)
                if (n <= 0) break
                bos.write(buf, 0, n)
                total += n
            }
        }
        return bos.toByteArray()
    }
}
