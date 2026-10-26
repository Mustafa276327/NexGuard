package com.nexguard.feature.scan.detection

import com.nexguard.core.common.Finding
import com.nexguard.core.common.Severity

/**
 * Python geliştirici betikleri için açıklanabilir davranış analizi.
 *
 * Amaç: kullanıcının kendi .py dosyalarını yanlışlıkla "tehdit" göstermek yerine
 * betiğin ne yaptığını anlatan uyarılar üretmek. Buradaki tüm bulgular
 * [Finding.warningOnly] işaretlidir; risk skorunu ve tehdit hükmünü etkilemez.
 * Kesin kötü amaçlı imzalar (EICAR, bilinen kötü hash) diğer dedektörlerde kalır
 * ve yine tehdit olarak raporlanır.
 */
class PythonAnalyzer : Detector {
    override val name = "python"
    override val priority = 66

    private val importRegex = Regex(
        """(?m)^\s*(?:from\s+([A-Za-z_][\w.]*)\s+import|import\s+([A-Za-z_][\w.]*(?:\s*,\s*[A-Za-z_][\w.]*)*))"""
    )
    private val longBase64 = Regex("""[A-Za-z0-9+/]{180,}={0,2}""")

    private val networkModules = setOf(
        "requests", "urllib", "urllib2", "urllib3", "httpx", "aiohttp", "socket",
        "ftplib", "paramiko", "telnetlib", "smtplib", "imaplib", "poplib",
        "websocket", "websockets", "pycurl", "http", "xmlrpc"
    )
    private val networkCalls = listOf(
        "requests.get(", "requests.post(", "requests.put(", "requests.request(",
        "urlopen(", "httpx.", "aiohttp.", "socket.socket(", ".connect((",
        "ftplib.ftp(", "paramiko.", "smtplib.", "telnetlib.", "websocket."
    )

    private val execPatterns = listOf(
        "os.system(", "os.popen(", "os.exec", "os.spawn", "subprocess.",
        "pty.spawn(", "ctypes.", "marshal.loads", "pickle.loads",
        "commands.getoutput(", "platform.popen("
    )
    private val dynamicCodeRegex = Regex("""\b(?:exec|eval|compile)\s*\(""")

    private val reverseShellTokens = listOf(
        "dup2(", "pty.spawn", "/bin/sh", "/bin/bash", "cmd.exe",
        "nc -e", "ncat -e", "/dev/tcp/"
    )
    private val credentialTokens = listOf(
        "getpass(", "keyring", "browser_cookie3", "cookies.sqlite", "login data",
        "cryptunprotectdata", "pynput", "keyboard.on_press", "keyboard.hook(",
        "credential", "password manager"
    )
    private val persistenceTokens = listOf(
        "crontab", "/etc/cron", "systemd", ".bashrc", "launchd", "rc.local",
        "winreg", "hkey_current_user", "hkey_local_machine", "startup",
        "schtasks", "registry"
    )
    private val destructiveTokens = listOf(
        "shutil.rmtree(", "os.remove(", "os.unlink(", "fernet(", "cryptography.fernet",
        "ransom", "encrypt_file", "encrypt_files"
    )

