package com.nexguard.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nexguard.app.R
import com.nexguard.app.receiver.MonitorActionReceiver
import com.nexguard.app.util.NgLog
import com.nexguard.app.util.Prefs
import com.nexguard.core.common.Verdict
import com.nexguard.core.eventbus.EventBus
import com.nexguard.core.eventbus.NexEvent
import com.nexguard.feature.alerts.AlertChannels
import com.nexguard.feature.alerts.AlertManager
import com.nexguard.feature.monitor.DownloadMonitor
import com.nexguard.feature.quarantine.QuarantineManager
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
    @Inject lateinit var quarantineManager: QuarantineManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        if (!Prefs.onboardingDone(this) || !Prefs.monitorOn(this)) {
            NgLog.i("İzleme", "Servis kapalı: izleme ayarı devre dışı")
            stopSelf()
            return
        }
        alertManager.ensureChannels()
        try {
            monitor.start()
            NgLog.i("İzleme", "Gerçek zamanlı izleme başlatıldı")
        } catch (e: Exception) {
            NgLog.e("İzleme", "İzleme başlatılamadı: ${e.message}")
            Log.w(TAG, "İzleme başlatılamadı: ${e.message}")
        }
        scope.launch {
            bus.events.collect { event ->
                if (event !is NexEvent.ThreatDetected) return@collect
                if (event.verdict == Verdict.CLEAN) return@collect
                handleThreat(event)
            }
        }
    }

    private suspend fun handleThreat(event: NexEvent.ThreatDetected) {
        val prefs = getSharedPreferences("nexguard_prefs", MODE_PRIVATE)
        val notify = prefs.getBoolean("notify_threats", true)
        val autoQuarantine = prefs.getBoolean("auto_quarantine", false)
        val fromBackground = event.source == "download" || event.source == "install"
        if (!fromBackground) return

        val name = File(event.path).name
        NgLog.w("İzleme", "Arka plan tehdidi: $name · ${event.threatType} · ${event.score}/100")
        if (event.verdict == Verdict.DANGEROUS && autoQuarantine && isExternalFile(event.path)) {
            val outcome = quarantineManager.quarantine(
                event.path,
                "Otomatik karantina (indirme izleme)",
                event.threatType
            )
            if (outcome.success && !outcome.sourceDeleteFailed) {
                alertManager.cancelThreat(event.path)
                if (notify) alertManager.showQuarantined(name)
                NgLog.i("İzleme", "Otomatik karantina tamamlandı: $name")
            } else if (outcome.success) {
                NgLog.w("İzleme", "Karantina oluştu ancak kaynak silinemedi: $name")
                if (notify) {
                    alertManager.showInfo(
                        "Kaynak dosya silinemedi",
                        "$name karantinaya alındı ancak özgün dosya kaldı. Dosyayı açmayın."
                    )
                    alertManager.showThreat(event.path, event.verdict, event.score, event.threatType)
                }
            } else if (notify) {
                NgLog.e("İzleme", "Otomatik karantina başarısız: ${outcome.message}")
                alertManager.showInfo("Otomatik karantina başarısız", outcome.message)
                alertManager.showThreat(event.path, event.verdict, event.score, event.threatType)
            }
        } else if (notify) {
            alertManager.showThreat(event.path, event.verdict, event.score, event.threatType)
        }
    }

    private fun isExternalFile(path: String): Boolean {
        return path.startsWith("/storage/") && File(path).isFile
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Prefs.onboardingDone(this) || !Prefs.monitorOn(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    FOREGROUND_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(
                    FOREGROUND_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            }
        } catch (e: Exception) {
            // Android 12+ arka plandan başlatma kısıtı olabilir; çökme yerine dur.
            NgLog.e("İzleme", "Ön plan servisi başlatılamadı: ${e.message}")
            Log.w(TAG, "Ön plan servisi başlatılamadı: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("nexguard_route", "history")
        }
        val contentPi = launch?.let {
            PendingIntent.getActivity(
                this, 1, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val stopIntent = Intent(this, MonitorActionReceiver::class.java).apply {
            action = MonitorActionReceiver.ACTION_STOP
        }
        val stopPi = PendingIntent.getBroadcast(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, AlertChannels.SERVICE)
            .setSmallIcon(R.drawable.ic_stat_nexguard)
            .setContentTitle("NexGuard koruması aktif")
            .setContentText("İndirilenler ve yeni uygulamalar izleniyor")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setColor(0xFF0B57D0.toInt())
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Durdur", stopPi)
        contentPi?.let { builder.setContentIntent(it) }
        return builder.build()
    }

    override fun onDestroy() {
        NgLog.i("İzleme", "Gerçek zamanlı izleme durduruldu")
        scope.cancel()
        try { monitor.stop() } catch (e: Exception) { }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val TAG = "NexGuardService"
        const val FOREGROUND_ID = 1
    }
}
