package com.climapilot.free

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * EN: Re-arms all scene schedules after a reboot. AlarmManager alarms are cleared when the device
 *     restarts, so without this any daily scene time would silently stop working until the app is
 *     opened again. Triggered by ACTION_BOOT_COMPLETED (see the manifest).
 * DE: Stellt nach einem Neustart alle Szenen-Zeitpläne wieder her. AlarmManager-Alarme werden beim
 *     Neustart gelöscht; ohne diesen Empfänger würde jede tägliche Szenenzeit stillschweigend
 *     ausfallen, bis die App erneut geöffnet wird. Ausgelöst durch ACTION_BOOT_COMPLETED (siehe Manifest).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val appCtx = context.applicationContext
            val scenes = SceneRepo.load(appCtx) ?: return
            SceneScheduler.rescheduleAll(appCtx, scenes)
        }
    }
}
