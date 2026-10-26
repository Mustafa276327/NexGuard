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

Bu anahtar bir dönem depoya işlenmişti; parolası da kaynak kodda sabitti. Depo public ise
anahtar ele geçirilmiş sayılmalıdır. Yeni bir anahtarla rotasyon yapılırsa yüklü
kullanıcılar uygulamayı kaldırıp yeniden kurmak zorunda kalır (aynı `applicationId`).
Karar kullanıcıya aittir; bu dosya yalnızca durumu belgeler.
