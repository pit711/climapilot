package com.climapilot.free.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.climapilot.free.R

/**
 * EN: 1×1 home-screen widget — the smallest size. Shows the target temperature and the mode/power
 *     state; tapping it toggles power (offline, via [AcWidgetProvider]'s central control path).
 * DE: 1×1-Homescreen-Widget — die kleinste Größe. Zeigt Zieltemperatur und Modus/Ein-Aus-Zustand;
 *     Tippen schaltet Ein/Aus (offline, über den zentralen Steuer-Pfad von [AcWidgetProvider]).
 */
class AcWidget1x1Provider : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        renderAll(ctx, mgr, ids)
    }

    companion object {
        fun renderAll(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val snap = WidgetRepo.load(ctx)
            for (id in ids) mgr.updateAppWidget(id, build(ctx, snap))
        }

        private fun build(ctx: Context, snap: WidgetRepo.Snapshot): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_ac_1x1)
            v.setTextViewText(R.id.widget_temp, AcWidgetProvider.formatTemp(snap.targetTemp))
            val status = when {
                !snap.present -> "–"
                !snap.powerOn -> ctx.getString(R.string.widget_off)
                else -> ctx.getString(AcWidgetProvider.modeNameRes(snap.mode))
            }
            v.setTextViewText(R.id.widget_status, status)
            // EN: Whole tile toggles power. DE: Die ganze Kachel schaltet Ein/Aus.
            v.setOnClickPendingIntent(R.id.widget_root, AcWidgetProvider.broadcast(ctx, AcWidgetProvider.ACTION_POWER, 1))
            return v
        }
    }
}
