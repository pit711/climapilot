package com.climapilot.free

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * EN: Schedules scenes that have a daily time. Uses [AlarmManager] exact, allow-while-idle alarms so
 *     a scene fires even in Doze. Exact alarms are one-shot on modern Android, so the receiver
 *     re-schedules the next day after each firing. If the OS won't grant exact alarms, we fall back
 *     to an inexact alarm (still allow-while-idle) — it may fire a little late but won't be dropped.
 *     Reliability also depends on the app being exempt from battery optimisation (esp. on Samsung).
 *
 * DE: Plant Szenen mit täglicher Uhrzeit. Nutzt exakte „allow-while-idle"-Alarme von [AlarmManager],
 *     damit eine Szene auch im Doze-Modus auslöst. Exakte Alarme sind auf modernem Android einmalig,
 *     daher plant der Receiver nach jedem Auslösen den nächsten Tag neu. Verweigert das System exakte
 *     Alarme, fallen wir auf einen ungenauen Alarm zurück (weiterhin allow-while-idle) — er kann etwas
 *     später feuern, geht aber nicht verloren. Die Zuverlässigkeit hängt außerdem davon ab, dass die
 *     App von der Akku-Optimierung ausgenommen ist (besonders bei Samsung).
 */
object SceneScheduler {
    const val ACTION_APPLY = "com.climapilot.free.APPLY_SCENE"
    const val EXTRA_SCENE_ID = "sceneId"

    /** EN: (Re)schedule every scene: arm the ones with a time, cancel the ones without. DE: Alle Szenen (neu) planen: die mit Uhrzeit aktivieren, die ohne abbrechen. */
    fun rescheduleAll(ctx: Context, scenes: List<Scene>) {
        scenes.forEach { s ->
            val mins = s.scheduleMinutes
            if (mins != null) schedule(ctx, s.id, mins) else cancel(ctx, s.id)
        }
    }

    /** EN: Arm the next occurrence of a scene at [minutes] since midnight. DE: Das nächste Auftreten einer Szene um [minutes] seit Mitternacht aktivieren. */
    fun schedule(ctx: Context, sceneId: String, minutes: Int) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(ctx, sceneId)
        val triggerAt = nextTriggerMillis(minutes)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (e: SecurityException) {
            // EN: Exact-alarm permission revoked at runtime → inexact fallback. DE: Exakt-Alarm-Recht zur Laufzeit entzogen → ungenauer Fallback.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    /** EN: Cancel any pending alarm for a scene. DE: Einen ausstehenden Alarm einer Szene abbrechen. */
    fun cancel(ctx: Context, sceneId: String) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(ctx, sceneId))
    }

    private fun pendingIntent(ctx: Context, sceneId: String): PendingIntent {
        val intent = Intent(ctx, SceneAlarmReceiver::class.java).apply {
            action = ACTION_APPLY
            putExtra(EXTRA_SCENE_ID, sceneId)
        }
        // EN: requestCode keyed by scene id so each scene has its own alarm slot. DE: requestCode nach Szenen-ID, damit jede Szene ihren eigenen Alarm-Slot hat.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(ctx, sceneId.hashCode(), intent, flags)
    }

    /** EN: Next wall-clock time for [minutes] today, or tomorrow if already passed. DE: Nächster Zeitpunkt für [minutes] heute, sonst morgen, falls bereits vorbei. */
    private fun nextTriggerMillis(minutes: Int): Long {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutes / 60)
            set(Calendar.MINUTE, minutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (next.timeInMillis <= now.timeInMillis) next.add(Calendar.DAY_OF_YEAR, 1)
        return next.timeInMillis
    }
}
