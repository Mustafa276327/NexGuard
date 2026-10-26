# NexGuard

NexGuard, Android cihazlarda dosyaları, kurulu uygulamaları, indirmeleri, Wi-Fi ağlarını ve bağlantıları
inceleyen, çevrimdışı öncelikli bir güvenlik uygulamasıdır. Amaç, cihazdaki riskleri anlaşılır biçimde
göstermek ve tehditleri güvenli biçimde izole etmektir.

## Özellikler

- **Tam ve hızlı tarama:** Erişilebilir tüm depolama alanı, indirilenler, belgeler ve isteğe bağlı olarak
  kurulu APK'lar taranır. Canlı dosya akışı, filtreler, ilerleme, durdurma ve özet ekranı vardır.
- **Çok katmanlı analiz:** Bilinen hash imzaları, EICAR, APK/DEX ve izin analizi, arşiv incelemesi,
  PE başlıkları, betik komutları, belge içindeki çalıştırılabilir içerik, entropi ve IOC taraması.
- **İçerik tabanlı tür tanıma:** Uzantıya güvenilmez; dosyanın gerçek baytları (magic) incelenir. Yanlış
  etiketlenmiş görseller (ör. WebP/PNG dosyasının `.jpg` olması) tehdit sayılmaz; buna karşılık görsel
  kılığındaki çalıştırılabilir/arşiv içerik ve görselin sonuna eklenmiş yük (polyglot) yakalanır.
  APK'nın `.zip` olarak yeniden adlandırılması da tespit edilir.
- **Önbellek:** Yalnızca değişmemiş **temiz** dosyalar için kullanılır. Tehditler ve değişen dosyalar
  her zaman yeniden taranır; motor sürümü değişince önbellek geçersiz olur.
- **Dürüst tarama durumu:** Dosya bulunamazsa, okunamazsa, içeriği değişirse veya bir dedektör hata
  verirse sonuç asla "temiz" olarak sunulmaz. `TAMAM`, `KISMİ`, `BULUNAMADI`, `OKUNAMADI` ve `HATA`
  durumları ayrı gösterilir; yalnızca tamamlanmış ve tehdit içermeyen tarama yeşil `TEMİZ` sayılır.
  Teknik sorunlar tehdit sayılmaz ve önbelleğe yazılmaz.
- **Açıklamalı uyarılar:** Tehdit eşiğinin altında kalan bulgular kırmızı alarm yerine amber `UYARI`
  olarak gösterilir; canlı akışta "Uyarılar" filtresi ve oturum özetinde uyarı sayısı bulunur.
- **Python geliştirici betikleri:** `.py`, `.pyw` ve `.ipynb` dosyaları tehdit sayılmaz. İçe aktarılan
  modüller ve betiğin ne yaptığı (ağ erişimi, komut çalıştırma, gizleme, ters kabuk, kimlik bilgisi
  erişimi, kalıcılık, dosya silme/şifreleme) açıklamalı uyarı olarak raporlanır. EICAR veya bilinen
  kötü hash gibi **kesin imzalar** Python dosyalarında da tehdit olarak kalır.
- **Sistem uygulamaları güvenilir:** Sistem bölümündeki (`/system`, `/product`, `/vendor`, `/apex`)
  uygulamalar platform imzasıyla geldiği ve salt okunur olduğu için güvenli kabul edilir; tam taramaya
  dahil edilmez ve uygulama ekranında `GÜVENİLİR` olarak gösterilir. NexGuard kendi paketini de
  güvenilir sayar.
- **Gerçek zamanlı izleme:** İndirilenler ve yeni kurulan uygulamalar izlenir; tehdit bildirimi ve
  isteğe bağlı otomatik karantina uygulanır.
- **AES-256-GCM karantina:** Dosyalar Android Keystore anahtarıyla parçalı şifrelenir. Geri yüklemede
  SHA-256 bütünlük doğrulaması yapılır.
