package com.climapilot.free

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * EN: Runs the weekly day-planner ([PlanRepo]). Design goal: a planned scene must apply on time even
 *     while the phone sits idle on Wi-Fi with the app closed. Two parts make that work:
 *
 *     1. *Timing* — a single [AlarmManager.setAlarmClock] alarm is always armed for the **next** plan
 *        event (a window start or end). setAlarmClock is the one alarm type that reliably fires out of
 *        Doze without the user toggling anything (plain exact/allow-while-idle alarms get deferred until
 *        the phone next wakes). Only one alarm slot is used; after each firing the next event is re-armed.
 *
 *     2. *State reconciliation* — when the alarm fires (or after a reboot) we don't blindly run the
 *        action we armed; we recompute **what the plan says the AC should be doing right now** and apply
 *        that. So if several boundaries were missed during a long Doze/while the phone was off, we still
 *        converge to the correct current state instead of replaying a stale "start" after its window
 *        already ended. A persisted "last applied event time" stops the same boundary from re-firing.
 *
 *     The actual network apply is delegated to [AcCommandWorker] (Wi-Fi lock, retries, LAN re-discovery),
 *     exactly like the daily scenes and the sleep timer. Reliability still depends on the app being
 *     exempt from battery optimisation on aggressive OEMs (see [ReliabilityHelper]).
 *
 * DE: Führt den Wochen-Tagesplaner ([PlanRepo]) aus. Ziel: eine geplante Szene muss pünktlich greifen,
 *     auch wenn das Handy mit geschlossener App im WLAN im Standby liegt. Dafür sorgen zwei Teile:
 *
 *     1. *Timing* — es ist immer genau ein [AlarmManager.setAlarmClock]-Alarm für das **nächste**
 *        Plan-Ereignis (Fensterbeginn oder -ende) gesetzt. setAlarmClock ist der einzige Alarmtyp, der
 *        zuverlässig aus dem Doze auslöst, ohne dass der Nutzer etwas umschalten muss (einfache
 *        exakte/allow-while-idle-Alarme werden bis zum nächsten Aufwachen verzögert). Es wird nur ein
 *        Alarm-Slot genutzt; nach jedem Auslösen wird das nächste Ereignis neu gesetzt.
 *
 *     2. *Zustandsabgleich* — wenn der Alarm auslöst (oder nach einem Neustart) führen wir nicht blind
 *        die gesetzte Aktion aus, sondern berechnen neu, **was der Plan gerade jetzt für die Klima
 *        vorsieht**, und wenden das an. Wurden also während eines langen Doze / bei ausgeschaltetem
 *        Handy mehrere Grenzen verpasst, landen wir trotzdem im korrekten aktuellen Zustand, statt einen
 *        veralteten „Start" nachzuspielen. Eine gespeicherte „Zeit der letzten angewandten Aktion"
 *        verhindert, dass dieselbe Grenze erneut auslöst.
 *
 *     Das eigentliche Anwenden im Netz übernimmt [AcCommandWorker] (WLAN-Lock, Retries, LAN-Neusuche) —
 *     genau wie bei den täglichen Szenen und dem Sleep-Timer. Die Zuverlässigkeit hängt weiterhin davon
 *     ab, dass die App auf aggressiven Herstellern von der Akku-Optimierung ausgenommen ist (siehe
 *     [ReliabilityHelper]).
 */
object PlanScheduler {
    const val ACTION_TICK = "com.climapilot.free.PLAN_TICK"

    private const val PREFS = "climapilot_plan_sched"
    private const val K_LAST = "last_applied"     // EN: event time we last acted on / DE: Zeit der zuletzt ausgeführten Aktion
    // EN: One fixed alarm slot for the whole plan (the "next event"). DE: Ein fester Alarm-Slot für den ganzen Plan (das „nächste Ereignis").
    private const val REQUEST_CODE = 0x504C       // "PL"
    private const val DAY_MS = 24L * 60 * 60 * 1000
    // EN: An alarm may fire a hair early; treat events within this window as due now. DE: Ein Alarm kann minimal zu früh feuern; Ereignisse in diesem Fenster gelten als jetzt fällig.
    private const val FIRE_TOLERANCE_MS = 10_000L

