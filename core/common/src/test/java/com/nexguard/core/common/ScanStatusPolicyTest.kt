package com.nexguard.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Uygulama kabul matrisi (master prompt §22) temel alınmıştır:
 * eksik/okunamayan dosya veya çöken dedektör hiçbir zaman sahte "CLEAN" üretmez.
 */
class ScanStatusPolicyTest {

    @Test
    fun `healthy scan completes with no message`() {
        val outcome = ScanStatusPolicy.evaluate(ScanHealth())
        assertEquals(ScanStatus.COMPLETED, outcome.status)
        assertNull(outcome.message)
        assertTrue(outcome.status.isConclusive)
    }

    @Test
    fun `missing file is NOT_FOUND and not conclusive`() {
        val outcome = ScanStatusPolicy.evaluate(ScanHealth(exists = false))
        assertEquals(ScanStatus.NOT_FOUND, outcome.status)
        assertEquals(ScanStatusPolicy.MESSAGE_NOT_FOUND, outcome.message)
        assertFalse(outcome.status.isConclusive)
    }

    @Test
    fun `path that is not a file is NOT_FOUND`() {
        val outcome = ScanStatusPolicy.evaluate(ScanHealth(exists = true, isFile = false))
        assertEquals(ScanStatus.NOT_FOUND, outcome.status)
        assertEquals(ScanStatusPolicy.MESSAGE_NOT_A_FILE, outcome.message)
    }

    @Test
    fun `permission denied is UNREADABLE`() {
        val outcome = ScanStatusPolicy.evaluate(ScanHealth(readable = false))
        assertEquals(ScanStatus.UNREADABLE, outcome.status)
        assertEquals(ScanStatusPolicy.MESSAGE_UNREADABLE, outcome.message)
    }

    @Test
    fun `hash failure is ERROR`() {
        val outcome = ScanStatusPolicy.evaluate(ScanHealth(hashComputed = false))
        assertEquals(ScanStatus.ERROR, outcome.status)
        assertEquals(ScanStatusPolicy.MESSAGE_HASH_FAILED, outcome.message)
    }

    @Test
    fun `detector exception downgrades to PARTIAL`() {
        val outcome = ScanStatusPolicy.evaluate(
            ScanHealth(failedDetectors = listOf("ApkAnalyzer", "ArchiveScanner"))
        )
        assertEquals(ScanStatus.PARTIAL, outcome.status)
        assertTrue(outcome.message!!.contains("2 dedektör"))
        assertTrue(outcome.message!!.contains("ApkAnalyzer"))
    }

    @Test
    fun `changing file is PARTIAL and wins over detector errors`() {
        val outcome = ScanStatusPolicy.evaluate(
            ScanHealth(failedDetectors = listOf("EntropyDetector"), changedDuringScan = true)
        )
        assertEquals(ScanStatus.PARTIAL, outcome.status)
        assertEquals(ScanStatusPolicy.MESSAGE_CHANGED, outcome.message)
    }

    @Test
    fun `access problems take precedence over partial signals`() {
        val outcome = ScanStatusPolicy.evaluate(
            ScanHealth(
                exists = false,
                readable = false,
                hashComputed = false,
                failedDetectors = listOf("X"),
                changedDuringScan = true
            )
        )
        assertEquals(ScanStatus.NOT_FOUND, outcome.status)
    }

    @Test
    fun `blank detector names are ignored`() {
        val outcome = ScanStatusPolicy.evaluate(ScanHealth(failedDetectors = listOf(" ", "")))
        assertEquals(ScanStatus.COMPLETED, outcome.status)
    }

    @Test
    fun `scan result is only clean when completed`() {
        val base = ScanResult(
            targetPath = "/sdcard/a.txt",
            sha256 = "abc",
            verdict = Verdict.CLEAN,
            score = 0,
            startedAt = 0,
            durationMs = 1
        )
        assertTrue(base.isCleanFile)
        assertFalse(base.isThreat)

        assertFalse(base.copy(status = ScanStatus.PARTIAL).isCleanFile)
        assertFalse(base.copy(status = ScanStatus.UNREADABLE).isCleanFile)
        assertFalse(base.copy(status = ScanStatus.NOT_FOUND).isCleanFile)
        assertFalse(base.copy(status = ScanStatus.ERROR).isCleanFile)
    }

    @Test
    fun `technical failure is not counted as threat`() {
        val failed = ScanResult(
            targetPath = "/sdcard/secret",
            sha256 = "",
            verdict = Verdict.CLEAN,
            score = 0,
            startedAt = 0,
            durationMs = 1,
            status = ScanStatus.UNREADABLE,
            error = ScanStatusPolicy.MESSAGE_UNREADABLE
        )
        assertFalse(failed.isThreat)
        assertFalse(failed.isCleanFile)
        assertNotNull(failed.error)
    }

    @Test
    fun `threat verdict stays threat regardless of status`() {
        val threat = ScanResult(
            targetPath = "/sdcard/eicar.com",
            sha256 = "275a021bbfb6489e54d471899f7db9d1663fc695ec2fe2a2c4538aabf651fd0f",
            verdict = Verdict.DANGEROUS,
            score = 90,
            startedAt = 0,
            durationMs = 1,
            status = ScanStatus.PARTIAL
        )
        assertTrue(threat.isThreat)
        assertFalse(threat.isCleanFile)
    }
}