    override suspend fun inspect(target: ScanTarget): List<Finding> {
        if (target.extension !in ScanPolicy.pythonExtensions) return emptyList()

        val head = target.file.readHead(4096)
        val bytes = target.sample()
        val text = String(bytes, Charsets.ISO_8859_1)
        val imports = extractImports(text)
        val lower = text.lowercase()
        val out = mutableListOf<Finding>()

        // Her Python dosyası en az bir bilgilendirme uyarısı alır: böylece
        // kullanıcı dosyayı "tehdit" değil, açıklamalı "uyarı" olarak görür.
        val baseEvidence = mutableMapOf(
            "dil" to "Python",
            "satır" to text.lineSequence().count().toString()
        )
        if (imports.isNotEmpty()) {
            baseEvidence["modüller"] = imports.take(10).joinToString(", ")
        }
        purposeOf(imports)?.let { baseEvidence["özet"] = it }
        if (!FileTypeSniffer.looksLikeText(head)) {
            baseEvidence["içerik"] = "Metin değil (${FileTypeSniffer.sniff(head).name.lowercase()})"
        }
        out += warning(
            ruleId = "PY_SCRIPT",
            weight = 0,
            evidence = baseEvidence
        )

        if (!FileTypeSniffer.looksLikeText(head)) return out

        val networkHits = networkCalls.filter { lower.contains(it) } +
            imports.filter { baseModule(it) in networkModules }.map { "import $it" }
        if (networkHits.isNotEmpty()) {
            out += warning(
                ruleId = "PY_NETWORK",
                weight = 16,
                evidence = evidence(
                    "modüller" to networkHits.take(6).joinToString(", "),
                    "açıklama" to "Ağa veri gönderebilir veya dışarıdan veri alabilir"
                )
            )
        }

        val execHits = execPatterns.filter { lower.contains(it) } +
            if (dynamicCodeRegex.containsMatchIn(text)) listOf("exec/eval/compile") else emptyList()
        if (execHits.isNotEmpty()) {
            out += warning(
                ruleId = "PY_EXEC",
                weight = 18,
                evidence = evidence(
                    "kalıplar" to execHits.take(6).joinToString(", "),
                    "açıklama" to "Sistem komutu veya dinamik kod çalıştırabilir"
                )
            )
        }

        val downloadLike = listOf("urlopen(", "requests.get(", "requests.post(", "httpx.", "aiohttp.", "urllib")
            .any { lower.contains(it) }
        if (downloadLike && execHits.isNotEmpty()) {
            out += warning(
                ruleId = "PY_DOWNLOAD_EXEC",
                weight = 22,
                evidence = evidence(
                    "açıklama" to "Ağdan içerik indirip çalıştırma kalıbı birlikte görülüyor"
                )
            )
        }

        val obfuscationHits = listOf(
            "base64.b64decode", "base64.decodebytes", "marshal.loads", "zlib.decompress",
            "codecs.decode", "exec(", "eval("
        ).filter { lower.contains(it) }
        if (obfuscationHits.size >= 2 && (longBase64.containsMatchIn(text) || obfuscationHits.size >= 3)) {
            out += warning(
                ruleId = "PY_OBFUSCATION",
                weight = 20,
                evidence = evidence(
                    "kalıplar" to obfuscationHits.take(5).joinToString(", "),
                    "base64" to longBase64.containsMatchIn(text).toString(),
                    "açıklama" to "Kod gizlenmiş veya çalışma anında çözülüyor olabilir"
                )
            )
        }

        val hasSocket = lower.contains("socket.socket(") || imports.any { baseModule(it) == "socket" }
        val shellHits = reverseShellTokens.filter { lower.contains(it) }
        if (hasSocket && shellHits.isNotEmpty()) {
            out += warning(
                ruleId = "PY_REVERSE_SHELL",
                weight = 24,
                evidence = evidence(
                    "kalıplar" to shellHits.take(5).joinToString(", "),
                    "açıklama" to "Uzaktan kabuk bağlantısı kurma kalıbı; güvenilmeyen kaynaktan çalıştırmayın"
                )
            )
        }

        val credentialHits = credentialTokens.filter { lower.contains(it) }
        if (credentialHits.isNotEmpty()) {
            out += warning(
                ruleId = "PY_CREDENTIAL",
                weight = 20,
                evidence = evidence(
                    "kalıplar" to credentialHits.take(5).joinToString(", "),
                    "açıklama" to "Parola, çerez veya tuş kaydı erişimi olabilir"
                )
            )
        }

        val persistenceHits = persistenceTokens.filter { lower.contains(it) }
        if (persistenceHits.isNotEmpty()) {
            out += warning(
                ruleId = "PY_PERSISTENCE",
                weight = 18,
                evidence = evidence(
                    "kalıplar" to persistenceHits.take(5).joinToString(", "),
                    "açıklama" to "Betik kendini otomatik başlatacak şekilde kurabilir"
                )
            )
        }

        val destructiveHits = destructiveTokens.filter { lower.contains(it) }
        if (destructiveHits.isNotEmpty()) {
            out += warning(
                ruleId = "PY_DESTRUCTIVE",
                weight = 20,
                evidence = evidence(
                    "kalıplar" to destructiveHits.take(5).joinToString(", "),
                    "açıklama" to "Dosya silebilir veya şifreleyebilir"
                )
            )
        }

        return out
    }

    private fun warning(ruleId: String, weight: Int, evidence: Map<String, String>): Finding =
        Finding(
            detector = name,
            ruleId = ruleId,
            severity = if (weight == 0) Severity.INFO else Severity.LOW,
            weight = weight,
            evidence = evidence,
            warningOnly = true
        )

    private fun evidence(vararg pairs: Pair<String, String>): Map<String, String> =
        pairs.filter { it.second.isNotBlank() }.toMap()

    private fun extractImports(text: String): List<String> {
        return importRegex.findAll(text).flatMap { match ->
            val from = match.groupValues[1]
            val direct = match.groupValues[2]
            if (from.isNotBlank()) {
                sequenceOf(from)
            } else {
                direct.split(',').asSequence().map { it.trim().substringBefore(' ') }
            }
        }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(40)
            .toList()
    }

    private fun baseModule(name: String): String = name.substringBefore('.')

    private fun purposeOf(imports: List<String>): String? {
        val bases = imports.map { baseModule(it) }.toSet()
        return when {
            bases.any { it in setOf("flask", "django", "fastapi", "streamlit", "gradio") } ->
                "Web/API uygulaması"
            bases.any { it in setOf("pandas", "numpy", "matplotlib", "seaborn", "scipy") } ->
                "Veri analizi"
            bases.any { it in setOf("telegram", "discord", "slack_sdk", "twilio") } ->
                "Bot/entegrasyon"
            bases.any { it in setOf("tkinter", "PyQt5", "PyQt6", "PySide2", "PySide6", "kivy") } ->
                "Masaüstü arayüzü"
            bases.any { it in setOf("requests", "httpx", "aiohttp", "urllib", "socket", "ftplib", "smtplib") } ->
                "Ağ istemcisi"
            bases.any { it in setOf("cv2", "PIL", "imageio", "moviepy") } ->
                "Görüntü/video işleme"
            bases.any { it in setOf("sklearn", "tensorflow", "torch", "keras") } ->
                "Makine öğrenmesi"
            bases.any { it in setOf("subprocess", "os", "shutil") } ->
                "Sistem otomasyonu"
            else -> null
        }
    }
}
