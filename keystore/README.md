# Keystore

Bu dosya NexGuard'ın imzalama anahtarını içerir (kişisel kullanım, Play Store dışı dağıtım).

- `nexguard.jks` — imza keystore'u
- Şifreler `app/build.gradle.kts` içinde hardcoded olarak görünüyor; **üretimde bu değerleri CI Secrets veya ortam değişkeninden almalı, koda gömmemelidir.**

**Asla bu dosyayı kaybetmeyin.** Anahtar kaybolursa mevcut kurulumların üzerine güncelleme yüklenemez (imza uyuşmazlığı).