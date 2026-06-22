package com.climapilot.free

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * EN: Fired by [PlanScheduler] when the next weekly-plan event is due. It hands the work to
 *     [PlanScheduler.reconcileAndReschedule], which figures out what the plan should be doing right now,
 *     enqueues the robust [AcCommandWorker] to apply it, and arms the alarm for the following event — so
 *     the plan keeps rolling whether or not the AC was reachable this time.
 * DE: Wird von [PlanScheduler] ausgelöst, wenn das nächste Wochenplan-Ereignis fällig ist. Die Arbeit
 *     übernimmt [PlanScheduler.reconcileAndReschedule]: ermittelt, was der Plan gerade vorsieht, reiht
 *     den zuverlässigen [AcCommandWorker] zum Anwenden ein und setzt den Alarm für das nächste Ereignis —
 *     der Plan läuft also weiter, egal ob die Klima diesmal erreichbar war.
 */
class PlanAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PlanScheduler.reconcileAndReschedule(context.applicationContext)
    }
}
