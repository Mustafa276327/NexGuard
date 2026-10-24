package com.nexguard.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.nexguard.app.service.MonitorService
import com.nexguard.app.util.NgLog

class MonitorActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_STOP) return
        context.getSharedPreferences("nexguard_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("monitor_on", false).apply()
        context.stopService(Intent(context, MonitorService::class.java))
        NotificationManagerCompat.from(context).cancel(1)
        NgLog.i("İzleme", "Bildirimden kapatıldı")
    }

    companion object {
        const val ACTION_STOP = "com.nexguard.action.STOP_MONITOR"
    }
}
