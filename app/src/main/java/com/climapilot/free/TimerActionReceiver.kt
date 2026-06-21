package com.climapilot.free

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * EN: Handles the two action buttons on the sleep-timer countdown notification.
 *     • Off now  → cancel the pending alarm/worker, then power the unit off immediately.
 *     • Cancel   → just drop the timer (alarm + worker + notification), leave the AC as-is.
 * DE: Verarbeitet die zwei Aktions-Knöpfe der Sleep-Timer-Countdown-Benachrichtigung.
 *     • Jetzt aus → ausstehenden Alarm/Worker abbrechen, dann das Gerät sofort ausschalten.
 *     • Abbrechen → den Timer nur verwerfen (Alarm + Worker + Notification), Klima bleibt wie sie ist.
 */
class TimerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        val deviceId = SleepTimerScheduler.deviceId(ctx) ?: 0L
        // EN: Both paths cancel the pending timer (also removes the notification via clear()). DE: Beide Pfade brechen den ausstehenden Timer ab (entfernt via clear() auch die Notification).
        SleepTimerScheduler.cancel(ctx)
        if (intent.action == ACTION_OFF_NOW) {
            AcCommandWorker.enqueuePowerOff(ctx, deviceId)
        }
    }

    companion object {
        const val ACTION_OFF_NOW = "com.climapilot.free.TIMER_OFF_NOW"
        const val ACTION_CANCEL = "com.climapilot.free.TIMER_CANCEL"
    }
}