- **Tehdit ayrıntısı:** Virüs/tehdit türü, risk puanı, bulgular, öneriler, karantinaya alma ve kalıcı
  silme seçenekleri.
- **Uygulama denetimi:** Tehlikeli izinler, eski hedef SDK, debug yapısı, bilinmeyen kaynak ve APK
  derin analizi. Sistem uygulamaları ve NexGuard kendisi güvenilir kabul edilir; derin analiz yapılmaz.
- **Cihaz denetimi:** Root, SELinux, ekran kilidi, şifreleme, USB hata ayıklama, yama yaşı, cihaz
  yöneticisi ve pil optimizasyonu kontrolleri.
- **Wi-Fi denetimi:** Açık/WEP ağlar ve aynı SSID'nin farklı güvenlik türleriyle yayınlanması
  (evil-twin şüphesi) işaretlenir.
- **Site kontrolü:** URL'ler çevrimdışı sezgisel kurallarla puanlanır; HTTP, IP host, punycode,
  kısaltıcı, şüpheli TLD, `@` yönlendirmesi ve kimlik avı kelimeleri tespit edilir. Engellenen alan
  adları her zaman tehlikeli kabul edilir.
- **Canlı log:** Tarama, izleme ve karantina olayları bellekte tutulur; dosya yolu ve kişisel içerik
  yazılmaz.

## Tarama durumları

| Durum | Anlamı | Tehdit sayılır mı? |
|---|---|---|
| `TAMAM` | Tüm dedektörler çalıştı, dosya tarama sırasında değişmedi. | Hükme göre |
| `KISMİ` | Bir/birden fazla dedektör hata verdi veya dosya tarandığı sırada değişti. | Hayır (hüküm kesin değil) |
| `BULUNAMADI` | Dosya silinmiş/taşınmış ya da yol dosya değil. | Hayır |
| `OKUNAMADI` | Dosya var ama izin/erişim nedeniyle okunamadı. | Hayır |
| `HATA` | İçerik okunamadı, SHA-256 hesaplanamadı. | Hayır |

Karar kuralı: `ScanResult.isCleanFile` yalnızca `status == COMPLETED && verdict == CLEAN` olduğunda
`true` döner. Karantina, bildirim ve tehdit sayacı yalnızca `verdict` alanına bakmaya devam eder;
teknik hata sonuçları önbelleğe yazılmaz ve oturum özetinde "sorun" olarak sayılır.

## Gizlilik ve güvenlik modeli

- Dosyalar varsayılan olarak cihazdan çıkmaz. Bulut doğrulama varsayılan **kapalıdır**; açıldığında
  yalnızca yerel analizde **şüpheli** bulunan dosyanın SHA-256 hash'i sorgulanır.
- Veritabanı SQLCipher ile şifrelenir; anahtar Android Keystore/EncryptedSharedPreferences korumasındadır.
- Karantina dosyaları uygulamanın özel dizininde tutulur; diğer uygulamalar erişemez.
- Yalnızca gerçekten gerekli izinler istenir: tüm dosyalara erişim, Wi-Fi taraması için konum/yakın
  cihazlar, bildirimler ve isteğe bağlı cihaz yöneticisi.
- SMS, kişiler, kamera ve mikrofon izinleri istenmez.
- İmzalama anahtarı kişisel/sideload dağıtım içindir; üretim/mağaza dağıtımında CI secret kullanın.

## Modüller

```
app/                 Compose UI, navigasyon, servisler, Hilt bağlantıları
core/common          Modeller, risk puanlama, tehdit kataloğu, URL analizi
core/database        Room + SQLCipher veritabanı, DAO'lar ve migrasyonlar
core/eventbus        Uygulama içi olay veriyolu
core/security        Keystore yardımcıları
engine/signatures    İmza altyapısı
feature/scan         Tarama motoru ve dedektörler
feature/monitor      İndirme/uygulama izleme
feature/quarantine   Şifreli karantina
feature/alerts       Bildirimler ve aksiyonlar
feature/home         Ana ekran verileri
feature/onboarding   Gerçek izin ve cihaz yöneticisi akışı
feature/settings     Ayar ve önbellek işlemleri
```

