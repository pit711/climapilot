package com.climapilot.free.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import com.climapilot.free.R
import com.climapilot.free.SleepTimerScheduler
import com.climapilot.free.TimerActionReceiver

/**
 * EN: Dedicated sleep-timer widget. Shows a live count-down to the moment the AC switches off, plus
 *     quick presets (30 m / 1 h / 2 h / 4 h) and Off-now / Cancel. The count-down uses a Chronometer,
 *     which ticks itself while the widget is visible — no polling, no battery cost. Arming reuses the
 *     app's offline SleepTimerScheduler; Off-now / Cancel go through the existing TimerActionReceiver.
 * DE: Eigenes Sleep-Timer-Widget. Zeigt einen Live-Countdown bis zum Ausschalten der Klima, dazu
 *     Schnellwahl (30 Min / 1 Std / 2 Std / 4 Std) und Jetzt-aus / Abbrechen. Der Countdown nutzt einen
 *     Chronometer, der sich selbst weiterzählt, solange das Widget sichtbar ist — kein Polling, keine
 *     Akku-Kosten. Das Scharfstellen nutzt den Offline-SleepTimerScheduler der App; Jetzt-aus /
 *     Abbrechen laufen über den vorhandenen TimerActionReceiver.
 */
class AcWidgetSleepProvider : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        renderAll(ctx, mgr, ids)
    }

    companion object {
        fun renderAll(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            for (id in ids) mgr.updateAppWidget(id, build(ctx))
        }

        private fun build(ctx: Context): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_sleep)
            val triggerAt = SleepTimerScheduler.triggerAt(ctx)
            val running = triggerAt != null && triggerAt > System.currentTimeMillis()

            if (running) {
                // EN: base in the future + count-down mode → the Chronometer shows the remaining time and
                //     ticks it down on its own. DE: base in der Zukunft + Countdown-Modus → der Chronometer
                //     zeigt die Restzeit und zählt sie selbst herunter.
                val remaining = triggerAt!! - System.currentTimeMillis()
                v.setChronometer(R.id.sleep_chrono, SystemClock.elapsedRealtime() + remaining, null, true)
                v.setChronometerCountDown(R.id.sleep_chrono, true)
                v.setViewVisibility(R.id.sleep_chrono, android.view.View.VISIBLE)
                v.setViewVisibility(R.id.sleep_idle, android.view.View.GONE)
                v.setViewVisibility(R.id.sleep_sub, android.view.View.VISIBLE)
            } else {
                v.setViewVisibility(R.id.sleep_chrono, android.view.View.GONE)
                v.setViewVisibility(R.id.sleep_idle, android.view.View.VISIBLE)
                v.setViewVisibility(R.id.sleep_sub, android.view.View.GONE)
            }

            // EN: Presets reuse the all-in-one widget's sleep action (arms the offline alarm). DE: Die Presets nutzen die Sleep-Aktion des Alles-Widgets (stellt den Offline-Alarm scharf).
            v.setOnClickPendingIntent(R.id.sleep_p30, sleepArm(ctx, 30, 81))
            v.setOnClickPendingIntent(R.id.sleep_p60, sleepArm(ctx, 60, 82))
            v.setOnClickPendingIntent(R.id.sleep_p120, sleepArm(ctx, 120, 83))
            v.setOnClickPendingIntent(R.id.sleep_p240, sleepArm(ctx, 240, 84))
            v.setOnClickPendingIntent(R.id.sleep_off_now, timerAction(ctx, TimerActionReceiver.ACTION_OFF_NOW, 85))
            v.setOnClickPendingIntent(R.id.sleep_cancel, timerAction(ctx, TimerActionReceiver.ACTION_CANCEL, 86))
            return v
        }

        private fun sleepArm(ctx: Context, minutes: Int, req: Int): PendingIntent {
            val intent = Intent(ctx, AcWidgetProvider::class.java)
                .setAction(AcWidgetProvider.ACTION_SLEEP)
                .putExtra(AcWidgetProvider.EXTRA_SLEEP, minutes)
            return PendingIntent.getBroadcast(ctx, req, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun timerAction(ctx: Context, action: String, req: Int): PendingIntent {
            val intent = Intent(ctx, TimerActionReceiver::class.java).setAction(action)
            return PendingIntent.getBroadcast(ctx, req, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
