package com.nexguard.core.common

import java.util.UUID

enum class Verdict { CLEAN, SUSPICIOUS, DANGEROUS }

/**
 * Bir dosya taramasının teknik durumu. [Verdict] yalnızca tehdit hükmünü anlatır;
 * dosya okunamadığında, hash hesaplanamadığında veya bir dedektör çöktüğünde
 * sonuç "temiz" sayılmaz. Karar verirken [ScanResult.isCleanFile] kullanılmalıdır.
 */
enum class ScanStatus {
    /** Tüm aşamalar tamamlandı; [ScanResult.verdict] güvenilir. */
    COMPLETED,

    /** Tarama yapıldı ama kısmi: dedektör hatası veya dosya değişimi. */
    PARTIAL,

    /** Dosya bulunamadı veya yol artık bir dosyayı göstermiyor. */
    NOT_FOUND,

    /** Dosya var ama okunamadı (izin yok / erişim engellendi). */
    UNREADABLE,

    /** Beklenmeyen hata; güvenilir sonuç üretilemedi. */
    ERROR;

    val isConclusive: Boolean get() = this == COMPLETED
    val isIssue: Boolean get() = this != COMPLETED
}

enum class Severity { INFO, LOW, MEDIUM, HIGH, CRITICAL }

data class Finding(
    val id: String = UUID.randomUUID().toString(),
    val detector: String,
    val ruleId: String,
    val severity: Severity,
    val weight: Int,
    val evidence: Map<String, String> = emptyMap()
)

data class ScanResult(
    val id: String = UUID.randomUUID().toString(),
    val targetPath: String,
    val sha256: String,
    val verdict: Verdict,
    val score: Int,
    val startedAt: Long,
    val durationMs: Long,
    val findings: List<Finding> = emptyList(),
    val sizeBytes: Long = 0,
    /** Sonuç, değişmemiş dosya önbelleğinden geldiyse true. */
    val cached: Boolean = false,
    /** manual | download | install | app */
    val source: String = "manual",
    /** Teknik tarama durumu. [Verdict] tek başına yeterli değildir. */
    val status: ScanStatus = ScanStatus.COMPLETED,
    /** Teknik durum açıklaması; kullanıcıya gösterilebilir. */
    val error: String? = null,
    /** Çalışamayan yerel dedektörlerin adları. */
    val detectorErrors: List<String> = emptyList()
) {
    /**
     * Yalnızca tamamlanmış (kısmi olmayan) ve tehdit içermeyen tarama gerçekten
     * "temiz" sayılır. `verdict == CLEAN` tek başına bu anlama gelmez.
     */
    val isCleanFile: Boolean get() = status == ScanStatus.COMPLETED && verdict == Verdict.CLEAN

    /** Tehdit hükmü var mı. Teknik hata durumları tehdit sayılmaz. */
    val isThreat: Boolean get() = verdict != Verdict.CLEAN
}

data class ScanSession(
    val id: String = UUID.randomUUID().toString(),
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val mode: String = "full",
    val label: String = "",
    val filesScanned: Int = 0,
    val threats: Int = 0,
    val cachedFiles: Int = 0,
    /** Teknik olarak tamamlanamayan dosya sayısı (okunamadı, bulunamadı, kısmi). */
    val errors: Int = 0,
    val status: String = "running",
    val score: Int = 100
)

data class ScanRun(
    val session: ScanSession,
    val results: List<ScanResult>
)

data class TimelineEvent(
    val id: String = UUID.randomUUID().toString(),
    val ts: Long = System.currentTimeMillis(),
    val source: String,
    val kind: String,
    val summary: String,
    val refId: String? = null
)

data class Result<out T>(val data: T? = null, val error: Throwable? = null) {
    val isSuccess: Boolean get() = error == null
    companion object {
        fun <T> success(data: T): Result<T> = Result(data = data)
        fun <T> failure(e: Throwable): Result<T> = Result(error = e)
    }
}
