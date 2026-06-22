package com.climapilot.free

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * EN: Re-arms time-based work after a reboot. AlarmManager alarms are cleared when the device
 *     restarts, so without this both the daily scene schedules and a pending sleep timer would
 *     silently stop working until the app is opened again. Triggered by ACTION_BOOT_COMPLETED
 *     (see the manifest).
 * DE: Stellt zeitbasierte Aufgaben nach einem Neustart wieder her. AlarmManager-Alarme werden beim
 *     Neustart gelöscht; ohne diesen Empfänger würden sowohl die täglichen Szenen-Zeitpläne als auch
 *     ein ausstehender Sleep-Timer stillschweigend ausfallen, bis die App erneut geöffnet wird.
 *     Ausgelöst durch ACTION_BOOT_COMPLETED (siehe Manifest).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val appCtx = context.applicationContext

        // EN: Re-arm all daily scene schedules. DE: Alle täglichen Szenen-Zeitpläne neu setzen.
        SceneRepo.load(appCtx)?.let { SceneScheduler.rescheduleAll(appCtx, it) }

        // EN: Re-arm the weekly plan and apply whatever it should be doing right now (a boundary may have
        //     been crossed while the phone was off). DE: Den Wochenplan neu setzen und anwenden, was er
        //     gerade vorsehen sollte (eine Grenze wurde evtl. überschritten, während das Handy aus war).
        PlanScheduler.reconcileAndReschedule(appCtx)

        // EN: Re-arm a pending sleep timer from its persisted absolute trigger time. DE: Einen ausstehenden Sleep-Timer aus seiner gespeicherten absoluten Auslösezeit neu setzen.
        val triggerAt = SleepTimerScheduler.triggerAt(appCtx)
        val deviceId = SleepTimerScheduler.deviceId(appCtx)
        if (triggerAt != null && deviceId != null) {
            if (triggerAt > System.currentTimeMillis()) {
                // EN: Still in the future — set the alarm again. DE: Noch in der Zukunft — den Alarm erneut setzen.
                SleepTimerScheduler.schedule(appCtx, deviceId, triggerAt)
            } else {
                // EN: It came due while the phone was off — power off now and forget it. DE: Wurde fällig, während das Handy aus war — jetzt ausschalten und verwerfen.
                AcCommandWorker.enqueuePowerOff(appCtx, deviceId)
            }
        }
    }
}
