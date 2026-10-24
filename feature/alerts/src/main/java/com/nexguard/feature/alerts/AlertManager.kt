package com.nexguard.feature.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nexguard.core.common.Verdict
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

object ThreatActions {
    const val ACTION_QUARANTINE = "com.nexguard.action.QUARANTINE_THREAT"
    const val EXTRA_PATH = "threat_path"
    const val EXTRA_THREAT_TYPE = "threat_type"
    const val EXTRA_SCORE = "threat_score"
}

object AlertChannels {
    const val THREATS = "nexguard_threats"
    const val SERVICE = "ng_monitor"
    const val INFO = "nexguard_info"
}

/**
 * Tehdit bildirimleri: yüksek öncelikli kanal, karantina aksiyonu, uygulamaya
 * güvenli yönlendirme ve izin/kanal kontrolleri. Bildirim gönderilemezse
 * (izin yok) sessizce atlar; tarama akışını etkilemez.
 */
@Singleton
class AlertManager @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {
    private val manager = NotificationManagerCompat.from(context)
    private val lastNotified = ConcurrentHashMap<String, Long>()

    init {
        ensureChannels()
    }

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                AlertChannels.THREATS,
                "Tehdit Uyarıları",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Zararlı veya şüpheli dosya tespitleri"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                AlertChannels.SERVICE,
                "Koruma Servisi",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Gerçek zamanlı korumanın çalıştığını gösterir"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                AlertChannels.INFO,
                "Bilgilendirmeler",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Karantina ve işlem sonuçları"
            }
        )
    }

    fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        return manager.areNotificationsEnabled()
    }

    /** Tehdit bildirimi gönderir; gönderildiyse bildirim kimliğini döndürür. */
    fun showThreat(
        path: String,
        verdict: Verdict,
        score: Int,
        threatType: String
    ): Int? {
        if (!canNotify()) return null
        val now = System.currentTimeMillis()
        val last = lastNotified[path] ?: 0L
        if (now - last < 30_000) return null
        if (lastNotified.size > 300) lastNotified.clear()
        lastNotified[path] = now

        val fileName = File(path).name.ifBlank { "Dosya" }
        val safeType = threatType.ifBlank { if (verdict == Verdict.DANGEROUS) "Zararlı dosya" else "Şüpheli dosya" }
        val title = when (verdict) {
            Verdict.DANGEROUS -> "Tehlikeli: $safeType"
            Verdict.SUSPICIOUS -> "Şüpheli: $safeType"
            Verdict.CLEAN -> return null
        }
        val id = (path.hashCode() and 0x7FFFFFFF).let { if (it == 0) 7 else it }

        val contentIntent = launchIntent("history")?.let {
            PendingIntent.getActivity(
                context, id, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val quarantineIntent = Intent(ThreatActions.ACTION_QUARANTINE).apply {
            setPackage(context.packageName)
            putExtra(ThreatActions.EXTRA_PATH, path)
            putExtra(ThreatActions.EXTRA_THREAT_TYPE, safeType)
            putExtra(ThreatActions.EXTRA_SCORE, score)
        }
        val quarantinePi = PendingIntent.getBroadcast(
            context, id, quarantineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, AlertChannels.THREATS)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText("$fileName · risk $score/100")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$fileName\nRisk skoru: $score/100\nTür: $safeType\n\n" +
                        "Karantinaya almanız veya dosyayı açmamanız önerilir."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setGroup("nexguard_threats")
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setLocalOnly(true)
            .setWhen(now)
            .setShowWhen(true)
            .setColor(0xFFD32F2F.toInt())
            .addAction(android.R.drawable.ic_menu_delete, "Karantinaya al", quarantinePi)
        contentIntent?.let { builder.setContentIntent(it) }

        return try {
            manager.notify(id, builder.build())
            id
        } catch (e: SecurityException) {
            null
        }
    }

    fun showQuarantined(fileName: String) {
        if (!canNotify()) return
        val id = ("quarantined:$fileName").hashCode() and 0x7FFFFFFF
        val contentIntent = launchIntent("quarantine")?.let {
            PendingIntent.getActivity(
                context, id, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val builder = NotificationCompat.Builder(context, AlertChannels.INFO)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Dosya karantinaya alındı")
            .setContentText(fileName)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$fileName güvenli biçimde karantinaya alındı."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setColor(0xFF2E7D32.toInt())
        contentIntent?.let { builder.setContentIntent(it) }
        try { manager.notify(id, builder.build()) } catch (e: SecurityException) { }
    }

    fun cancel(id: Int) {
        try { manager.cancel(id) } catch (e: Exception) { }
    }

    fun showInfo(title: String, text: String) {
        if (!canNotify()) return
        val id = ("info:$title:$text").hashCode() and 0x7FFFFFFF
        val contentIntent = launchIntent("settings")?.let {
            PendingIntent.getActivity(
                context, id, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val builder = NotificationCompat.Builder(context, AlertChannels.INFO)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        contentIntent?.let { builder.setContentIntent(it) }
        try { manager.notify(id, builder.build()) } catch (e: SecurityException) { }
    }

    fun cancelThreat(path: String) {
        val id = (path.hashCode() and 0x7FFFFFFF).let { if (it == 0) 7 else it }
        cancel(id)
    }

    private fun launchIntent(route: String): Intent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return intent.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("nexguard_route", route)
        }
    }
}
