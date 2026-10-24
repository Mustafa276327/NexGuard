package com.nexguard.engine.signatures

/**
 * Basit, thread-safe hash imza deposu. Uygulama içindeki kalıcı imza kaynağı
 * `feature:scan` modülündeki HashDatabase'dir; bu sınıf bağımsız kullanım ve
 * testler için güncellenebilir bir bellek içi görünüm sunar.
 */
class SignatureDb(initial: Collection<String> = emptyList()) {

    @Volatile
    private var badHashes: Set<String> = normalize(initial)

    fun contains(hash: String): Boolean = badHashes.contains(hash.trim().lowercase())

    @Synchronized
    fun update(entries: Set<String>) {
        badHashes = normalize(entries)
    }

    val size: Int get() = badHashes.size

    private fun normalize(values: Collection<String>): Set<String> =
        values.asSequence()
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()
}
