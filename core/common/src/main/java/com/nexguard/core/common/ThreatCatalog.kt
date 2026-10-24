package com.nexguard.core.common

/**
 * Tek bir kuralın kullanıcıya gösterilecek adı ve ait olduğu tehdit ailesi.
 * Tüm ekranlar (tarama, geçmiş, karantina, bildirim) aynı dili kullanır.
 */
data class RuleInfo(
    val title: String,
    val threatType: String,
    val advice: String
)

object ThreatCatalog {

    private val rules: Map<String, RuleInfo> = mapOf(
        // --- Ön süzgeç ---
        "RTLO" to RuleInfo("Sağdan sola gizleme (RTLO)", "Gizlenmiş Dosya", "Dosya adı aldatıcı; açmayın ve karantinaya alın."),
        "RTLO_EXT" to RuleInfo("Uzantıda RTLO hilesi", "Gizlenmiş Dosya", "Gerçek uzantı gizlenmiş; dosyayı açmayın."),
        "DOUBLE_EXT" to RuleInfo("Çift uzantı", "Gizlenmiş Dosya", "Belge gibi görünen çalıştırılabilir dosya; açmayın."),
        "FAKE_NAME" to RuleInfo("Sahte uygulama adı", "Şüpheli APK", "Resmî uygulama adını taklit ediyor olabilir; kaynağı doğrulayın."),
        "EXT_MAGIC_MISMATCH" to RuleInfo("Uzantı ve içerik uyuşmuyor", "Gizlenmiş Dosya", "Dosya uzantısı içeriğiyle uyuşmuyor; dikkatli olun."),
        "HOMOGLYPH_NAME" to RuleInfo("Karışık alfabe (homoglif) adı", "Gizlenmiş Dosya", "Dosya adında farklı alfabe karakterleri var; taklit olabilir."),
        "HIDDEN_EXTENSION" to RuleInfo("Gizli çift uzantı", "Gizlenmiş Dosya", "Dosya adında gizlenmiş uzantı var."),
        "TRAILING_SPACES" to RuleInfo("Sondaki boşluklarla gizlenmiş uzantı", "Gizlenmiş Dosya", "Uzantı boşluklarla gizlenmiş; açmayın."),

        // --- İmza / hash ---
        "KNOWN_BAD_HASH" to RuleInfo("Bilinen zararlı yazılım imzası", "Zararlı Yazılım", "Dosya bilinen bir zararlıyla birebir eşleşiyor; hemen karantinaya alın."),
        "EICAR_TEST" to RuleInfo("EICAR antivirüs test imzası", "Test Virüsü", "Bu bir antivirüs test dosyasıdır; motorun çalıştığını doğrular."),
        "MALWAREBAZAAR_HIT" to RuleInfo("MalwareBazaar kaydı", "Zararlı Yazılım", "Hash çevrimiçi tehdit veritabanında zararlı olarak kayıtlı."),
        "VIRUSTOTAL_HIT" to RuleInfo("VirusTotal tespiti", "Zararlı Yazılım", "Birden fazla motor dosyayı zararlı olarak işaretledi."),

        // --- APK ---
        "DANGEROUS_PERM_COMBO" to RuleInfo("Tehlikeli izin kombinasyonu", "Şüpheli APK", "Uygulama çok sayıda hassas izin istiyor; kaynağını doğrulayın."),
        "SMS_STEALER_COMBO" to RuleInfo("SMS + erişilebilirlik kombinasyonu", "SMS Hırsızı", "SMS okuma ve ekran erişimi birlikte isteniyor; bankacılık truvası olabilir."),
        "BANKER_COMBO" to RuleInfo("Banka truvası belirtileri", "Banka Truvası", "Ekran üstü + erişilebilirlik + SMS izinleri birlikte kullanılıyor."),
        "SPYWARE_COMBO" to RuleInfo("Casus yazılım belirtileri", "Casus Yazılım", "Kişisel verilere geniş erişim isteyen gizli uygulama olabilir."),
        "RANSOMWARE_COMBO" to RuleInfo("Fidye yazılımı belirtileri", "Fidye Yazılımı", "Dosya şifreleme ve yönetim izinleri birlikte isteniyor."),
        "DROPPER_COMBO" to RuleInfo("Dropper / yükleyici belirtileri", "Dropper", "Uygulama başka uygulama kurup dinamik kod çalıştırabilir."),
        "SMS_PERMISSIONS" to RuleInfo("SMS izinleri", "SMS Hırsızı", "Uygulama SMS okuyup gönderebilir; kaynağına güvenmiyorsanız kurmayın."),
        "TELEGRAM_BOT" to RuleInfo("Gömülü Telegram bot token'ı", "Casus Yazılım", "Uygulama içinde komuta-kontrol amaçlı bir bot anahtarı var."),
        "HIDDEN_APP" to RuleInfo("Simgesiz gizli uygulama", "Casus Yazılım", "Başlatıcı simgesi olmayan, arka planda çalışan uygulama; stalkerware olabilir."),
        "ACCESSIBILITY_ABUSE" to RuleInfo("Erişilebilirlik servisi kullanımı", "Casus Yazılım", "Erişilebilirlik servisi ekranı okuyabilir; yalnızca güvendiğiniz uygulamalara izin verin."),
        "DEVICE_ADMIN_ABUSE" to RuleInfo("Cihaz yöneticisi kullanımı", "Casus Yazılım", "Uygulama cihaz yöneticisi olmak istiyor; kaldırılması zorlaşabilir."),
        "BOOT_PERSISTENCE" to RuleInfo("Açılışta otomatik başlama", "Casus Yazılım", "Uygulama cihaz açılışında kendini başlatıyor."),
        "OVERLAY_ATTACK" to RuleInfo("Ekran üstü + erişilebilirlik", "Banka Truvası", "Diğer uygulamaların üzerine çizim yapıp girişleri çalabilir."),
        "DYNAMIC_CODE_LOADING" to RuleInfo("Dinamik kod yükleme", "Dropper", "Çalışma anında dışarıdan kod yükleyebilir."),
        "PACKER_DETECTED" to RuleInfo("Paketleyici tespit edildi", "Paketlenmiş Dosya", "Kod paketlenmiş; analiz zorlaştırılmış olabilir."),
        "MANY_EMBEDDED_URLS" to RuleInfo("Çok sayıda gömülü adres", "Şüpheli APK", "Uygulama içinde çok sayıda dış bağlantı var."),
        "EMBEDDED_IPS" to RuleInfo("Gömülü IP adresleri", "Şüpheli APK", "Sabit IP adresleri komuta-kontrol sunucusu olabilir."),
        "DEBUG_CERT" to RuleInfo("Debug sertifikası", "Şüpheli APK", "Uygulama geliştirici debug anahtarıyla imzalanmış; resmî sürüm değil."),
        "EXPIRED_CERT" to RuleInfo("Süresi geçmiş sertifika", "Şüpheli APK", "İmza sertifikasının süresi dolmuş."),
        "APK_PARSE_ERROR" to RuleInfo("APK okunamadı", "Şüpheli APK", "Paket yapısı bozuk veya kasıtlı olarak bozulmuş olabilir."),
        "ZIP_TOO_MANY_ENTRIES" to RuleInfo("Aşırı dosya sayısı", "Şüpheli Arşiv", "Arşivde olağandışı çok dosya var; zip bomb olabilir."),
        "ZIP_BOMB_SUSPECT" to RuleInfo("Zip bomb şüphesi", "Şüpheli Arşiv", "Açıldığında diski doldurabilir; açmayın."),

        // --- Arşiv ---
        "ZIP_BOMB" to RuleInfo("Zip bomb", "Şüpheli Arşiv", "Sıkıştırma oranı aşırı yüksek; açmayın."),
        "ZIP_SLIP" to RuleInfo("Dizin kaçışı (zip-slip)", "Şüpheli Arşiv", "Arşiv, hedef klasör dışına dosya yazmaya çalışıyor."),
        "NESTED_TOO_DEEP" to RuleInfo("Aşırı iç içe arşiv", "Şüpheli Arşiv", "İç içe arşiv derinliği güvenli sınırı aşıyor."),
        "ARCHIVE_TYPE" to RuleInfo("Sıkıştırılmış arşiv", "Arşiv", "İçerik derinlemesine taranamadı; kaynağına güvenin."),
        "ENCRYPTED_ARCHIVE" to RuleInfo("Şifreli arşiv", "Şüpheli Arşiv", "İçerik taranamıyor; şifreli arşivlere dikkat edin."),
        "OOXML_MACRO" to RuleInfo("Office makrosu", "Zararlı Belge", "Belge makro içeriyor; düzenlemeyi etkinleştirmeyin."),
        "OOXML_EXTERNAL" to RuleInfo("Harici Office şablonu", "Zararlı Belge", "Belge dışarıdan içerik çekiyor; kaynağı doğrulanmamış."),
        "OOXML_PARSE_ERROR" to RuleInfo("Office dosyası okunamadı", "Şüpheli Belge", "Belge yapısı bozuk; dikkatli açın."),
        "OLD_TARGET" to RuleInfo("Eski Android hedefi", "Şüpheli APK", "Uygulama çok eski Android sürümleri için derlenmiş; güvenlik açıkları içerebilir."),
        "ZIP_PARSE_ERROR" to RuleInfo("Arşiv okunamadı", "Şüpheli Arşiv", "Arşiv yapısı bozuk veya zarar görmüş."),

        // --- Belge ---
        "POLYGLOT" to RuleInfo("Çoklu format (polyglot)", "Zararlı Belge", "Dosya aynı anda birden fazla format gibi davranıyor."),
        "PDF_JS" to RuleInfo("PDF içinde JavaScript", "Zararlı Belge", "PDF betik çalıştırabilir; güvenilmeyen PDF'leri açmayın."),
        "PDF_AUTO_ACTION" to RuleInfo("PDF otomatik eylemi", "Zararlı Belge", "PDF açılır açılmaz işlem tetikleyebilir."),
        "PDF_EMBEDDED_FILE" to RuleInfo("PDF gömülü dosyası", "Zararlı Belge", "PDF içinde gizlenmiş dosya var."),
        "PDF_LAUNCH" to RuleInfo("PDF harici program başlatma", "Zararlı Belge", "PDF başka bir program çalıştırmaya çalışıyor."),
        "OFFICE_MACRO" to RuleInfo("Office makrosu", "Zararlı Belge", "Belge makro içeriyor; makroları etkinleştirmeyin."),
        "AUTO_MACRO" to RuleInfo("Otomatik çalışan makro", "Zararlı Belge", "Belge açılınca makro çalışabilir."),
        "RTF_OBJECT" to RuleInfo("RTF gömülü nesnesi", "Zararlı Belge", "RTF içinde gömülü nesne var; exploit olabilir."),
        "EXECUTABLE_DISGUISED" to RuleInfo("Belge içinde çalıştırılabilir", "Zararlı Belge", "Belge gibi görünen dosya aslında program."),
        "HTML_SCRIPT" to RuleInfo("HTML betiği", "Şüpheli Belge", "HTML dosyası betik içeriyor; tarayıcıda açarken dikkat edin."),
        "HTML_OBFUSCATED" to RuleInfo("Karıştırılmış HTML", "Zararlı Belge", "Betik gizlenmiş/şifrelenmiş görünüyor."),

        // --- Betik ---
        "SCRIPT_DOWNLOAD_EXEC" to RuleInfo("İndir ve çalıştır", "Zararlı Betik", "Betik uzaktan kod indirip çalıştırıyor."),
        "SCRIPT_OBFUSCATION" to RuleInfo("Betik gizleme", "Zararlı Betik", "Kod okunaksızlaştırılmış; zararlı olabilir."),
        "SCRIPT_ENCODED_COMMAND" to RuleInfo("Kodlanmış komut", "Zararlı Betik", "Base64/encoded komut çalıştırılıyor."),
        "SCRIPT_REVERSE_SHELL" to RuleInfo("Ters kabuk", "Zararlı Betik", "Uzaktan komut çalıştırma bağlantısı kurmaya çalışıyor."),
        "SUSPICIOUS_COMMAND" to RuleInfo("Şüpheli komut", "Zararlı Betik", "Dosya kabuk komutları içeriyor."),
        "SUSPICIOUS_TLD" to RuleInfo("Şüpheli alan adı", "Kimlik Avı", "Dosya riskli üst düzey alan adlarına bağlantı içeriyor."),
        "MANY_URLS" to RuleInfo("Çok sayıda bağlantı", "Şüpheli Dosya", "Dosya çok sayıda URL içeriyor."),
        "IP_URL" to RuleInfo("IP adresli bağlantı", "Kimlik Avı", "Alan adı yerine doğrudan IP kullanan bağlantı."),

        // --- PE / Windows ---
        "PE_PACKED" to RuleInfo("Paketlenmiş Windows programı", "Şüpheli Windows Programı", "Bölüm adları bilinen paketleyicilerle eşleşiyor."),
        "PE_SUSPICIOUS_IMPORTS" to RuleInfo("Şüpheli Windows API'leri", "Şüpheli Windows Programı", "Bellek yazma/uzaktan işlem gibi riskli API'ler kullanılıyor."),
        "PE_RWX_SECTION" to RuleInfo("Yazılabilir + çalıştırılabilir bölüm", "Şüpheli Windows Programı", "Kendini değiştiren kod belirtisi."),
        "PE_HIGH_ENTROPY" to RuleInfo("Yüksek entropili PE", "Paketlenmiş Dosya", "Program bölümü şifrelenmiş/paketlenmiş."),
        "PE_INVALID" to RuleInfo("Bozuk PE başlığı", "Şüpheli Windows Programı", "Çalıştırılabilir dosya başlığı geçersiz."),

        // --- Genel ---
        "HIGH_ENTROPY" to RuleInfo("Yüksek entropi", "Paketlenmiş Dosya", "İçerik şifrelenmiş veya sıkıştırılmış olabilir."),
        "PACKER_SIGNATURE" to RuleInfo("Paketleyici izi", "Paketlenmiş Dosya", "Dosya bilinen bir paketleyici izi taşıyor."),
        "FUZZY_SIMILAR" to RuleInfo("Bilinen zararlıya benzer", "Zararlı Yazılım", "İçerik bilinen bir zararlı örnekle büyük ölçüde benzer."),
        "DETECTOR_ERROR" to RuleInfo("Analiz hatası", "Bilinmiyor", "Bir dedektör bu dosyayı inceleyemedi.")
    )

