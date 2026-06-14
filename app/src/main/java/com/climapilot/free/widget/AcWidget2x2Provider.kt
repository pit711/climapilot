package com.climapilot.free.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.climapilot.free.R
import kotlin.math.roundToInt

/**
 * EN: 2×2 home-screen widget — a compact version with name, target temperature, −/power/+/mode
 *     buttons and a status line. It reuses [AcWidgetProvider]'s central, offline control path: every
 *     button broadcasts an action to [AcWidgetProvider], which performs the LAN command and re-renders
 *     all widget sizes.
 * DE: 2×2-Homescreen-Widget — eine kompakte Variante mit Name, Zieltemperatur, −/Ein-Aus/+/Modus-Knöpfen
 *     und Statuszeile. Es nutzt den zentralen, offline-fähigen Steuer-Pfad von [AcWidgetProvider]:
 *     jeder Knopf sendet eine Aktion an [AcWidgetProvider], der den LAN-Befehl ausführt und alle
 *     Widget-Größen neu rendert.
 */
class AcWidget2x2Provider : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        renderAll(ctx, mgr, ids)
    }

    companion object {
        fun renderAll(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val snap = WidgetRepo.load(ctx)
            for (id in ids) mgr.updateAppWidget(id, build(ctx, snap))
        }

        private fun build(ctx: Context, snap: WidgetRepo.Snapshot): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_ac_2x2)
            v.setTextViewText(R.id.widget_name, snap.name)
            v.setTextViewText(R.id.widget_temp, AcWidgetProvider.formatTemp(snap.targetTemp))

            val status = when {
                !snap.present -> ctx.getString(R.string.widget_not_connected)
                !snap.powerOn -> ctx.getString(R.string.widget_off)
                else -> {
                    val modeName = ctx.getString(AcWidgetProvider.modeNameRes(snap.mode))
                    val power = snap.powerW?.let { "${it.roundToInt()} W" }
                    listOfNotNull(modeName, power).joinToString("  ·  ")
                }
            }
            v.setTextViewText(R.id.widget_status, status)

            v.setOnClickPendingIntent(R.id.btn_power, AcWidgetProvider.broadcast(ctx, AcWidgetProvider.ACTION_POWER, 1))
            v.setOnClickPendingIntent(R.id.btn_minus, AcWidgetProvider.broadcast(ctx, AcWidgetProvider.ACTION_TEMP_DOWN, 2))
            v.setOnClickPendingIntent(R.id.btn_plus, AcWidgetProvider.broadcast(ctx, AcWidgetProvider.ACTION_TEMP_UP, 3))
            v.setOnClickPendingIntent(R.id.btn_mode, AcWidgetProvider.broadcast(ctx, AcWidgetProvider.ACTION_MODE, 4))
            v.setOnClickPendingIntent(R.id.widget_root, AcWidgetProvider.openApp(ctx))
            return v
        }
    }
}
