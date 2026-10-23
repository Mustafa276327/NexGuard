package com.nexguard.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nexguard.app.service.MonitorService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Android 15+: BOOT_COMPLETED'dan dataSync foreground servisi başlatılamaz;
            // kısıt fırlatırsa uygulama çökmesin — sonraki manuel açılışta servis kurulur.
            try {
                context.startForegroundService(Intent(context, MonitorService::class.java))
            } catch (e: Exception) {
                // başlatma reddedildi; servis MainActivity açılışında yeniden denenebilir
            }
        }
    }
}