## Sürüm matrisi

| Bileşen | Sürüm |
|---|---|
| Gradle | 8.9 |
| Android Gradle Plugin | 8.7.2 |
| Kotlin | 2.0.21 |
| KSP | 2.0.21-1.0.25 |
| Hilt | 2.52 |
| Room | 2.6.1 |
| JDK | 17 |
| compileSdk / targetSdk | 35 |
| minSdk | 29 |

## Derleme

```bash
export ANDROID_HOME="$HOME/android-sdk"
chmod +x gradlew
./gradlew :app:assembleDebug --no-daemon
```

Çıktı: `app/build/outputs/apk/debug/app-debug.apk`.

İmzalı, R8 ile küçültülmüş sürüm için:

```bash
./gradlew :app:assembleRelease --no-daemon
```

Çıktı: `app/build/outputs/apk/release/app-release.apk`.

Release imzası kaynak kodda tutulmaz. `local.properties` (git dışı) veya ortam değişkeni olarak
`NEXGUARD_STORE_FILE`, `NEXGUARD_STORE_PASSWORD`, `NEXGUARD_KEY_ALIAS`, `NEXGUARD_KEY_PASSWORD`
tanımlanmalıdır; eksikse `assembleRelease` açık bir hatayla durur. Debug derlemesi AGP'nin otomatik
debug anahtarıyla imzalanır ve release anahtarını kullanmaz. Ayrıntı: `keystore/README.md`.

Termux/FUSE ortamında `./gradlew` çalışmazsa:

```bash
sh gradlew :app:assembleDebug --no-daemon
```

`local.properties` içinde `sdk.dir` doğru olmalıdır. AAPT2 özel bir yoldan kullanılacaksa
`android.aapt2FromMavenOverride` ayarlanabilir.

## Test

```bash
./gradlew :core:common:testDebugUnitTest --no-daemon
```

Risk puanlama ve URL analizi birim testleri `core/common/src/test` altındadır. GitHub Actions
`.github/workflows/build-debug.yml` her push'ta testleri ve debug APK derlemesini çalıştırır;
`NexGuard-debug-apk` artifact'ı üretilir.

## Bilinen sınırlar

- Sezgisel motor imza tabanlı ürünlerin yerini tutmaz; yeni/zero-day zararlılar için garanti vermez.
- Android 14+ arka plan/ön plan servis kısıtları cihaz üreticisine göre değişebilir. Pil optimizasyonu
  istisnası izlemenin sürekliliğini artırır.
- Bulut doğrulama deneyseldir; MalwareBazaar/VirusTotal gibi topluluk API'leri erişim anahtarı
  gerektirebilir. Anahtar olmadan özellik sessizce devre dışı kalır ve yerel tarama sonucu korunur.
- Wi-Fi tarama sonuçları Android sürümüne ve konum servisi durumuna göre sınırlanabilir.
- `Android/data` ve `Android/obb` dizinleri erişim kısıtları nedeniyle atlanır.
- Gizli dizinler ve gizli dosyalar (`.gs_fs0`, `.thumbnails`, `.trash` gibi uygulama önbellekleri)
  taranmaz. Buradaki `.jpg` dosyaları gerçek görsel olmadığı için yanlış alarm kaynağıydı.
- Tam taramada kurulu uygulamaların yalnızca kullanıcı tarafından kurulanları incelenir; sistem
  uygulamaları ve NexGuard'ın kendi paketi taranmaz (değiştirilemezler, güçlü izinleri yanlış alarm üretir).
- Görsel sonuna eklenmiş yük, dosyanın son 16 KB'ı içinde aranır; daha büyük polyglot dosyalarda
  ek yükün başlangıcı bu pencerenin dışında kalabilir.

## Lisans

Bu proje kişisel kullanım için geliştirilmiştir. Üçüncü taraf bileşenlerin lisansları geçerlidir.
