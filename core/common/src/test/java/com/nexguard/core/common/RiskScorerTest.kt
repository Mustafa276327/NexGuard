package com.nexguard.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskScorerTest {

    private fun finding(
        detector: String,
        rule: String,
        severity: Severity,
        weight: Int,
        evidence: Map<String, String> = emptyMap(),
        warningOnly: Boolean = false
    ) = Finding(
        detector = detector,
        ruleId = rule,
        severity = severity,
        weight = weight,
        evidence = evidence,
        warningOnly = warningOnly
    )

    @Test
    fun `clean when there are no findings`() {
        assertEquals(Verdict.CLEAN, RiskScorer.verdict(0, emptyList()))
    }

    @Test
    fun `single low signal stays clean`() {
        val f = listOf(finding("prefilter", "FAKE_NAME", Severity.LOW, 15))
        assertEquals(Verdict.CLEAN, RiskScorer.verdict(RiskScorer.score(f), f))
    }

    @Test
    fun `single medium signal is suspicious`() {
        val f = listOf(finding("entropy", "HIGH_ENTROPY", Severity.MEDIUM, 35))
        assertEquals(Verdict.SUSPICIOUS, RiskScorer.verdict(RiskScorer.score(f), f))
    }

    @Test
    fun `two independent critical signals are dangerous`() {
        val f = listOf(
            finding("document", "EXECUTABLE_DISGUISED", Severity.CRITICAL, 80),
            finding("script", "SCRIPT_DOWNLOAD_EXEC", Severity.CRITICAL, 70)
        )
        assertEquals(Verdict.DANGEROUS, RiskScorer.verdict(RiskScorer.score(f), f))
    }

    @Test
    fun `definitive signature alone is dangerous`() {
        val f = listOf(finding("hash", "KNOWN_BAD_HASH", Severity.CRITICAL, 95))
        assertEquals(Verdict.DANGEROUS, RiskScorer.verdict(RiskScorer.score(f), f))
    }

    @Test
    fun `eicar hash and text produce dangerous verdict`() {
        val f = listOf(
            finding("hash", "KNOWN_BAD_HASH", Severity.CRITICAL, 95),
            finding("ioc", "EICAR_TEST", Severity.CRITICAL, 90)
        )
        val score = RiskScorer.score(f)
        assertTrue(score >= 90)
        assertEquals(Verdict.DANGEROUS, RiskScorer.verdict(score, f))
    }

    @Test
    fun `many weak findings from one detector do not inflate score`() {
        val many = (1..8).map { finding("ioc", "SUSPICIOUS_COMMAND", Severity.MEDIUM, 20) }
        assertEquals(20 + (20 * 7) / 2, RiskScorer.score(many))
    }

    @Test
    fun `score is capped at 100`() {
        val many = listOf(
            finding("a", "X1", Severity.CRITICAL, 90),
            finding("b", "X2", Severity.CRITICAL, 90),
            finding("c", "X3", Severity.CRITICAL, 90)
        )
        assertEquals(100, RiskScorer.score(many))
    }

    @Test
    fun `warning only findings do not affect score or verdict`() {
        val warnings = listOf(
            finding("python", "PY_SCRIPT", Severity.INFO, 0, warningOnly = true),
            finding("python", "PY_NETWORK", Severity.LOW, 16, warningOnly = true),
            finding("python", "PY_REVERSE_SHELL", Severity.LOW, 24, warningOnly = true)
        )
        assertEquals(0, RiskScorer.score(warnings))
        assertEquals(Verdict.CLEAN, RiskScorer.verdict(RiskScorer.score(warnings), warnings))
    }

    @Test
    fun `warning findings do not hide a definite malware signature`() {
        val findings = listOf(
            finding("python", "PY_SCRIPT", Severity.INFO, 0, warningOnly = true),
            finding("hash", "KNOWN_BAD_HASH", Severity.CRITICAL, 95)
        )
        val score = RiskScorer.score(findings)
        assertTrue("Kesin imza skoru korunmalı: $score", score >= 90)
        assertEquals(Verdict.DANGEROUS, RiskScorer.verdict(score, findings))
    }

    @Test
    fun `clean result with findings is a warning`() {
        val warning = Finding(
            detector = "python", ruleId = "PY_SCRIPT", severity = Severity.INFO, weight = 0,
            warningOnly = true
        )
        val result = ScanResult(
            targetPath = "/storage/emulated/0/Download/arac.py",
            sha256 = "abc",
            verdict = Verdict.CLEAN,
            score = 0,
            startedAt = 0,
            durationMs = 10,
            findings = listOf(warning),
            status = ScanStatus.COMPLETED
        )
        assertTrue(result.isCleanFile)
        assertTrue(result.isWarning)
        assertFalse(result.isThreat)
    }

    @Test
    fun `clean result without findings is not a warning`() {
        val result = ScanResult(
            targetPath = "/storage/emulated/0/Download/foto.jpg",
            sha256 = "abc",
            verdict = Verdict.CLEAN,
            score = 0,
            startedAt = 0,
            durationMs = 10,
            status = ScanStatus.COMPLETED
        )
        assertFalse(result.isWarning)
    }

    @Test
    fun `threat catalog maps eicar to malware family`() {
        val f = listOf(finding("hash", "KNOWN_BAD_HASH", Severity.CRITICAL, 95))
        assertEquals("Zararlı Yazılım", ThreatCatalog.threatType(f))
    }

    @Test
    fun `apk family evidence wins`() {
        val f = listOf(
            finding(
                "apk", "DANGEROUS_PERM_COMBO", Severity.HIGH, 40,
                evidence = mapOf("family" to "SMS Hırsızı")
            )
        )
        assertEquals("SMS Hırsızı", ThreatCatalog.threatType(f))
    }
}
