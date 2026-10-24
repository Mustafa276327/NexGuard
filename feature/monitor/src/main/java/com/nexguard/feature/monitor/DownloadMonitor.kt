package com.nexguard.feature.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Environment
import android.os.FileObserver
import android.util.Log
import androidx.core.content.ContextCompat
import com.nexguard.core.eventbus.EventBus
import com.nexguard.core.eventbus.NexEvent
import com.nexguard.feature.scan.Scanner
import com.nexguard.feature.scan.detection.ScanOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * İndirilenler, belgeler ve mesajlaşma uygulamalarının dosya klasörlerini izler.
 * Dosya boyutu iki kez sabitlenince tarar; kısmi indirmeleri (.part, .crdownload)
 * atlar. Ayrıca yeni kurulan/güncellenen paketlerin APK'sını tarar.
 */
@Singleton
class DownloadMonitor @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val bus: EventBus,
    private val scanner: Scanner
) {
    private val observers = mutableListOf<FileObserver>()
    private val jobs = ConcurrentHashMap<String, Job>()
    private val lastScanned = ConcurrentHashMap<String, String>()
    private var scope: CoroutineScope? = null
    @Volatile private var running = false

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val pkg = intent.data?.schemeSpecificPart ?: return
            if (pkg == ctx.packageName) return
            val source = try {
                ctx.packageManager.getPackageInfo(pkg, 0).applicationInfo?.sourceDir
            } catch (e: Exception) {
                null
            } ?: return
            val s = scope ?: return
            s.launch {
                try {
                    if (File(source).canRead()) {
                        scanner.scanFile(
                            source,
                            ScanOptions(
                                source = "install",
                                deep = false,
                                useCache = true,
                                mode = "monitor",
                                label = "Yeni uygulama"
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Paket taraması başarısız: ${e.message}")
                }
            }
        }
    }

    fun start() {
        if (running) return
        running = true
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = s

        watchDirs().forEach { dir ->
            try {
                val observer = object : FileObserver(
                    dir,
                    CREATE or MOVED_TO or CLOSE_WRITE
                ) {
                    override fun onEvent(event: Int, path: String?) {
                        if (path.isNullOrBlank()) return
                        val file = File(dir, path)
                        if (!file.isFile) return
                        handleCandidate(file)
                    }
                }
                observer.startWatching()
                observers += observer
            } catch (e: Exception) {
                Log.w(TAG, "Klasör izlenemedi: ${dir.absolutePath} (${e.message})")
            }
        }

        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            // PACKAGE_* sistem tarafından korunan yayınlardır; üçüncü taraflar
            // taklit edemez. Sistem yayınlarını alabilmek için EXPORTED gerekir.
            ContextCompat.registerReceiver(context, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        } catch (e: Exception) {
            Log.w(TAG, "Paket alıcısı kaydedilemedi: ${e.message}")
        }
        Log.i(TAG, "İzleme başladı (${observers.size} klasör)")
    }

    private fun handleCandidate(file: File) {
        val name = file.name.lowercase()
        if (name.endsWith(".part") || name.endsWith(".crdownload") || name.endsWith(".tmp") ||
            name.endsWith(".download") || name.endsWith(".opdownload")
        ) return

        val s = scope ?: return
        jobs.remove(file.absolutePath)?.cancel()
        if (jobs.size > 80) return
        val job = s.launch {
            try {
                var lastSize = -1L
                var stable = 0
                val started = System.currentTimeMillis()
                while (isActive && System.currentTimeMillis() - started < 90_000) {
                    val f = File(file.absolutePath)
                    if (!f.isFile) return@launch
                    val size = f.length()
                    if (size == 0L) {
                        if (System.currentTimeMillis() - started > 8_000) return@launch
                    } else if (size == lastSize) {
                        stable++
                    } else {
                        stable = 0
                    }
                    if (stable >= 2) break
                    lastSize = size
                    delay(1000)
                }
                if (stable < 2) return@launch

                val f = File(file.absolutePath)
                if (!f.isFile || f.length() <= 0) return@launch
                val stamp = "${f.length()}:${f.lastModified()}"
                if (lastScanned[file.absolutePath] == stamp) return@launch
                if (lastScanned.size > 500) lastScanned.clear()
                lastScanned[file.absolutePath] = stamp

                bus.tryEmit(NexEvent.DownloadDetected(file.absolutePath))
                scanner.scanFile(
                    file.absolutePath,
                    ScanOptions(
                        source = "download",
                        deep = true,
                        useCache = false,
                        mode = "monitor",
                        label = "İndirme izleme"
                    )
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "İndirme taraması başarısız: ${e.message}")
                bus.tryEmit(NexEvent.ServiceDied("download-monitor"))
            } finally {
                jobs.remove(file.absolutePath)
            }
        }
        jobs[file.absolutePath] = job
    }

    private fun watchDirs(): List<File> {
        val relativeDirs = listOf(
            "Download",
            "Downloads",
            "Documents",
            "DCIM",
            "Pictures",
            "Movies",
            "Music",
            "Bluetooth",
            "Telegram/Telegram Documents",
            "WhatsApp/Media/WhatsApp Documents",
            "Android/media/org.telegram.messenger/Telegram/Telegram Documents",
            "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents"
        )
        return storageRoots()
            .flatMap { root -> relativeDirs.map { File(root, it) } }
            .distinctBy { it.absolutePath }
            .filter { it.isDirectory }
    }

    /** Birincil depolama ve takılı SD/USB birimlerinin kökleri. */
    private fun storageRoots(): List<File> {
        val roots = LinkedHashSet<File>()
        try {
            Environment.getExternalStorageDirectory()?.let { roots += it }
        } catch (e: Exception) { }
        try {
            context.getExternalFilesDirs(null).forEach { dir ->
                val path = dir?.absolutePath?.replace('\\', '/') ?: return@forEach
                val marker = "/Android/data/"
                val index = path.indexOf(marker)
                if (index > 0) {
                    val volume = File(path.substring(0, index))
                    if (volume.exists()) roots += volume
                }
            }
        } catch (e: Exception) { }
        return roots.toList()
    }

    fun stop() {
        running = false
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        observers.forEach {
            try { it.stopWatching() } catch (e: Exception) { }
        }
        observers.clear()
        try { context.unregisterReceiver(packageReceiver) } catch (e: Exception) { }
        scope?.cancel()
        scope = null
        Log.i(TAG, "İzleme durdu")
    }

    private companion object {
        const val TAG = "NexGuardMonitor"
    }
}