    // EN: A concrete moment the plan changes state. DE: Ein konkreter Moment, in dem der Plan den Zustand ändert.
    private data class Ev(val time: Long, val isStart: Boolean, val sceneId: String?)

    /**
     * EN: Arm the alarm for the next future plan event (or cancel it if the plan is empty/disabled).
     *     Called whenever the plan or scenes change, and on every app start. Never applies anything.
     * DE: Den Alarm für das nächste künftige Plan-Ereignis setzen (oder abbrechen, wenn der Plan leer/aus
     *     ist). Wird bei jeder Plan-/Szenen-Änderung und bei jedem App-Start aufgerufen. Wendet nie etwas an.
     */
    fun reschedule(ctx: Context) {
        val now = System.currentTimeMillis()
        val next = upcomingEvents(ctx, now)
            .filter { it.time > now }
            .minByOrNull { it.time }
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(ctx)
        if (next == null) {
            am.cancel(pi)
            return
        }
        arm(am, pi, ctx, next.time)
    }

    /**
     * EN: Apply the action that should be in effect *now*, then re-arm the next event. Called from the
     *     alarm receiver and from [BootReceiver]. Idempotent: the same boundary won't be applied twice.
     * DE: Die Aktion anwenden, die *jetzt* gelten soll, und dann das nächste Ereignis neu setzen. Wird
     *     vom Alarm-Empfänger und von [BootReceiver] aufgerufen. Idempotent: dieselbe Grenze wird nicht
     *     zweimal angewendet.
     */
    fun reconcileAndReschedule(ctx: Context) {
        val now = System.currentTimeMillis()
        // EN: Look back far enough to catch a boundary missed during a long Doze / power-off (covers an
        //     overnight window plus a full missed day). DE: Weit genug zurückschauen, um eine im langen
        //     Doze / bei ausgeschaltetem Handy verpasste Grenze zu erfassen (deckt ein Über-Nacht-Fenster
        //     plus einen ganzen verpassten Tag ab).
        val due = upcomingEvents(ctx, now - 2 * DAY_MS)
            .filter { it.time <= now + FIRE_TOLERANCE_MS }
            // EN: The latest boundary at/just before now defines the current intended state; on a tie a
            //     window start wins over an off (so a back-to-back window takes over instead of switching off).
            // DE: Die letzte Grenze bei/kurz vor jetzt bestimmt den aktuellen Soll-Zustand; bei Gleichstand
            //     gewinnt ein Fensterbeginn über ein Aus (ein anschließendes Fenster übernimmt, statt auszuschalten).
            .maxWithOrNull(compareBy({ it.time }, { if (it.isStart) 1 else 0 }))

        val last = prefs(ctx).getLong(K_LAST, 0L)
        if (due != null && due.time > last) {
            if (due.isStart && due.sceneId != null) {
                AcCommandWorker.enqueuePlanScene(ctx, due.sceneId)
            } else if (!due.isStart) {
                AcCommandWorker.enqueuePlanPowerOff(ctx)
            }
            prefs(ctx).edit().putLong(K_LAST, due.time).apply()
        }
        reschedule(ctx)
    }

    // ---- internals ----