    fun info(ruleId: String): RuleInfo =
        rules[ruleId] ?: RuleInfo(prettify(ruleId), "Şüpheli Dosya", "Dosyayı dikkatle inceleyin.")

    fun title(ruleId: String): String = info(ruleId).title

    fun severityLabel(severity: Severity): String = when (severity) {
        Severity.CRITICAL -> "KRİTİK"
        Severity.HIGH -> "YÜKSEK"
        Severity.MEDIUM -> "ORTA"
        Severity.LOW -> "DÜŞÜK"
        Severity.INFO -> "BİLGİ"
    }

    fun verdictLabel(verdict: Verdict): String = when (verdict) {
        Verdict.CLEAN -> "TEMİZ"
        Verdict.SUSPICIOUS -> "ŞÜPHELİ"
        Verdict.DANGEROUS -> "TEHLİKELİ"
    }

    /**
     * Bulgulardan kullanıcıya gösterilecek tehdit türünü seçer.
     * Önce dedektörün bildirdiği aileye, sonra kural önceliğine bakar.
     */
    fun threatType(findings: List<Finding>): String {
        if (findings.isEmpty()) return "Temiz"
        findings.firstNotNullOfOrNull { f ->
            f.evidence["family"]?.takeIf { it.isNotBlank() && it != "unknown" }
        }?.let { return it }

        val ruleIds = findings.map { it.ruleId }.toSet()
        fun has(vararg ids: String) = ids.any { it in ruleIds }

        return when {
            has("EICAR_TEST", "KNOWN_BAD_HASH", "MALWAREBAZAAR_HIT", "VIRUSTOTAL_HIT", "FUZZY_SIMILAR") -> "Zararlı Yazılım"
            has("SMS_STEALER_COMBO") || (has("SMS_PERMISSIONS") && has("ACCESSIBILITY_ABUSE")) -> "SMS Hırsızı"
            has("BANKER_COMBO", "OVERLAY_ATTACK") -> "Banka Truvası"
            has("SPYWARE_COMBO", "HIDDEN_APP", "DEVICE_ADMIN_ABUSE", "BOOT_PERSISTENCE") -> "Casus Yazılım"
            has("RANSOMWARE_COMBO") -> "Fidye Yazılımı"
            has("DROPPER_COMBO", "DYNAMIC_CODE_LOADING") -> "Dropper"
            has("PDF_JS", "PDF_AUTO_ACTION", "PDF_EMBEDDED_FILE", "PDF_LAUNCH", "OFFICE_MACRO", "AUTO_MACRO", "OOXML_MACRO", "RTF_OBJECT", "POLYGLOT", "EXECUTABLE_DISGUISED") -> "Zararlı Belge"
            has("SCRIPT_DOWNLOAD_EXEC", "SCRIPT_OBFUSCATION", "SCRIPT_ENCODED_COMMAND", "SCRIPT_REVERSE_SHELL", "SUSPICIOUS_COMMAND") -> "Zararlı Betik"
            has("PE_PACKED", "PE_SUSPICIOUS_IMPORTS", "PE_RWX_SECTION", "PE_HIGH_ENTROPY", "PE_INVALID") -> "Şüpheli Windows Programı"
            has("PACKER_DETECTED", "PACKER_SIGNATURE", "HIGH_ENTROPY") -> "Paketlenmiş Dosya"
            has("SUSPICIOUS_TLD", "IP_URL") -> "Kimlik Avı"
            has("DANGEROUS_PERM_COMBO", "APK_PARSE_ERROR", "DEBUG_CERT", "EXPIRED_CERT", "MANY_EMBEDDED_URLS", "EMBEDDED_IPS", "ZIP_BOMB", "ZIP_BOMB_SUSPECT", "ZIP_SLIP", "ZIP_TOO_MANY_ENTRIES", "NESTED_TOO_DEEP") -> "Şüpheli Uygulama/Dosya"
            has("HIDDEN_EXTENSION", "DOUBLE_EXT", "RTLO", "RTLO_EXT", "HOMOGLYPH_NAME", "TRAILING_SPACES", "EXT_MAGIC_MISMATCH") -> "Gizlenmiş Dosya"
            else -> "Şüpheli Dosya"
        }
    }

    /** En yüksek ağırlıklı bulguya göre öneri metni. */
    fun advice(findings: List<Finding>): String =
        findings.maxByOrNull { it.weight }?.let { info(it.ruleId).advice }
            ?: "Dosyayı dikkatle inceleyin."

    private fun prettify(id: String): String =
        id.split('_').joinToString(" ") { part ->
            part.lowercase().replaceFirstChar { it.uppercase() }
        }
}
