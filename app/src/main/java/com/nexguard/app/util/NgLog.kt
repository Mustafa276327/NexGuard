package com.nexguard.app.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

enum class LogLevel { INFO, WARN, ERROR }

data class NgLogEntry(
    val id: Long,
    val time: Long,
    val level: LogLevel,
    val tag: String,
    val message: String
)

/**
 * Uygulama içi canlı log. Dosya yolu veya kişisel içerik yazılmaz; yalnızca
 * dosya adları ve işlem sonuçları. En fazla 400 kayıt bellekte tutulur.
 */
object NgLog {
    private val nextId = AtomicLong(1)
    private val _entries = MutableStateFlow<List<NgLogEntry>>(emptyList())
    val entries: StateFlow<List<NgLogEntry>> = _entries.asStateFlow()

    fun i(tag: String, message: String) = add(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String) = add(LogLevel.WARN, tag, message)
    fun e(tag: String, message: String) = add(LogLevel.ERROR, tag, message)

    private fun add(level: LogLevel, tag: String, message: String) {
        val entry = NgLogEntry(
            id = nextId.getAndIncrement(),
            time = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message.take(500)
        )
        _entries.update { (it + entry).takeLast(400) }
    }

    fun clear() {
        _entries.value = emptyList()
    }

    fun asText(): String {
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return _entries.value.joinToString("\n") { e ->
            "${fmt.format(Date(e.time))} [${e.level}] ${e.tag}: ${e.message}"
        }
    }
}
