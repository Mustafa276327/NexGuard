# NexGuard — Ana Proje Promptu

> Bu dosya, NexGuard projesini geliştirecek yapay zekâ kodlama ajanına (Claude Code vb.) verilecek ana talimattır.
> Dil: Türkçe. Kod, değişken adları ve commit mesajları İngilizce olabilir.

---

## 0. ROL VE ÇALIŞMA BİÇİMİ

Sen kıdemli bir Android güvenlik mühendisisin (Kotlin, Rust/NDK, ağ ve zararlı yazılım analizi bilgisi). Görevin, aşağıda tanımlanan **NexGuard** uygulamasını adım adım, test edilebilir şekilde geliştirmek.

**Çalışma kuralları**
1. **Dikey dilimlerle ilerle.** Önce MVP'yi uçtan uca çalıştır, sonra ek özellikler. Bir aşama kabul kriterlerini geçmeden sonrakine başlama.
2. Her aşamada: kısa plan → kod → test → kabul kriteri kontrolü → özet.
3. Belirsizlik varsa **en güvenli varsayımı** seç, varsayımı not et, devam et. Sadece ilerlemeyi gerçekten engelliyorsa soru sor.
4. Bilmediğin API'yi uydurma. Android 16 (API 36) davranış değişikliklerinden emin değilsen resmi dokümana bak ve kodu bunu belirten yorumla işaretle.
5. Gizli anahtarları (API key, bot token, keystore) **asla** koda gömme.
6. Her özelliği hangi tehdit modeli maddesine hizmet ettiğiyle ilişkilendir. Hiçbir tehdide bağlanmayan özellik ertelenir.
7. Cevap biçimi: kısa, net, madde madde. Gereksiz açıklama yok.

---

## 1. PROJE TANIMI

