package com.nexguard.core.common

/**
 * Açıklanabilir risk puanlama.
 *
 * - Aynı dedektörün birden fazla bulgusu tek sinyal sayılır: en yüksek ağırlık
 *   tam, kalanların yarısı eklenir. Böylece tek bir dedektörün çok sayıda zayıf
 *   bulgu üretmesi skoru şişirmez.
 * - "Tehlikeli" için ya kesin imza (hash/EICAR/bulut) ya da en az iki bağımsız
 *   dedektörden kritik sinyal + 70 puan gerekir.
 * - [Finding.warningOnly] bulgular skora ve hükme katılmaz. Böylece geliştirici
 *   dosyaları (ör. Python betikleri) "tehdit" olmadan açıklamalı uyarı olarak
 *   gösterilebilir; kesin imzalar (EICAR, kötü hash) yine tehdit sayılır.
 */
object RiskScorer {

    private val definitiveRules = setOf(
        "KNOWN_BAD_HASH",
        "EICAR_TEST",
        "MALWAREBAZAAR_HIT",
        "VIRUSTOTAL_HIT",
        "EXECUTABLE_DISGUISED"
    )

    fun score(findings: List<Finding>): Int {
        val actionable = findings.filterNot { it.warningOnly }
        if (actionable.isEmpty()) return 0
        val byDetector = actionable.groupBy { it.detector }
        var total = 0
        byDetector.forEach { (_, fs) ->
            val sorted = fs.map { it.weight }.sortedDescending()
            total += (sorted.firstOrNull() ?: 0) + sorted.drop(1).sum() / 2
        }
        return total.coerceIn(0, 100)
    }

    fun verdict(score: Int, findings: List<Finding>): Verdict {
        val actionable = findings.filterNot { it.warningOnly }
        if (actionable.isEmpty()) return Verdict.CLEAN
        val distinctDetectors = actionable.map { it.detector }.distinct().size
        val hasCritical = actionable.any { it.severity == Severity.CRITICAL }
        val hasDefinitive = actionable.any { it.ruleId in definitiveRules }
        return when {
            hasDefinitive && score >= 60 -> Verdict.DANGEROUS
            score >= 70 && hasCritical && distinctDetectors >= 2 -> Verdict.DANGEROUS
            score >= 30 -> Verdict.SUSPICIOUS
            else -> Verdict.CLEAN
        }
    }

    fun evaluate(findings: List<Finding>): Pair<Int, Verdict> {
        val score = score(findings)
        return score to verdict(score, findings)
    }
}
