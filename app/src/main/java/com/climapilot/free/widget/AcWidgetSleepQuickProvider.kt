package com.climapilot.free.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.climapilot.free.R
import com.climapilot.free.SleepTimerScheduler

/**
 * EN: One-tap sleep-timer tiles, 1x1, one fixed duration each. Tapping arms the offline sleep timer for
 *     that duration; while any timer runs every tile shows the remaining time (a Chronometer that ticks
 *     itself, no battery cost). Four thin subclasses exist because Android needs one provider per widget
 *     type in the picker — the whole behaviour lives here.
 * DE: Sleep-Timer-Kacheln zum Antippen, 1x1, je eine feste Dauer. Ein Tipp stellt den Offline-Sleep-Timer
 *     auf diese Dauer; solange ein Timer läuft, zeigt jede Kachel die Restzeit (ein Chronometer, der sich
 *     selbst weiterzählt, ohne Akku-Kosten). Die vier schlanken Unterklassen gibt es, weil Android je
 *     Widget-Typ in der Auswahl einen eigenen Provider braucht — das Verhalten steckt komplett hier.
 */
abstract class AcWidgetSleepQuickProvider : AppWidgetProvider() {

    /** EN: Duration this tile arms, in minutes. DE: Dauer, die diese Kachel scharfstellt, in Minuten. */
    protected abstract val minutes: Int

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) mgr.updateAppWidget(id, build(ctx, minutes))
    }

    companion object {
        /** EN: All four tile providers — used to refresh them together. DE: Alle vier Kachel-Provider — um sie gemeinsam zu aktualisieren. */
        private val tiles = listOf(
            AcWidgetSleep30Provider::class.java to 30,
            AcWidgetSleep60Provider::class.java to 60,
            AcWidgetSleep120Provider::class.java to 120,
            AcWidgetSleep240Provider::class.java to 240,
            AcWidgetSleep480Provider::class.java to 480,
            AcWidgetSleep720Provider::class.java to 720,
        )

        /** EN: Re-render every placed quick tile. DE: Jede platzierte Schnell-Kachel neu rendern. */
        fun renderAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            for ((cls, mins) in tiles) {
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, cls))
                for (id in ids) mgr.updateAppWidget(id, build(ctx, mins))
            }
        }

        private fun build(ctx: Context, minutes: Int): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_sleep_quick)
            val triggerAt = SleepTimerScheduler.triggerAt(ctx)
            val running = triggerAt != null && triggerAt > System.currentTimeMillis()

            // EN: Label: 30 min stays in minutes, the rest reads in hours. DE: Beschriftung: 30 Min bleibt in Minuten, der Rest in Stunden.
            if (minutes < 60) {
                v.setTextViewText(R.id.quick_value, minutes.toString())
                v.setTextViewText(R.id.quick_unit, ctx.getString(R.string.widget_sleep_unit_min))
            } else {
                v.setTextViewText(R.id.quick_value, (minutes / 60).toString())
                v.setTextViewText(R.id.quick_unit, ctx.getString(R.string.widget_sleep_unit_hour))
            }

            if (running) {
                val remaining = triggerAt!! - System.currentTimeMillis()
                v.setChronometer(R.id.quick_chrono, SystemClock.elapsedRealtime() + remaining, null, true)
                v.setChronometerCountDown(R.id.quick_chrono, true)
                v.setViewVisibility(R.id.quick_chrono, View.VISIBLE)
                v.setTextColor(R.id.quick_value, 0xFF5CE1EE.toInt())
            } else {
                v.setViewVisibility(R.id.quick_chrono, View.GONE)
                v.setTextColor(R.id.quick_value, 0xFFFFFFFF.toInt())
            }

            // EN: Reuse the all-in-one widget's sleep action; a distinct request code per duration keeps
            //     the extra from collapsing onto one shared PendingIntent.
            // DE: Die Sleep-Aktion des Alles-Widgets wiederverwenden; ein eigener Request-Code je Dauer
            //     verhindert, dass das Extra auf einen geteilten PendingIntent zusammenfällt.
            val intent = Intent(ctx, AcWidgetProvider::class.java)
                .setAction(AcWidgetProvider.ACTION_SLEEP)
                .putExtra(AcWidgetProvider.EXTRA_SLEEP, minutes)
            val pi = PendingIntent.getBroadcast(
                ctx, 900 + minutes, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            v.setOnClickPendingIntent(R.id.quick_root, pi)
            return v
        }
    }
}

/** EN: 30-minute sleep tile. DE: 30-Minuten-Sleep-Kachel. */
class AcWidgetSleep30Provider : AcWidgetSleepQuickProvider() { override val minutes = 30 }

/** EN: 1-hour sleep tile. DE: 1-Stunden-Sleep-Kachel. */
class AcWidgetSleep60Provider : AcWidgetSleepQuickProvider() { override val minutes = 60 }

/** EN: 2-hour sleep tile. DE: 2-Stunden-Sleep-Kachel. */
class AcWidgetSleep120Provider : AcWidgetSleepQuickProvider() { override val minutes = 120 }

/** EN: 4-hour sleep tile. DE: 4-Stunden-Sleep-Kachel. */
class AcWidgetSleep240Provider : AcWidgetSleepQuickProvider() { override val minutes = 240 }

/** EN: 8-hour sleep tile. DE: 8-Stunden-Sleep-Kachel. */
class AcWidgetSleep480Provider : AcWidgetSleepQuickProvider() { override val minutes = 480 }

/** EN: 12-hour sleep tile. DE: 12-Stunden-Sleep-Kachel. */
class AcWidgetSleep720Provider : AcWidgetSleepQuickProvider() { override val minutes = 720 }
