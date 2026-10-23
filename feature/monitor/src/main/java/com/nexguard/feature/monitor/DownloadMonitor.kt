package com.nexguard.feature.monitor

import android.os.FileObserver
import com.nexguard.core.eventbus.EventBus
import com.nexguard.core.eventbus.NexEvent
import java.io.File

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadMonitor @Inject constructor(private val bus: EventBus) {
    private val observers = mutableListOf<FileObserver>()
    private val dirs = listOf(
        "/storage/emulated/0/Download",
        "/storage/emulated/0/Downloads",
        "/storage/emulated/0/DCIM",
        "/storage/emulated/0/Documents"
    )
    private val pending = mutableMapOf<String, Long>()

    fun start() {
        dirs.forEach { path ->
            val f = File(path)
            if (!f.exists()) return@forEach
            val obs = object : FileObserver(f, CREATE or MOVED_TO or CLOSE_WRITE) {
                override fun onEvent(event: Int, path: String?) {
                    if (path == null) return
                    val full = "${f.absolutePath}/$path"
                    if (full.endsWith(".crdownload") || full.endsWith(".part") || full.endsWith(".tmp")) return
                    bus.tryEmit(NexEvent.DownloadDetected(full))
                }
            }
            obs.startWatching()
            observers += obs
        }
    }

    fun stop() {
        observers.forEach { it.stopWatching() }
        observers.clear()
    }
}