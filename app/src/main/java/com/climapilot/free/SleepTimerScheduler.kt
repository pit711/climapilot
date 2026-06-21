package com.climapilot.free

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * EN: Schedules the sleep timer as an [AlarmManager] alarm so the unit still powers off after N
 *     minutes even when the app is closed and its ViewModel (with the old in-process countdown) is
 *     gone. The single pending power-off is persisted (target device id + absolute trigger time) so
 *     the UI can restore the remaining-time display when the app is reopened, and so it can be
 *     cancelled. Uses [AlarmManager.setAlarmClock] — exact, wakes the device out of Doze and needs no
 *     SCHEDULE_EXACT_ALARM permission — which is the only thing that reliably fires while the phone is
 *     idle (plain exact/allow-while-idle alarms get deferred and only fired when the phone next wakes,
 *     e.g. when you reopen the app).
 *
 * DE: Plant den Sleep-Timer als [AlarmManager]-Alarm, damit das Gerät auch dann nach N Minuten
 *     ausschaltet, wenn die App geschlossen und ihr ViewModel (mit dem alten In-Prozess-Countdown)
 *     weg ist. Das einzelne ausstehende Ausschalten wird gespeichert (Ziel-Geräte-ID + absolute
 *     Auslösezeit), damit die UI beim erneuten Öffnen die Restzeit wiederherstellen und es abbrechen
 *     kann. Nutzt [AlarmManager.setAlarmClock] — exakt, weckt das Gerät aus dem Doze und braucht keine
 *     SCHEDULE_EXACT_ALARM-Berechtigung — das Einzige, das im Standby zuverlässig auslöst (einfache
 *     exakte/allow-while-idle-Alarme werden verzögert und feuern erst beim nächsten Aufwachen des
 *     Geräts, z. B. wenn man die App wieder öffnet).
 */
object SleepTimerScheduler {
    const val ACTION_SLEEP_OFF = "com.climapilot.free.SLEEP_OFF"
    const val EXTRA_DEVICE_ID = "deviceId"

    private const val PREFS = "climapilot_sleep_timer"
    private const val K_DEVICE = "device_id"
    private const val K_TRIGGER = "trigger_at"
    // EN: One fixed alarm slot — only a single sleep timer can be armed at a time. DE: Ein fester Alarm-Slot — es kann nur ein Sleep-Timer gleichzeitig aktiv sein.
    private const val REQUEST_CODE = 0x51EE9

    /** EN: Arm the power-off for [deviceId] at the absolute [triggerAt] (epoch millis). DE: Das Ausschalten für [deviceId] zur absoluten Zeit [triggerAt] (Epoch-Millis) aktivieren. */
    fun schedule(ctx: Context, deviceId: Long, triggerAt: Long) {
        prefs(ctx).edit().putLong(K_DEVICE, deviceId).putLong(K_TRIGGER, triggerAt).apply()
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(ctx, deviceId)
        // EN: Tapping the alarm-clock chip opens the app. DE: Ein Tippen auf das Wecker-Symbol öffnet die App.
        val show = PendingIntent.getActivity(
            ctx, REQUEST_CODE, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // EN: setAlarmClock is the reliable, Doze-proof choice, but some OEMs (e.g. HyperOS/MIUI) still
        //     throw if no exact-alarm permission is granted. Never let scheduling crash the app — fall
        //     back to an allow-while-idle alarm (may be deferred in Doze, but the timer still works).
        // DE: setAlarmClock ist die zuverlässige, Doze-feste Wahl, aber manche Hersteller (z. B.
        //     HyperOS/MIUI) werfen ohne Exact-Alarm-Recht trotzdem. Das Planen darf die App nie zum
        //     Absturz bringen — Fallback auf einen allow-while-idle-Alarm (ggf. im Doze verzögert, aber
        //     der Timer funktioniert weiter).
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), pi)
        } catch (e: SecurityException) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } catch (e2: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }
        // EN: Show the live countdown notification with off-now/cancel controls. DE: Die Live-Countdown-Benachrichtigung mit Jetzt-aus/Abbrechen-Steuerung zeigen.
        TimerNotification.show(ctx, triggerAt)
    }

    /** EN: Cancel the pending power-off (if any) and forget it. DE: Das ausstehende Ausschalten (falls vorhanden) abbrechen und vergessen. */
    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(ctx, deviceId(ctx) ?: 0L))
        // EN: Also drop a power-off that already fired and is still retrying in the background. DE: Auch ein bereits ausgelöstes, im Hintergrund noch wiederholendes Ausschalten verwerfen.
        AcCommandWorker.cancelPowerOff(ctx)
        clear(ctx)
    }

    /** EN: Drop the persisted state (after the alarm fired or was cancelled). DE: Den gespeicherten Zustand verwerfen (nachdem der Alarm ausgelöst wurde oder abgebrochen ist). */
    fun clear(ctx: Context) {
        prefs(ctx).edit().remove(K_DEVICE).remove(K_TRIGGER).apply()
        // EN: The timer is gone (fired or cancelled) — drop its notification. DE: Der Timer ist weg (ausgelöst oder abgebrochen) — seine Benachrichtigung entfernen.
        TimerNotification.cancel(ctx)
    }

    /** EN: Absolute trigger time of the pending power-off, or null if none is armed. DE: Absolute Auslösezeit des ausstehenden Ausschaltens, oder null, falls keiner aktiv ist. */
    fun triggerAt(ctx: Context): Long? =
        prefs(ctx).getLong(K_TRIGGER, 0L).takeIf { it > 0L }

    /** EN: Target device id of the pending power-off, or null. DE: Ziel-Geräte-ID des ausstehenden Ausschaltens, oder null. */
    fun deviceId(ctx: Context): Long? =
        prefs(ctx).getLong(K_DEVICE, 0L).takeIf { prefs(ctx).contains(K_DEVICE) }

    private fun pendingIntent(ctx: Context, deviceId: Long): PendingIntent {
        val intent = Intent(ctx, SleepTimerReceiver::class.java).apply {
            action = ACTION_SLEEP_OFF
            putExtra(EXTRA_DEVICE_ID, deviceId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(ctx, REQUEST_CODE, intent, flags)
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
