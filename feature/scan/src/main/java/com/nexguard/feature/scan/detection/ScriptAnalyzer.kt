package com.nexguard.feature.scan.detection

import com.nexguard.core.common.Finding
import com.nexguard.core.common.Severity

/**
 * İndirilen betiklerde (js, vbs, ps1, bat, sh…) indir-çalıştır, gizleme ve
 * ters kabuk gibi gerçek saldırı kalıplarını arar.
 */
class ScriptAnalyzer : Detector {
    override val name = "script"
    override val priority = 65

    override suspend fun inspect(target: ScanTarget): List<Finding> {
        if (target.extension !in ScanPolicy.scriptExtensions) return emptyList()
        // Python dosyaları PythonAnalyzer tarafından açıklamalı uyarı olarak
        // işlenir; burada kritik "indir-çalıştır" hükmü üretilmez.
        if (target.extension in ScanPolicy.pythonExtensions) return emptyList()
        val text = String(target.sample(), Charsets.ISO_8859_1)
        if (text.isBlank()) return emptyList()
        val lower = text.lowercase()
        val out = mutableListOf<Finding>()

        val download = listOf(
            "curl ", "wget ", "invoke-webrequest", "downloadstring", "urldownloadtofile",
            "start-bitstransfer", "certutil -urlcache", "http.get", "urlopen("
        ).any { lower.contains(it) }
        val execute = listOf(
            "| sh", "| bash", "invoke-expression", "iex(", "start-process", "exec(",
            "wscript.shell", "run ", "shell(", "os.system", "subprocess.", "eval("
        ).any { lower.contains(it) }
        if (download && execute) {
            out += Finding(
                detector = name, ruleId = "SCRIPT_DOWNLOAD_EXEC", severity = Severity.CRITICAL, weight = 75,
                evidence = mapOf("pattern" to "download + execute")
            )
        }

        val obfuscationTokens = listOf("eval(", "atob(", "fromcharcode", "unescape(", "frombase64string")
        val obfuscationHits = obfuscationTokens.count { lower.contains(it) }
        val longBase64 = Regex("""[A-Za-z0-9+/]{120,}={0,2}""").containsMatchIn(text)
        if (obfuscationHits >= 2 || (obfuscationHits >= 1 && longBase64)) {
            out += Finding(
                detector = name, ruleId = "SCRIPT_OBFUSCATION", severity = Severity.MEDIUM, weight = 40,
                evidence = mapOf("tokens" to obfuscationHits.toString(), "base64" to longBase64.toString())
            )
        }

        if (Regex("""(-enc(odedcommand)?\s+[A-Za-z0-9+/=]{40,})""", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            out += Finding(
                detector = name, ruleId = "SCRIPT_ENCODED_COMMAND", severity = Severity.HIGH, weight = 55,
                evidence = mapOf("pattern" to "encoded command")
            )
        }

        val reverseShell = listOf("/dev/tcp/", "nc -e", "ncat -e", "socat ", "bash -i >&", "mkfifo ")
        val revHits = reverseShell.filter { lower.contains(it) }
        if (revHits.size >= 2 || lower.contains("/dev/tcp/") || lower.contains("bash -i >&")) {
            out += Finding(
                detector = name, ruleId = "SCRIPT_REVERSE_SHELL", severity = Severity.CRITICAL, weight = 85,
                evidence = mapOf("tokens" to revHits.joinToString(", "))
            )
        }
        return out
    }
}
