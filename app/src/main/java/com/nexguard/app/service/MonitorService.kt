package com.nexguard.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.nexguard.core.common.Verdict
import com.nexguard.core.eventbus.EventBus
import com.nexguard.core.eventbus.NexEvent
import com.nexguard.feature.alerts.AlertManager
import com.nexguard.feature.monitor.DownloadMonitor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class MonitorService : Service() {
    @Inject lateinit var monitor: DownloadMonitor
    @Inject lateinit var bus: EventBus
    @Inject lateinit var alertManager: AlertManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel("ng_monitor", "NexGuard Monitor", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
        monitor.start()
        // Tehdit bildirimleri (uygulama kapalıyken de)
        scope.launch {
            bus.events.collect { e ->
                if (e is NexEvent.ThreatDetected && e.verdict == Verdict.DANGEROUS) {
                    alertManager.showThreat(
                        "Tehdit tespit edildi",
                        "${File(e.path).name} · skor ${e.score}/100"
                    )
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = Notification.Builder(this, "ng_monitor")
            .setContentTitle("NexGuard aktif")
            .setContentText("İndirilen dosyalar izleniyor")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build()
        startForeground(1, n)
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        monitor.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
