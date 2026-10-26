# Keystore

Bu dizin NexGuard'ın imzalama anahtarını içerir. **Anahtar dosyası ve parolaları Git'e
eklenmez** (`.gitignore`: `*.jks`, `*.keystore`).

## Yerel kurulum

1. `nexguard.jks` dosyasını bu dizine koyun (mevcut kurulumlarla aynı anahtar).
2. `local.properties` içine (git dışı) şu satırları ekleyin:

```properties
NEXGUARD_STORE_FILE=keystore/nexguard.jks
NEXGUARD_STORE_PASSWORD=<parola>
NEXGUARD_KEY_ALIAS=nexguard
NEXGUARD_KEY_PASSWORD=<parola>
```

Değerler ortam değişkeni olarak da verilebilir (`NEXGUARD_STORE_PASSWORD` vb.). CI'da
GitHub Secrets kullanın.

- Değerlerden biri eksikse `assembleRelease` açık bir hatayla durur; `assembleDebug`
  bundan etkilenmez ve AGP'nin otomatik debug anahtarıyla imzalanır.
- **Asla bu dosyayı kaybetmeyin.** Anahtar kaybolursa mevcut kurulumların üzerine
  güncelleme yüklenemez (imza uyuşmazlığı).

## Not: anahtar rotasyonu

Bu anahtar bir dönem depoya işlenmişti; parolası da kaynak kodda sabitti. Depo public
olduğu için eski anahtar ele geçirilmiş sayılır. **26.10.2026'da 1.1.0 sürümüyle yeni bir
4096 bit RSA anahtarına geçildi** (alias `nexguard`).

- 1.0.0 ve öncesi kurulumlar, yeni imzayla gelen güncellemeyi doğrudan kuramaz; uygulama
  kaldırılıp yeniden kurulmalıdır (tarama geçmişi/karantina silinir).
- Eski anahtar yalnızca arşiv amacıyla yerelde `nexguard-1.0.0.jks` olarak tutulur; yeni
  sürümleri imzalamak için kullanılmaz.
- Yeni anahtarın yedeği depo dışında (`~/nexguard-signing-backup/`) tutulur. Parola
  `local.properties` içindedir; Git'e eklenmez.
