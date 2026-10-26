package com.nexguard.core.common

/**
 * Bir dosya taramasının teknik sağlığını anlatan ham gözlemler.
 * [ScanStatusPolicy] bunları tek ve açıklanabilir bir [ScanStatus] sonucuna çevirir.
 */
data class ScanHealth(
    val exists: Boolean = true,
    val isFile: Boolean = true,
    val readable: Boolean = true,
    /** İçerik okunup SHA-256 hesaplanabildi mi. */
    val hashComputed: Boolean = true,
    /** Çalışırken istisna veren yerel dedektörler. */
    val failedDetectors: List<String> = emptyList(),
    /** Dosya boyutu/değişim zamanı tarama sırasında değişti mi. */
    val changedDuringScan: Boolean = false
)

data class ScanStatusOutcome(
    val status: ScanStatus,
    /** Kullanıcıya gösterilecek kısa açıklama; [ScanStatus.COMPLETED] ise null. */
    val message: String? = null
)

/**
 * Tarama durumlarının tek doğruluk kaynağı. Saf Kotlin'dir; Android'e bağlı
 * olmadığı için birim testleriyle doğrulanır.
 *
 * Kural: dosya erişilemiyor ya da içeriği okunamıyorsa sonuç asla "temiz"
 * olarak sunulmaz. Kısmi tarama (bir dedektör çöktü, dosya değişti) da kesin
 * hüküm sayılmaz.
 */
object ScanStatusPolicy {

    const val MESSAGE_NOT_FOUND = "Dosya bulunamadı veya taşınmış"
    const val MESSAGE_NOT_A_FILE = "Yol bir dosyayı göstermiyor"
    const val MESSAGE_UNREADABLE = "Dosya okunamadı (izin yok veya erişim engellendi)"
    const val MESSAGE_HASH_FAILED = "Dosya içeriği okunamadı; bütünlük doğrulanamadı"
    const val MESSAGE_CHANGED = "Dosya tarama sırasında değişti; sonuç kesin değil"

    fun evaluate(health: ScanHealth): ScanStatusOutcome {
        // Erişim sorunları en belirgin sinyaldir: hüküm üretilmedi.
        if (!health.exists) return ScanStatusOutcome(ScanStatus.NOT_FOUND, MESSAGE_NOT_FOUND)
        if (!health.isFile) return ScanStatusOutcome(ScanStatus.NOT_FOUND, MESSAGE_NOT_A_FILE)
        if (!health.readable) return ScanStatusOutcome(ScanStatus.UNREADABLE, MESSAGE_UNREADABLE)
        if (!health.hashComputed) return ScanStatusOutcome(ScanStatus.ERROR, MESSAGE_HASH_FAILED)

        // Değişen dosya, dedektör hatasından daha güçlü bir "kesin değil" sinyalidir.
        if (health.changedDuringScan) {
            return ScanStatusOutcome(ScanStatus.PARTIAL, MESSAGE_CHANGED)
        }

        val failed = health.failedDetectors.distinct().filter { it.isNotBlank() }
        if (failed.isNotEmpty()) {
            val listed = failed.take(3).joinToString(", ")
            val suffix = if (failed.size > 3) "…" else ""
            return ScanStatusOutcome(
                ScanStatus.PARTIAL,
                "${failed.size} dedektör çalışamadı: $listed$suffix"
            )
        }
        return ScanStatusOutcome(ScanStatus.COMPLETED)
    }
}
