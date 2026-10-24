package com.nexguard.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nexguard.app.service.MonitorService
import com.nexguard.app.util.NgLog
import com.nexguard.app.util.Prefs

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Prefs.onboardingDone(context) || !Prefs.monitorOn(context)) return
        try {
            context.startForegroundService(Intent(context, MonitorService::class.java))
            NgLog.i("Önyükleme", "İzleme servisi başlatıldı")
        } catch (e: Exception) {
            NgLog.w("Önyükleme", "İzleme servisi başlatılamadı: ${e.message}")
            // Başlatma reddedildi; servis MainActivity açılışında yeniden denenir.
        }
    }
}