    /**
     * EN: Generate every plan event from [fromMs] through ~9 days ahead, sorted by time. Skips disabled
     *     entries and entries whose scene no longer exists. Handles windows that cross midnight.
     * DE: Alle Plan-Ereignisse von [fromMs] bis ~9 Tage voraus erzeugen, nach Zeit sortiert. Überspringt
     *     deaktivierte Einträge und solche, deren Szene es nicht mehr gibt. Berücksichtigt Fenster über Mitternacht.
     */
    private fun upcomingEvents(ctx: Context, fromMs: Long): List<Ev> {
        val plan = PlanRepo.load(ctx)?.filter { it.enabled } ?: return emptyList()
        if (plan.isEmpty()) return emptyList()
        val sceneIds = (SceneRepo.load(ctx) ?: emptyList()).map { it.id }.toHashSet()
        val active = plan.filter { it.sceneId in sceneIds && it.days.isNotEmpty() }
        if (active.isEmpty()) return emptyList()

        val events = ArrayList<Ev>()
        // EN: Walk local-midnight day starts from one day before the range to nine days after. Times are
        //     built by setting the hour/minute fields on each day's calendar (not by adding milliseconds),
        //     so they stay correct across daylight-saving transitions. DE: Lokale Mitternachts-Tagesanfänge
        //     von einem Tag vor dem Bereich bis neun Tage danach durchgehen. Zeiten werden durch Setzen der
        //     Stunden-/Minutenfelder am jeweiligen Tag gebildet (nicht durch Millisekunden-Addition), damit
        //     sie auch über Sommer-/Winterzeit-Umstellungen korrekt bleiben.
        val day = Calendar.getInstance().apply {
            timeInMillis = fromMs
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -1)
        }
        repeat(11) {
            val iso = isoDayOfWeek(day)
            active.forEach { e ->
                if (iso in e.days) {
                    events.add(Ev(atTime(day, e.startMinutes, plusDay = false), isStart = true, sceneId = e.sceneId))
                    if (e.endAction == EndAction.OFF) {
                        // EN: End ≤ start ⇒ the window runs into the next day. DE: Ende ≤ Start ⇒ das Fenster reicht in den Folgetag.
                        val overnight = e.endMinutes <= e.startMinutes
                        events.add(Ev(atTime(day, e.endMinutes, plusDay = overnight), isStart = false, sceneId = null))
                    }
                }
            }
            day.add(Calendar.DAY_OF_YEAR, 1)
        }
        return events.sortedBy { it.time }
    }

    /** EN: Epoch millis of [day] (optionally +1 day) at [minutes] since midnight, DST-correct. DE: Epoch-Millis von [day] (optional +1 Tag) um [minutes] seit Mitternacht, sommerzeit-korrekt. */
    private fun atTime(day: Calendar, minutes: Int, plusDay: Boolean): Long {
        val c = day.clone() as Calendar
        if (plusDay) c.add(Calendar.DAY_OF_YEAR, 1)
        c.set(Calendar.HOUR_OF_DAY, minutes / 60)
        c.set(Calendar.MINUTE, minutes % 60)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** EN: ISO weekday (1=Mon … 7=Sun) of a calendar. DE: ISO-Wochentag (1=Mo … 7=So) eines Kalenders. */
    private fun isoDayOfWeek(cal: Calendar): Int {
        val dow = cal.get(Calendar.DAY_OF_WEEK)   // 1=Sun … 7=Sat
        return if (dow == Calendar.SUNDAY) 7 else dow - 1
    }

    private fun arm(am: AlarmManager, pi: PendingIntent, ctx: Context, triggerAt: Long) {
        // EN: Tapping the alarm-clock chip opens the app. DE: Ein Tippen auf das Wecker-Symbol öffnet die App.
        val show = PendingIntent.getActivity(
            ctx, REQUEST_CODE, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // EN: setAlarmClock = reliable & Doze-proof, but some OEMs throw without exact-alarm permission;
        //     never let scheduling crash — fall back to allow-while-idle (may be deferred, still fires).
        // DE: setAlarmClock = zuverlässig & Doze-fest, aber manche Hersteller werfen ohne Exact-Alarm-Recht;
        //     das Planen darf nie abstürzen — Fallback auf allow-while-idle (ggf. verzögert, feuert trotzdem).
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), pi)
        } catch (e: SecurityException) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } catch (e2: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }
    }

    private fun pendingIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx, PlanAlarmReceiver::class.java).apply { action = ACTION_TICK }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(ctx, REQUEST_CODE, intent, flags)
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