| Alan | Değer |
|------|-------|
| Ad | **NexGuard** |
| Tür | Android cihaz güvenlik uygulaması |
| Kullanıcı | Tek kişi (sahibi). Tüm izinler istenir. |
| Hedef platform | Android 16 (API 36), minSdk 29 (Android 10) |
| Dağıtım | Sadece kişisel APK (Play Store yok), GitHub Releases |
| Derleme | GitHub Actions (Termux'tan `git push` ile tetiklenir) |
| Dil/Stack | Kotlin + Jetpack Compose, çekirdek tarayıcı Rust (NDK) |
| Veri politikası | Çevrimdışı öncelikli. Kişisel veri cihazdan çıkmaz. |
| Geliştirici ortamı | Termux (arm64) → Android Studio kullanılamaz, her şey CI üzerinden doğrulanır |

### 1.1 Tehdit modeli (her özellik bunlardan birine bağlanır)
1. **Kötü APK / zararlı dosya** → her indirmede otomatik kontrol
2. **Telefon dolandırıcılığı** (SMS, sahte link, uzaktan erişim, sahte uygulama)
3. **Ağ saldırısı** (sahte Wi-Fi, DNS/ARP, MITM)
4. **Girilen siteler** (phishing, zararlı alan adı)

### 1.2 Kapsam dışı (bilinçli olarak yapılmayacak)
- Başkasının ağına/cihazına saldırı, sızma, exploit aracı yok. Ağ araçları yalnızca sahibin kendi ağı ve cihazı içindir.
- Zararlı kod, exploit veya kötü amaçlı payload **yazma/üretme yok**. Test için yalnızca EICAR ve kendi ürettiğimiz zararsız sahte örnekler.
- Play Store uyumluluğu hedef değil.
- Bulut hesabı, kullanıcı takibi, telemetri yok.

---

## 2. MİMARİ

### 2.1 Modüller (Gradle multi-module)
```
:app                 → tek Activity, navigation, DI (Hilt)
:core:common         → ortak modeller, Result tipleri, logger
:core:database       → Room (SQLCipher ile şifreli), DAO'lar, migration
:core:eventbus       → olay veriyolu (Kotlin Flow), zaman çizelgesinin kaynağı
:core:security       → Keystore, şifreleme, biyometrik, tamper kontrolü
:feature:onboarding  → kurulum sihirbazı + izin yönetimi
:feature:home        → ana ekran, TARA butonu, güvenlik skoru
:feature:scan        → tarama orkestrasyonu, sonuç ekranları
:feature:monitor     → indirme izleme, yükleme izleme, gerçek zamanlı koruma
:feature:web         → site koruması (Accessibility + Nexus-Browser kanalı)
:feature:network     → Wi-Fi, VPN/DNS filtre, güvenlik duvarı
:feature:device      → cihaz durumu denetimi, snapshot farkı
:feature:quarantine  → karantina yönetimi
:feature:alerts      → popup (overlay), bildirim, tam ekran bildirim
:feature:settings    → ayarlar, izin paneli, yedek/geri yükleme, log ekranı
:engine:scanner-rs   → Rust çekirdek (UniFFI ile Kotlin köprüsü)
:engine:signatures   → imza DB yönetimi, güncelleme, doğrulama
```

### 2.2 Temel prensipler
- **Detector → Finding** arayüzü: her dedektör aynı sözleşmeyi uygular, eklenti gibi takılır.
- **Olay veriyolu:** tarama, izleme, ağ ve uyarı olayları tek akıştan geçer ve zaman çizelgesine yazılır.
- **Servis dayanıklılığı:** ön plan servisi + WorkManager + boot receiver + watchdog. Servis ölürse yeniden başlar, çökme döngüsü korumalıdır.
- **Parser izolasyonu:** güvenilmeyen dosya (APK/ZIP/PDF) ayrıştırması `isolatedProcess` içinde, izinsiz, zaman aşımı + bellek + derinlik sınırlarıyla çalışır (zip bomb, zip-slip koruması).
- **Açıklanabilirlik:** her karar için "neden işaretlendi" verisi (kural, sinyal, ağırlık) saklanır.

### 2.3 Veri modeli (taslak)
```
scan_results(id, target_path, sha256, verdict, score, started_at, duration_ms)
findings(id, scan_id, detector, rule_id, severity, weight, evidence_json)
quarantine(id, original_path, stored_name, sha256, reason, created_at, restored_at)
sites(domain, first_seen, last_checked, score, verdict, signals_json, user_decision)
network_events(id, type, ssid, bssid, details_json, ts)
device_snapshots(id, ts, perms_json, admins_json, a11y_json, notif_json, certs_json)
allowlist(id, kind, value, note)          // paket, hash, alan adı
timeline(id, ts, source, kind, summary, ref_id)
settings(key, value)
```
Not: `sites` ve `timeline` kayıtlarında **tam URL ve mesaj içeriği saklanmaz**, yalnızca alan adı ve sonuç.

---

## 3. İLK AÇILIŞ — KURULUM SİHİRBAZI

Sihirbaz adım adım ilerler. Her adım: kısa açıklama, "İzin ver" butonu (ilgili Ayarlar sayfasına gider), durum rozeti (✅/❌). Kullanıcıdan dönünce izin otomatik yeniden kontrol edilir. Özet ekranında eksik izinler kırmızı listelenir.

**Adım 0 — Kısıtlı ayarlar:** APK yan yüklendiği için Android 13+ erişilebilirlik, bildirim erişimi vb. kısıtlar. Rehber metni: *Ayarlar → Uygulamalar → NexGuard → ⋮ → Kısıtlı ayarlara izin ver.*

**Ayarlardan elle verilenler**
- Cihaz yöneticisi (`DeviceAdminReceiver`)
- Erişilebilirlik servisi
- Bildirim erişimi (`NotificationListenerService`)
- Kullanım erişimi (`PACKAGE_USAGE_STATS`)
- Diğer uygulamaların üzerinde çizme (`SYSTEM_ALERT_WINDOW`)
- Tüm dosyalara erişim (`MANAGE_EXTERNAL_STORAGE`)
- Tam ekran bildirim (`USE_FULL_SCREEN_INTENT`)
- Kesin alarm (`SCHEDULE_EXACT_ALARM`)
- Pil optimizasyonu muafiyeti (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)
- VPN izni (`VpnService.prepare` sistem onayı)

**Çalışma zamanı izinleri**
- Bildirim (`POST_NOTIFICATIONS`)
- Konum (hassas + arka plan), `NEARBY_WIFI_DEVICES`
- `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`
- Kamera
- SMS (`READ_SMS`, `RECEIVE_SMS`), arama kaydı, telefon durumu, rehber
- Medya izinleri (`READ_MEDIA_*`, `ACCESS_MEDIA_LOCATION`)
- Biyometrik

**Manifest (onaysız)**
`QUERY_ALL_PACKAGES`, `INTERNET`, `ACCESS_NETWORK_STATE`, `CHANGE_WIFI_STATE`, `CHANGE_NETWORK_STATE`, `FOREGROUND_SERVICE` (+ doğru tür: `specialUse`/`dataSync`), `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `USE_BIOMETRIC`

**Üretici notu:** Samsung/Xiaomi/Oppo gibi cihazlarda ek "otomatik başlat" ve pil kısıtı adımı rehber olarak gösterilir (cihaz modeline göre).

**Watchdog:** Sonradan bir izin kapatılırsa (erişilebilirlik, VPN, bildirim erişimi) NexGuard fark eder ve kritik uyarı verir.

---

## 4. ANA EKRAN

```
┌────────────────────────────┐
│ ☰                       ⚙  │
│                            │
│         ╭────────╮         │
│         │  TARA  │         │   ← büyük yuvarlak buton
│         ╰────────╯         │
│   Son tarama: dün 21:14    │   ← küçük yazı
│   Güvenlik skoru: 92 ✅     │
│                            │
│ [Dosya Tara] [Wi-Fi]       │
│ [Uygulamalar] [Cihaz]      │
│ [Siteler] [Geçmiş]         │
│ [Karantina] [Ayarlar]      │
└────────────────────────────┘
```
- TARA: dokun = hızlı/tam (ayarlı varsayılan), basılı tut = tür seçimi (Hızlı / Tam / Özel).
- Tarama sırasında buton ilerleme halkasına döner; canlı sayaç (taranan dosya, bulunan sorun).
- Ana ekran widget'ı ve Quick Settings "Tara" kutucuğu.
- Koyu/AMOLED tema, Material You, TR/EN.

---

## 5. TARAMA MOTORU

### 5.1 Katmanlar (ucuzdan pahalıya; yeterli kanıt bulununca durur)
1. **Ön süzgeç:** uzantı ↔ magic byte uyuşmazlığı, çift uzantı, RTLO, sahte ad kalıpları (`e-Devlet.apk`, `Kargo.apk`)
2. **Hash:** SHA-256 → yerel kötü hash DB (Bloom/cuckoo filtre + tam DB)
3. **İmza/Kural:** Aho-Corasick çoklu desen, YARA benzeri kural seti
4. **APK statik analiz:** manifest, izin kombinasyonları, imza/sertifika, bileşenler, gömülü URL/IP, dinamik kod yükleme (`DexClassLoader`, reflection), gömülü `.so`, paketleyici izi
5. **Entropi/paketleyici** tespiti
6. **Arşiv içi tarama:** zip/rar/7z, iç içe sınırı, şifreli arşiv uyarısı, zip bomb limiti
7. **Belge analizi:** PDF içi JS, Office makrosu, polyglot dosya
8. **Bulanık hash:** TLSH/ssdeep varyant eşleşmesi
9. **Bulut doğrulama (isteğe bağlı):** VirusTotal/MalwareBazaar **yalnızca hash** sorgusu, önbellekli
10. **ML (v2):** cihaz içi TFLite modeli

### 5.2 Veri kaynakları (hepsi ücretsiz/açık; lisanslara uy)
- MalwareBazaar (abuse.ch) APK hash akışı
- ClamAV imzalarının **Android (`Andr.*`) alt kümesi** (tam DB telefona yüklenmez)
- Açık YARA kural setleri (signature-base, Yara-Rules)
- Site: Google Safe Browsing, URLhaus, PhishTank, OpenPhish, Phishing.Database, StevenBlack/Hagezi DNS listeleri
- Kota/ücretsiz limitler değişebilir → **her sonuç önbelleğe alınır**, kota korumalı istemci yazılır.
- İmza DB'si **Ed25519 ile imzalı, sürümlü**, delta güncellemeli; Termux'tan manuel yükleme desteklenir.

### 5.3 Skorlama ve hüküm
- 0–100 risk skoru; sinyal ağırlıkları açıklanabilir şekilde saklanır.
- **Temiz** (<30) / **Şüpheli** (30–69) / **Tehlikeli** (≥70).
- **Kural:** "Tehlikeli" hükmü için en az **iki bağımsız sinyal** (ör. imza + davranış). Tek zayıf sinyal en fazla "Şüpheli" üretir.
- Sistem ve Google/OEM paketleri için güvenilir liste + imza doğrulaması (yanlış alarmı azaltır).
- Kullanıcı "Yanlış alarm" bildirebilir → beyaz listeye eklenir.

### 5.4 Performans
- Parçalı okuma, mmap, öncelik kuyruğu.
- Artımlı tarama: (yol + mtime + boyut) önbelleği → ilk tarama uzun, sonrakiler saniyeler.
- Sıcaklık/pil bilinci: ısınınca yavaşla, ağır işi şarj + boşta çalıştır.

---

## 6. İNDİRME İZLEME VE UYARI SİSTEMİ (MVP ÇEKİRDEĞİ)

### 6.1 İzleme
- Ön plan servisi + `FileObserver` (Download, Telegram, WhatsApp, tarayıcı klasörleri) + `MediaStore` ContentObserver + `DownloadManager` yayını.
- Geçici dosyalar (`.crdownload`, `.part`, `.tmp`) beklenir; ad değişip boyut sabitlenince tarama başlar.
- `PACKAGE_ADDED` ile yeni uygulamalar ayrıca yakalanır. **APK iner inmez taranır, yükleme ekranı açılmadan uyarılır.**
- Android 16 kısıtları: `Android/data` altı sınırlı → MediaStore + genel klasörler öncelikli.

### 6.2 Uyarı seviyeleri
| Seviye | Davranış |
|--------|----------|
| Tehlikeli | Tam ekran bildirim + overlay popup. Yüksek güvenle **otomatik karantina** ("Geri al" butonlu). |
| Şüpheli | Bildirim, dokununca detay. Otomatik işlem yok, kullanıcıya sorar. |
| Temiz | Sessiz, yalnızca geçmişe yazılır. |

> Android arka plandan Activity açmayı kısıtladığı için popup, **tam ekran bildirim + overlay** birlikte kullanılır.

### 6.3 Popup içeriği
Dosya adı, konum, **tehdit türü** (Trojan, Banker, SMS stealer, Spyware/Stalkerware, Ransomware, Adware, Dropper, Exploit, Phishing dosyası, Şüpheli APK), risk skoru, tespit nedenleri.
Butonlar: **Karantinaya al** (önerilen) · **Sil** · **Detay** · **Yoksay (bir kez)** · **Güvenilir say** · **Raporla**

### 6.4 Karantina
- Dosya uygulamanın özel dizinine taşınır, yeniden adlandırılır, çalıştırılamaz hale getirilir, Keystore ile şifrelenir.
- Geri yükleme, kalıcı silme, "güvenli işaretle".
- Meta veri (kaynak yol, hash, sebep) Room'da saklanır.

---

## 7. SİTE VE LİNK KORUMASI

**URL yakalama**
- **Chrome:** Accessibility servisiyle adres çubuğu okuma.
- **Nexus-Browser:** doğrudan entegrasyon (Intent/ContentProvider); kanal `signature` izniyle korunur (başka uygulama sahte veri yollayamaz).
- Uygulama içi linkler (WhatsApp, Telegram vb.): yerel VPN DNS katmanı.
- İzlenen tarayıcı listesi ayarlardan değiştirilebilir.

**Araştırma akışı (arka planda, sayfayı yavaşlatmaz)**
1. Yerel DB/önbellek → 2. çevrimdışı kara listeler → 3. alan adı sezgileri (yeni domain, typosquat, punycode, IP'li adres, şüpheli TLD) → 4. Safe Browsing (hash-prefix) → 5. itibar araştırması → 6. birleşik skor, DB'ye kayıt.

**İtibar araştırması**
- Google: **resmi Programmable Search API** + önbellek (sayfa kazıma yok).
- Şikayetvar: **zayıf sinyal**, hız sınırlı, isteğe bağlı. Başarısız olursa sistem yine çalışır. Şikayet sayısı tek başına "tehlikeli" demez.

**Gizlilik:** dışarıya yalnızca **alan adı** gider (tam URL değil); bilinen güvenli siteler beyaz listede kalır.

**Bildirim:** Tehlikeli → tam ekran uyarı + "siteden çık / engelle"; Şüpheli → bildirim; Temiz → sessiz.

---

## 8. DOLANDIRICILIK KORUMASI (Türkiye'ye özel)

- **Uzaktan erişim uyarısı:** AnyDesk/TeamViewer/RustDesk/ekran paylaşımı açıkken banka/kripto uygulaması açılırsa kırmızı uyarı.
- **Mesaj/link analizi:** SMS, WhatsApp bildirimleri; kargo, e-Devlet, banka, bahis, kripto kalıpları; kısa link çözümleme. Yerel kurallar (+ v2'de TFLite). Mesaj içeriği saklanmaz, sadece sonuç.
- **Arayan numara itibarı:** yerel spam DB.
- **Klon/sahte uygulama:** aynı paket adı + farklı imza (WhatsApp, Telegram, bankalar için bilinen imzalar saklanır), taklit ad/ikon, güncellemede ani izin artışı.
- **Pano koruması:** kripto adres değiştirme saldırısı izleme.

---

## 9. AĞ GÜVENLİĞİ

- **Wi-Fi tarama:** SSID, BSSID, kanal, sinyal, şifreleme (Açık/WEP/WPA/WPA2/WPA3), WPS.
- **Riskler:** açık ağ, WEP, Evil Twin (aynı SSID/farklı BSSID veya şifreleme), gateway MAC değişimi, ARP spoofing şüphesi, DNS değişimi, captive portal.
- **Kendi ağında:** cihaz keşfi (IP/MAC/üretici), açık port denetimi, router (varsayılan şifre, UPnP, açık yönetim paneli). Yalnızca sahibin ağı.
- **Yerel VPN (`VpnService`):** DNS filtreleme (kötü alan, reklam/izleyici), DoH desteği, **uygulama bazlı güvenlik duvarı**, beaconing (düzenli aralıkla aynı sunucuya bağlanma) ve gece yüksek upload tespiti, offline GeoIP ülke haritası.
- **MITM tespiti:** kullanıcı CA sertifikası kurulumu, yeni proxy/özel DNS ayarı.
- HTTPS içeriği görülmez, yalnızca alan adı (dürüst sınır).

---

## 10. CİHAZ GÜVENLİĞİ VE GİZLİLİK

- Root/bootloader, verified boot, Play Integrity, Magisk/Zygisk/Frida/Xposed izleri, SELinux, USB hata ayıklama, geliştirici seçenekleri, bilinmeyen kaynaklar, kilit ekranı, şifreleme, yama seviyesi (+ bilinen açık uyarısı), eski WebView/Chrome.
- **Snapshot farkı:** izin, erişilebilirlik, cihaz yöneticisi, bildirim erişimi, kullanıcı CA listesi her gün kaydedilir; yeni eklenen uyarılır.
- **Stalkerware:** gizli uygulama, ikonsuz + açılışta başlayan, cihaz yöneticisi/erişilebilirlik sahipleri.
- **Sensör denetimi:** hangi uygulama kamera/mikrofon/konum kullandı (zaman çizelgesi).
- **Tracker SDK tespiti:** açık kaynak imza listeleriyle (Exodus Privacy tarzı).
- **Overlay/tapjacking** tespiti.
- **Ransomware canary:** önemli klasörlere yem dosyalar, değişirse alarm + şüpheli uygulamayı işaretle.
- **USB (juice jacking) ve ADB** bağlantı uyarısı, **Bluetooth** bilinmeyen/takip cihazı tarama.
- **Şifreli kasa, uygulama kilidi, güvenli silme, zorlama PIN, EXIF temizleyici, HIBP kontrolü (k-anonymity).**
- **Hırsızlık önleme:** davetsiz misafir fotoğrafı, SIM değişimi uyarısı, Telegram bot komutları (`/konum /kilitle /ses /foto /durum`), son bilinen konum.
- **Uygulama dondurma:** şüpheli uygulamayı silmeden devre dışı bırak.
- **Panik butonu:** ağı kes, hassas uygulamaları kilitle, isteğe bağlı yerel veri/anahtar silme.

---

## 11. NEXGUARD'IN KENDİ GÜVENLİĞİ

Uygulama cihazdaki en güçlü izin setine sahip olacağı için kendisi hedeftir.
- Veritabanı ve karantina Android Keystore ile şifreli; açılışta biyometrik/PIN.
- İmza doğrulama ve tamper kontrolü; R8/obfuscation, native bütünlük kontrolü.
- Dışarıya yalnızca alan adı/hash gider. **Loglarda URL, mesaj içeriği, kişisel veri yok** (debug build dahil).
- Gezinme geçmişi **30 gün** sonra silinir; SMS içeriği saklanmaz.
- Nexus-Browser ↔ NexGuard kanalı `signature` izniyle korunur.
- Kaldırma denemesinde cihaz yöneticisi ile uyarı (Android'in izin verdiği ölçüde).
- Servis/izin kapanırsa watchdog uyarır.
- Güncelleme: **GitHub Releases + imza doğrulama**. **Keystore'un şifreli yedeği** zorunlu (kaybolursa güncelleme yapılamaz).

---

## 12. DERLEME, CI/CD VE TEST

**GitHub Actions**
- `debug` ve `release` iş akışları ayrı. Tetik: `push` (debug), `tag v*` (release).
- Kotlin + Rust (`cargo-ndk`, arm64-v8a öncelikli, önbellekli).
- İmzalama: keystore ve şifreler **GitHub Secrets**; APK artifact olarak yayınlanır, tag'de Release'e eklenir.
- Sürüm numaralandırma: `versionCode` CI'da otomatik, `versionName` semver.
- Otomatik: ktlint/detekt, birim testleri, Rust `clippy` + `cargo test`.

**Test**
- **EICAR** test dosyası ve kendi ürettiğimiz **zararsız sahte "zararlı" örnekler** (gerçek zararlı kullanılmaz).
- Yanlış alarm test seti: temiz uygulama/dosya havuzu, hedef yanlış alarm oranı ölçülür.
- Parser'lara fuzzing (`cargo-fuzz`).
- Zip bomb/zip-slip/derin iç içe arşiv testleri.
- Android 16 cihazda elle kabul testleri, uygulama içi **canlı log ekranı + dışa aktarma** (CI döngüsü yavaş olduğu için şart).

---

## 13. YOL HARİTASI VE KABUL KRİTERLERİ

### MVP
Kapsam: kurulum sihirbazı + izinler, ana ekran, manuel/tam tarama, indirme izleme, popup/bildirim, karantina, canlı log ekranı, CI hattı, kendi güncelleme kontrolü.
**Kabul kriterleri**
1. Sihirbaz tüm izinleri sırayla ister, eksikleri doğru gösterir, geri dönüşte yeniden kontrol eder.
2. EICAR dosyası indirildiğinde **10 saniyeden kısa sürede** tespit edilir, popup çıkar, karantinaya alınabilir ve geri yüklenebilir.
3. Temiz dosya indirmesi uyarı üretmez (yanlış alarm yok).
4. Uygulama kapatılıp ekran kilitliyken indirme izleme sürer (watchdog test edilir).
5. Tam taramada uygulama çökmez, ilerleme halkası ve sayaç çalışır.
6. Veritabanı şifreli, loglarda URL/mesaj içeriği yok.
7. CI, tag'den imzalı APK üretir; uygulama yeni sürümü kontrol edip imzayı doğrular.

### v1
ClamAV (Android alt kümesi) + YARA + MalwareBazaar hash, yükleme koruması, site kontrolü (Chrome + Nexus-Browser), Wi-Fi tarama, cihaz durumu, dolandırıcılık mesajı ve uzaktan erişim uyarısı.
**Kabul:** bilinen test phishing URL'si işaretlenir; Evil Twin simülasyonu uyarı verir; root/yama durumu doğru raporlanır; yanlış alarm oranı raporlanır.

### v2
ML modeli, DNS filtreleme + uygulama güvenlik duvarı, sensör denetimi, snapshot farkı, tracker tespiti.

### v3
Hırsızlık önleme, şifreli kasa, uygulama kilidi, Telegram bot, beaconing/GeoIP, router denetimi, ransomware canary, panik butonu.

### v4
Termux/MobSF derin analiz köprüsü, IOC içe aktarma (STIX/MISP uyumlu), Nexus (OSINT paneli) entegrasyonu, kullanıcı kural motoru.

---

## 14. RİSKLER VE DÜRÜST SINIRLAR

| Risk | Önlem |
|------|-------|
| Android arka plan/depolama kısıtları üreticiye göre değişir | Pil muafiyeti + otomatik başlat rehberi, watchdog |
| Hiçbir motor her zararlıyı yakalamaz | Katmanlı tespit + davranış izleme + 2 sinyal kuralı |
| Yanlış alarm güveni bozar | Güvenilir liste, "neden işaretlendi", geri bildirim |
| Root olmadan başka uygulamanın özel verisi okunamaz | Kapsam dışı, belgelenmiş sınır |
| VPN ile HTTPS içeriği görülemez | Yalnızca alan adı bazlı koruma |
| Google/Şikayetvar erişimi kırılgan | Resmi API + önbellek + zayıf sinyal |
| API kotaları değişebilir | Önbellek, kota korumalı istemci |
| Keystore kaybı | Şifreli yedek, belgelenmiş kurtarma |
| Proje çok büyük | Dikey dilim, aşama kabul kriteri olmadan ilerleme yok |

---

## 15. AÇIK MADDELER

- Cihaz marka/modeli henüz belirtilmedi (üreticiye özel pil/otomatik başlat rehberi için).
- API anahtarları (VirusTotal, Google, Telegram) kullanıcıdan alınıp CI Secrets'a eklenecek.
- ClamAV/YARA veri setlerinin son lisans kontrolü, kullanıma başlamadan önce yapılacak.
- ML eğitim verisi lisansları v2 başlamadan değerlendirilecek.

---

## 16. İLK GÖREVİN

1. Depo yapısını, Gradle modüllerini ve GitHub Actions `debug` iş akışını kur. Boş uygulama CI'da derlenip APK üretmeli.
2. Kurulum sihirbazı iskeleti + izin durum yöneticisi.
3. Ana ekran (TARA butonu, son tarama zamanı).
4. Olay veriyolu + şifreli Room kurulumu.
5. Ardından MVP maddelerini sırayla, her biri için kabul kriterini doğrulayarak ilerle.

Her adım sonunda kısaca: **ne yapıldı · hangi kabul kriteri geçti · sıradaki adım · varsayımlar**.
