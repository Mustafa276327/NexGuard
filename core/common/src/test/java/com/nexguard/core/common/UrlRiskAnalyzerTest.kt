package com.nexguard.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlRiskAnalyzerTest {

    @Test
    fun `normal https site is clean`() {
        val a = UrlRiskAnalyzer.analyze("https://www.example.com/urun/123")
        assertTrue(a.valid)
        assertEquals(Verdict.CLEAN, a.verdict)
        assertEquals("www.example.com", a.host)
    }

    @Test
    fun `scheme is added when missing`() {
        val a = UrlRiskAnalyzer.analyze("example.com")
        assertEquals("https", a.scheme)
        assertEquals(Verdict.CLEAN, a.verdict)
    }

    @Test
    fun `plain http is suspicious`() {
        val a = UrlRiskAnalyzer.analyze("http://example.com")
        assertEquals(Verdict.SUSPICIOUS, a.verdict)
        assertTrue(a.reasons.any { it.contains("HTTP") })
    }

    @Test
    fun `http with ip and login path is dangerous`() {
        val a = UrlRiskAnalyzer.analyze("http://192.168.1.1/login")
        assertEquals(Verdict.DANGEROUS, a.verdict)
        assertTrue(a.score >= 60)
    }

    @Test
    fun `punycode with login is at least suspicious`() {
        val a = UrlRiskAnalyzer.analyze("https://xn--pypal-4ve.com/login/verify")
        assertTrue(a.verdict != Verdict.CLEAN)
        assertTrue(a.reasons.any { it.contains("Punycode") })
    }

    @Test
    fun `shortener is suspicious`() {
        val a = UrlRiskAnalyzer.analyze("https://bit.ly/abc123")
        assertEquals(Verdict.SUSPICIOUS, a.verdict)
        assertTrue(a.reasons.any { it.contains("Kısaltılmış") })
    }

    @Test
    fun `blocked domain is always dangerous`() {
        val a = UrlRiskAnalyzer.analyze("https://sub.example.com/x", blockedDomains = setOf("example.com"))
        assertEquals(Verdict.DANGEROUS, a.verdict)
        assertEquals(100, a.score)
    }

    @Test
    fun `javascript scheme is dangerous`() {
        val a = UrlRiskAnalyzer.analyze("javascript:alert(1)")
        assertEquals(Verdict.DANGEROUS, a.verdict)
        assertFalse(a.valid.not())
    }

    @Test
    fun `at sign redirect is suspicious`() {
        val a = UrlRiskAnalyzer.analyze("https://paypal.com@evil.example/login")
        assertTrue(a.score >= 25)
        assertTrue(a.reasons.any { it.contains("@") })
    }

    @Test
    fun `apk download link gets extra risk`() {
        val a = UrlRiskAnalyzer.analyze("https://downloads.example.com/app.apk")
        assertTrue(a.verdict != Verdict.CLEAN)
        assertTrue(a.reasons.any { it.contains("çalıştırılabilir") })
    }
}
