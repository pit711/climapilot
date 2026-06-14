package com.climapilot.free.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.climapilot.free.R

/**
 * EN: Power-only home-screen widget. Shows the on/off state; tapping the button (or the whole tile)
 *     toggles power offline via [AcWidgetProvider]'s central control path.
 * DE: Reines Ein/Aus-Homescreen-Widget. Zeigt den An/Aus-Zustand; ein Tippen auf den Knopf (oder die
 *     ganze Kachel) schaltet die Klima offline über den zentralen Steuer-Pfad von [AcWidgetProvider].
 */
class AcWidgetPowerProvider : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        renderAll(ctx, mgr, ids)
    }

    companion object {
        fun renderAll(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val snap = WidgetRepo.load(ctx)
            for (id in ids) mgr.updateAppWidget(id, build(ctx, snap))
        }

        private fun build(ctx: Context, snap: WidgetRepo.Snapshot): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_power)
            val status = when {
                !snap.present -> ctx.getString(R.string.widget_not_connected)
                snap.powerOn -> ctx.getString(R.string.widget_on)
                else -> ctx.getString(R.string.widget_off)
            }
            v.setTextViewText(R.id.widget_status, status)
            val power = AcWidgetProvider.broadcast(ctx, AcWidgetProvider.ACTION_POWER, 1)
            v.setOnClickPendingIntent(R.id.btn_power, power)
            v.setOnClickPendingIntent(R.id.widget_root, power)
            return v
        }
    }
}
