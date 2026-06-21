package com.climapilot.free

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * EN: Fired by [SleepTimerScheduler] when a sleep timer is due. The exact timing is handled by the
 *     AlarmManager alarm; the actual power-off (connect + command, with retries and a Wi-Fi lock) is
 *     handed to [AcCommandWorker] so it survives the ~10 s broadcast budget and a momentarily
 *     unavailable LAN. Works even when the app process is gone.
 * DE: Wird von [SleepTimerScheduler] ausgelöst, wenn ein Sleep-Timer fällig ist. Das exakte Timing
 *     übernimmt der AlarmManager-Alarm; das eigentliche Ausschalten (Verbinden + Befehl, mit Retries
 *     und WLAN-Lock) übernimmt [AcCommandWorker], damit es das ~10-s-Broadcast-Budget und ein kurz
 *     nicht erreichbares LAN übersteht. Funktioniert auch, wenn der App-Prozess weg ist.
 */
class SleepTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val deviceId = intent.getLongExtra(SleepTimerScheduler.EXTRA_DEVICE_ID, 0L)
        AcCommandWorker.enqueuePowerOff(context.applicationContext, deviceId)
    }
}
