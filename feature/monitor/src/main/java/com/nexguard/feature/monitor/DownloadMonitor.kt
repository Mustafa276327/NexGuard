package com.nexguard.feature.monitor

import android.os.FileObserver
import com.nexguard.core.eventbus.EventBus
import com.nexguard.core.eventbus.NexEvent
import com.nexguard.feature.scan.Scanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadMonitor @Inject constructor(
    private val bus: EventBus,
    private val scanner: Scanner
) {
    private val observers = mutableListOf<FileObserver>()
    private val dirs = listOf(
        "/storage/emulated/0/Download",
        "/storage/emulated/0/Downloads",
        "/storage/emulated/0/DCIM",
        "/storage/emulated/0/Documents"
    )
    // path -> last known size
    private val pendingSizes = ConcurrentHashMap<String, Long>()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val checkJob = Job()
    private val executor = Executors.newScheduledThreadPool(2)

    fun start() {
        dirs.forEach { path ->
            val f = File(path)
            if (!f.exists()) return@forEach
            val obs = object : FileObserver(f, FileObserver.CREATE or FileObserver.MOVED_TO or FileObserver.CLOSE_WRITE) {
                override fun onEvent(event: Int, path: String?) {
                    if (path == null) return
                    val full = "${f.absolutePath}/$path"
                    if (full.endsWith(".crdownload") || full.endsWith(".part") || full.endsWith(".tmp")) return
                    // Boyut sabitlenene kadar bekle (maks 15 saniye), sonra tara
                    pendingSizes[full] = -1L
                    scheduleStabilityCheck(full)
                }
            }
            obs.startWatching()
            observers += obs
        }
        // Periyodik boyut kontrolü
        executor.scheduleAtFixedRate({ checkPendingSizes() }, 1, 1, java.util.concurrent.TimeUnit.SECONDS)
    }

    private fun scheduleStabilityCheck(path: String) {
        scope.launch(checkJob) {
            var lastSize: Long = -1
            var stableCount = 0
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < 15000 && stableCount < 2) {
                val f = File(path)
                val size = if (f.exists()) f.length() else -1L
                if (size == lastSize && size != -1L) {
                    stableCount++
                } else {
                    stableCount = 0
                }
                lastSize = size
                kotlinx.coroutines.delay(1000)
            }
            if (stableCount >= 2 && lastSize != -1L) {
                bus.tryEmit(NexEvent.DownloadDetected(path))
                try {
                    scanner.scanFile(path)
                } catch (e: Exception) {
                    bus.tryEmit(NexEvent.ServiceDied("download-monitor"))
                }
            }
            pendingSizes.remove(path)
        }
    }

    private fun checkPendingSizes() {
        // Cleanup stale entries older than 30s
        val now = System.currentTimeMillis()
        pendingSizes.keys.forEach { k ->
            // (no timestamp tracking in this simple version; stability coroutine handles cleanup)
        }
    }

    fun stop() {
        checkJob.cancel()
        executor.shutdownNow()
        observers.forEach { it.stopWatching() }
        observers.clear()
    }
}