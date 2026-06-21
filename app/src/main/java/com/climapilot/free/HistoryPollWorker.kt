package com.climapilot.free

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.climapilot.free.midea.MideaAcSession
import com.climapilot.free.midea.MideaDevice
import java.util.concurrent.TimeUnit

/**
 * EN: Periodic background poll (~15 min, the WorkManager minimum) that — only while the history feature
 *     is enabled and the phone is on an unmetered (Wi-Fi) network — connects to the AC over the LAN with
 *     the cached token, reads state + energy, and appends a [UsageHistory] sample. This is what fills the
 *     charts when the app is closed. A missed poll (AC unreachable / not on home Wi-Fi) is simply skipped;
 *     the next interval tries again. WorkManager re-schedules it across reboots automatically.
 * DE: Periodischer Hintergrund-Poll (~15 min, das WorkManager-Minimum), der — nur solange das
 *     Verlaufs-Feature aktiv ist und das Handy in einem ungemessenen (WLAN-)Netz hängt — sich mit dem
 *     gecachten Token über das LAN mit der Klima verbindet, Zustand + Energie liest und einen
 *     [UsageHistory]-Messwert anhängt. Das füllt die Charts auch bei geschlossener App. Ein verpasster
 *     Poll (Klima nicht erreichbar / nicht im Heim-WLAN) wird einfach übersprungen; das nächste Intervall
 *     versucht es erneut. WorkManager plant ihn über Neustarts hinweg automatisch neu.
 */
class HistoryPollWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!SettingsRepo.historyEnabled(applicationContext)) return Result.success()
        // EN: Poll every cached AC so each gets its own history. DE: Jede gecachte Klima abfragen, damit jede ihre eigene Historie bekommt.
        for (dev in TokenRepo.list(applicationContext)) {
            val device = MideaDevice(
                ip = dev.ip, port = dev.port, id = dev.id, sn = "", name = dev.name, type = 0xAC, version = 3,
            )
            runCatching {
                val session = MideaAcSession(device, cachedCreds = dev.token to dev.key)
                try {
                    session.connect()
                    val st = session.queryState()
                    val en = session.queryEnergy()
                    UsageHistory.record(
                        applicationContext, dev.id,
                        en?.powerW, en?.totalKwh, st?.powerOn ?: false,
                        st?.indoorTemp, st?.outdoorTemp, st?.fanSpeed ?: 0,
                    )
                } finally {
                    session.close()
                }
            }
        }
        // EN: Always success — a missed sample is fine, the schedule keeps running. DE: Immer Erfolg — ein verpasster Messwert ist ok, der Zeitplan läuft weiter.
        return Result.success()
    }

    companion object {
        private const val NAME = "ac_history_poll"

        /** EN: Turn the ~15 min Wi-Fi poll on or off. DE: Den ~15-min-WLAN-Poll ein- oder ausschalten. */
        fun setEnabled(ctx: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(ctx.applicationContext)
            if (!enabled) {
                wm.cancelUniqueWork(NAME)
                return
            }
            val req = PeriodicWorkRequestBuilder<HistoryPollWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build(),
                )
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}
