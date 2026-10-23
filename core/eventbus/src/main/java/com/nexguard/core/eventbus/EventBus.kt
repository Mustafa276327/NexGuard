package com.nexguard.core.eventbus

import com.nexguard.core.common.TimelineEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed class NexEvent {
    data class ScanStarted(val path: String) : NexEvent()
    data class ScanProgress(val scanned: Int, val found: Int) : NexEvent()
    data class ScanFinished(val result: com.nexguard.core.common.ScanResult) : NexEvent()
    data class DownloadDetected(val path: String) : NexEvent()
    data class ThreatDetected(val path: String, val verdict: com.nexguard.core.common.Verdict, val score: Int) : NexEvent()
    data class Timeline(val event: TimelineEvent) : NexEvent()
    data class PermissionChanged(val permission: String, val granted: Boolean) : NexEvent()
    data class ServiceDied(val service: String) : NexEvent()
}

class EventBus {
    private val _events = MutableSharedFlow<NexEvent>(extraBufferCapacity = 64, replay = 0)
    val events: SharedFlow<NexEvent> = _events.asSharedFlow()

    suspend fun emit(event: NexEvent) { _events.emit(event) }
    fun tryEmit(event: NexEvent): Boolean = _events.tryEmit(event)
}