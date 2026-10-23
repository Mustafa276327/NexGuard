package com.nexguard.engine.signatures

class SignatureDb {
    private val badHashes = setOf<String>()
    fun contains(hash: String): Boolean = badHashes.contains(hash.lowercase())
    fun update(entries: Set<String>) { /* TODO: persist */ }
}