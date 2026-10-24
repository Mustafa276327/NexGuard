# NexGuard

NexGuard, Android cihazlarda dosyaları, kurulu uygulamaları, indirmeleri, Wi-Fi ağlarını ve bağlantıları
inceleyen, çevrimdışı öncelikli bir güvenlik uygulamasıdır. Amaç, cihazdaki riskleri anlaşılır biçimde
göstermek ve tehditleri güvenli biçimde izole etmektir.

## Özellikler

- **Tam ve hızlı tarama:** Erişilebilir tüm depolama alanı, indirilenler, belgeler ve isteğe bağlı olarak
  kurulu APK'lar taranır. Canlı dosya akışı, filtreler, ilerleme, durdurma ve özet ekranı vardır.
- **Çok katmanlı analiz:** Bilinen hash imzaları, EICAR, APK/DEX ve izin analizi, arşiv incelemesi,
  PE başlıkları, betik komutları, belge içindeki çalıştırılabilir içerik, entropi ve IOC taraması.
- **Önbellek:** Yalnızca değişmemiş **temiz** dosyalar için kullanılır. Tehditler ve değişen dosyalar
  her zaman yeniden taranır; motor sürümü değişince önbellek geçersiz olur.
- **Gerçek zamanlı izleme:** İndirilenler ve yeni kurulan uygulamalar izlenir; tehdit bildirimi ve
  isteğe bağlı otomatik karantina uygulanır.
- **AES-256-GCM karantina:** Dosyalar Android Keystore anahtarıyla parçalı şifrelenir. Geri yüklemede
  SHA-256 bütünlük doğrulaması yapılır.
- **Tehdit ayrıntısı:** Virüs/tehdit türü, risk puanı, bulgular, öneriler, karantinaya alma ve kalıcı
  silme seçenekleri.
- **Uygulama denetimi:** Tehlikeli izinler, eski hedef SDK, debug yapısı, bilinmeyen kaynak ve APK
  derin analizi.
- **Cihaz denetimi:** Root, SELinux, ekran kilidi, şifreleme, USB hata ayıklama, yama yaşı, cihaz
  yöneticisi ve pil optimizasyonu kontrolleri.
- **Wi-Fi denetimi:** Açık/WEP ağlar ve aynı SSID'nin farklı güvenlik türleriyle yayınlanması
  (evil-twin şüphesi) işaretlenir.
- **Site kontrolü:** URL'ler çevrimdışı sezgisel kurallarla puanlanır; HTTP, IP host, punycode,
  kısaltıcı, şüpheli TLD, `@` yönlendirmesi ve kimlik avı kelimeleri tespit edilir. Engellenen alan
  adları her zaman tehlikeli kabul edilir.
- **Canlı log:** Tarama, izleme ve karantina olayları bellekte tutulur; dosya yolu ve kişisel içerik
  yazılmaz.

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

## Lisans

Bu proje kişisel kullanım için geliştirilmiştir. Üçüncü taraf bileşenlerin lisansları geçerlidir.
