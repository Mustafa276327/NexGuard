package com.nexguard.core.common

import java.net.URI

/**
 * Saf Kotlin URL risk analizi. Ağ isteği yapmaz; yalnızca adres yapısını
 * değerlendirir. Hem uygulama içi site kontrolünde hem birim testlerinde
 * kullanılabilir.
 */
object UrlRiskAnalyzer {

    data class Assessment(
        val input: String,
        val host: String = "",
        val scheme: String = "",
        val score: Int = 0,
        val verdict: Verdict = Verdict.CLEAN,
        val reasons: List<String> = emptyList(),
        val valid: Boolean = true
    )

    private val ipv4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
    private val shorteners = setOf(
        "bit.ly", "bitly.com", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd",
        "buff.ly", "rebrand.ly", "cutt.ly", "shorturl.at", "rb.gy", "tiny.cc",
        "lnkd.in", "s.id", "urlz.fr", "shorte.st", "adf.ly"
    )
    private val suspiciousTlds = setOf(
        "tk", "ml", "ga", "cf", "gq", "zip", "mov", "click", "top", "xyz", "work",
        "icu", "buzz", "cam", "rest", "loan", "win", "bid", "download", "stream",
        "racing", "party", "date", "faith", "review", "country", "science", "men",
        "cricket", "gdn", "link", "monster", "quest", "surf", "bar", "cyou", "sbs",
        "cfd", "help", "makeup", "skin", "hair", "beauty", "autos", "homes",
        "motorcycles", "boats", "yachts", "bond"
    )
    private val phishingWords = setOf(
        "login", "signin", "sign-in", "verify", "verification", "account", "update",
        "secure", "security", "bank", "paypal", "apple", "icloud", "google",
        "microsoft", "amazon", "netflix", "instagram", "facebook", "whatsapp",
        "wallet", "bonus", "prize", "gift", "support", "helpdesk", "confirm",
        "password", "otp", "sms", "free"
    )
    private val executableExtensions = setOf("apk", "exe", "msi", "scr", "bat", "cmd", "js", "vbs")

    fun analyze(raw: String, blockedDomains: Set<String> = emptySet()): Assessment {
        val input = raw.trim()
        if (input.isBlank()) {
            return Assessment(input, valid = false, verdict = Verdict.SUSPICIOUS, reasons = listOf("Bağlantı boş"))
        }

        val schemeMatch = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):").find(input)
        if (schemeMatch != null) {
            val candidate = schemeMatch.groupValues[1].lowercase()
            if (candidate !in setOf("http", "https")) {
                val after = input.substring(schemeMatch.value.length)
                val looksLikeHostPort = Regex("""^\d{1,5}(/.*)?$""").matches(after)
                if (!looksLikeHostPort) {
                    return Assessment(
                        input = input,
                        scheme = candidate,
                        score = 100,
                        verdict = Verdict.DANGEROUS,
                        reasons = listOf("Desteklenmeyen veya tehlikeli protokol: $candidate")
                    )
                }
            }
        }

        val withScheme = if (input.contains("://")) input else "https://$input"
        val uri = try {
            URI(withScheme)
        } catch (e: Exception) {
            return Assessment(input, valid = false, verdict = Verdict.SUSPICIOUS, reasons = listOf("Geçersiz bağlantı biçimi"))
        }

        val scheme = uri.scheme?.lowercase().orEmpty()
        if (scheme !in setOf("http", "https")) {
            return Assessment(
                input = input,
                scheme = scheme,
                score = 100,
                verdict = Verdict.DANGEROUS,
                reasons = listOf("Desteklenmeyen veya tehlikeli protokol: ${scheme.ifBlank { "bilinmiyor" }}")
            )
        }

        val host = uri.host?.lowercase()?.trimEnd('.') ?: run {
            return Assessment(input, valid = false, verdict = Verdict.SUSPICIOUS, reasons = listOf("Alan adı okunamadı"))
        }
        if (host.isBlank() || (!host.contains('.') && host != "localhost")) {
            return Assessment(input, host = host, valid = false, verdict = Verdict.SUSPICIOUS, reasons = listOf("Geçerli bir alan adı yok"))
        }

        var score = 0
        val reasons = mutableListOf<String>()
        fun add(points: Int, reason: String) {
            score += points
            reasons += reason
        }

        val blocked = blockedDomains.map { it.lowercase().trim().removePrefix("www.") }
            .filter { it.isNotBlank() }
        if (blocked.any { host == it || host.endsWith(".$it") }) {
            return Assessment(
                input = input,
                host = host,
                scheme = scheme,
                score = 100,
                verdict = Verdict.DANGEROUS,
                reasons = listOf("Alan adı engel listesinde")
            )
        }

        if (scheme == "http") add(25, "Şifresiz HTTP bağlantısı")
        if (ipv4.matches(host)) add(35, "Alan adı yerine IP adresi kullanılmış")
        if (uri.userInfo != null) add(30, "Adres içinde @ ile yönlendirme hilesi")
        if (host.startsWith("xn--") || host.split('.').any { it.startsWith("xn--") }) {
            add(30, "Punycode/alfabe taklidi (xn--)")
        }
        if (shorteners.any { host == it || host.endsWith(".$it") }) {
            add(30, "Kısaltılmış bağlantı; gerçek hedef gizli")
        }
        val tld = host.substringAfterLast('.', "")
        if (tld in suspiciousTlds) add(25, "Şüpheli üst düzey alan adı (.$tld)")
        if (host.split('.').count { it.isNotBlank() } >= 4) add(8, "Aşırı alt alan adı")
        if (host.length > 45) add(8, "Olağandışı uzun alan adı")
        if (uri.port != -1 && uri.port !in setOf(80, 443)) add(10, "Standart dışı port (${uri.port})")
        if (host.any { it.code > 127 }) add(20, "Alan adında Unicode karakterler")

        val path = (uri.rawPath ?: "").lowercase()
        val query = (uri.rawQuery ?: "").lowercase()
        val pathAndQuery = "$path $query"
        val hits = phishingWords.filter { pathAndQuery.contains(it) }
        if (hits.isNotEmpty()) {
            add(minOf(20, hits.size * 8), "Kimlik avı anahtar kelimeleri: ${hits.take(3).joinToString(", ")}")
        }
        val extension = path.substringAfterLast('.', "").substringBefore('/')
        if (extension in executableExtensions) add(30, "Bağlantı çalıştırılabilir dosya indiriyor (.$extension)")
        if (input.length > 220) add(8, "Olağandışı uzun bağlantı")
        if (input.count { it == '@' } > 1) add(10, "Birden fazla @ karakteri")

        score = score.coerceIn(0, 100)
        val verdict = when {
            score >= 60 -> Verdict.DANGEROUS
            score >= 25 -> Verdict.SUSPICIOUS
            else -> Verdict.CLEAN
        }
        return Assessment(
            input = input,
            host = host,
            scheme = scheme,
            score = score,
            verdict = verdict,
            reasons = reasons.ifEmpty { listOf("Belirgin bir risk işareti bulunmadı") }
        )
    }
}
