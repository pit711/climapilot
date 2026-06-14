package com.climapilot.free

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.climapilot.free.midea.MideaAcSession
import com.climapilot.free.midea.MideaDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * EN: Fired by [SleepTimerScheduler] when a sleep timer is due. Connects to the target device
 *     offline (using its cached token) and powers it off, preserving the other settings by reading
 *     the live state first. Works even when the app process is gone — that is the whole point of
 *     moving the timer off the ViewModel coroutine.
 * DE: Wird von [SleepTimerScheduler] ausgelöst, wenn ein Sleep-Timer fällig ist. Verbindet sich
 *     offline mit dem Zielgerät (mit dessen gecachtem Token) und schaltet es aus; die übrigen
 *     Einstellungen bleiben erhalten, indem zuerst der Live-Zustand gelesen wird. Funktioniert auch,
 *     wenn der App-Prozess weg ist — genau dafür wandert der Timer aus der ViewModel-Coroutine.
 */
class SleepTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val deviceId = intent.getLongExtra(SleepTimerScheduler.EXTRA_DEVICE_ID, 0L)
        val appCtx = context.applicationContext
        // EN: Keep the receiver alive while the network work runs. DE: Den Receiver am Leben halten, während die Netzwerkarbeit läuft.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                powerOff(appCtx, deviceId)
            } catch (_: Exception) {
                // EN: Best-effort; nothing to do if the AC is unreachable. DE: Best-Effort; nichts zu tun, wenn die Klima nicht erreichbar ist.
            } finally {
                SleepTimerScheduler.clear(appCtx)
                pending.finish()
            }
        }
    }

    private suspend fun powerOff(ctx: Context, deviceId: Long) {
        // EN: Resolve the device from the cached tokens (offline-capable). DE: Das Gerät aus den gecachten Tokens ermitteln (offline-fähig).
        val dev = TokenRepo.list(ctx).firstOrNull { it.id == deviceId }
            ?: TokenRepo.list(ctx).firstOrNull()
            ?: return
        val device = MideaDevice(
            ip = dev.ip, port = dev.port, id = dev.id, sn = "", name = dev.name, type = 0xAC, version = 3,
        )
        val session = MideaAcSession(device, cachedCreds = dev.token to dev.key)
        try {
            session.connect()
            // EN: Carry over the current mode/temp/fan so the off command doesn't reset them. DE: Aktuellen Modus/Temp/Lüfter übernehmen, damit der Aus-Befehl sie nicht zurücksetzt.
            session.queryState()?.let { s ->
                s.mode.takeIf { it in 1..5 }?.let { session.mode = it }
                session.tempC = s.targetTemp
                s.fanSpeed.takeIf { it in 1..102 }?.let { session.fan = it }
            }
            session.setPower(false)
        } finally {
            session.close()
        }
    }
}
