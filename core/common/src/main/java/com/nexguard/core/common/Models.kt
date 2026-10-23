package com.nexguard.core.common

import java.util.UUID

enum class Verdict { CLEAN, SUSPICIOUS, DANGEROUS }

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
    val findings: List<Finding> = emptyList()
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