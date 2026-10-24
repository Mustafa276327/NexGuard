package com.nexguard.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nexguard.app.util.NgLog
import com.nexguard.core.common.Verdict
import com.nexguard.feature.alerts.AlertManager
import com.nexguard.feature.alerts.ThreatActions
import com.nexguard.feature.quarantine.QuarantineManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class ThreatActionReceiver : BroadcastReceiver() {
    @Inject lateinit var quarantineManager: QuarantineManager
    @Inject lateinit var alertManager: AlertManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ThreatActions.ACTION_QUARANTINE) return
        val path = intent.getStringExtra(ThreatActions.EXTRA_PATH) ?: return
        val type = intent.getStringExtra(ThreatActions.EXTRA_THREAT_TYPE).orEmpty()
        val score = intent.getIntExtra(ThreatActions.EXTRA_SCORE, 80)
        val pending = goAsync()
        NgLog.i("Bildirim", "Karantina isteği: ${File(path).name}")
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val outcome = quarantineManager.quarantine(
                    path,
                    "Bildirimden karantina",
                    type
                )
                if (outcome.success && !outcome.sourceDeleteFailed) {
                    alertManager.cancelThreat(path)
                    alertManager.showQuarantined(File(path).name)
                    NgLog.i("Bildirim", "Karantina tamamlandı: ${File(path).name}")
                } else if (outcome.success) {
                    NgLog.w("Bildirim", "Karantina oluştu ancak kaynak silinemedi: ${File(path).name}")
                    alertManager.showInfo(
                        "Kaynak dosya silinemedi",
                        "${File(path).name} karantinaya alındı ancak özgün dosya kaldı."
                    )
                } else {
                    NgLog.e("Bildirim", "Karantina başarısız: ${outcome.message}")
                    alertManager.showInfo("Karantina başarısız", outcome.message)
                    alertManager.showThreat(path, Verdict.DANGEROUS, score, type)
                }
            } catch (e: Exception) {
                NgLog.e("Bildirim", "Karantina hatası: ${e.message}")
                Log.w("NexGuardReceiver", "Karantina hatası: ${e.message}")
                alertManager.showInfo("Karantina hatası", e.message ?: "Bilinmeyen hata")
            } finally {
                pending.finish()
            }
        }
    }
}
