package com.climapilot.free.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.climapilot.free.R

/**
 * EN: Temperature-only home-screen widget. Shows the target temperature with − / + buttons that nudge
 *     the setpoint by 0.5 °C offline via [AcWidgetProvider]'s central control path. Tapping the number
 *     opens the app.
 * DE: Reines Temperatur-Homescreen-Widget. Zeigt die Zieltemperatur mit −/+-Knöpfen, die den Sollwert
 *     offline um 0,5 °C ändern (zentraler Steuer-Pfad von [AcWidgetProvider]). Ein Tippen auf die Zahl
 *     öffnet die App.
 */
class AcWidgetTempProvider : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        renderAll(ctx, mgr, ids)
    }

    companion object {
        fun renderAll(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val snap = WidgetRepo.load(ctx)
            for (id in ids) mgr.updateAppWidget(id, build(ctx, snap))
        }

        private fun build(ctx: Context, snap: WidgetRepo.Snapshot): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_temp)
            v.setTextViewText(R.id.widget_temp, AcWidgetProvider.formatTemp(snap.targetTemp))
            val status = when {
                !snap.present -> ctx.getString(R.string.widget_not_connected)
                !snap.powerOn -> ctx.getString(R.string.widget_off)
                else -> ctx.getString(AcWidgetProvider.modeNameRes(snap.mode))
            }
            v.setTextViewText(R.id.widget_status, status)
            v.setOnClickPendingIntent(R.id.btn_minus, AcWidgetProvider.broadcast(ctx, AcWidgetProvider.ACTION_TEMP_DOWN, 2))
            v.setOnClickPendingIntent(R.id.btn_plus, AcWidgetProvider.broadcast(ctx, AcWidgetProvider.ACTION_TEMP_UP, 3))
            v.setOnClickPendingIntent(R.id.widget_temp, AcWidgetProvider.openApp(ctx))
            return v
        }
    }
}
