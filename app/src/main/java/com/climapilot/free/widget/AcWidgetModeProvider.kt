package com.climapilot.free.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.climapilot.free.R

/**
 * EN: Mode-only home-screen widget. Shows the current operating mode; tapping the button (or the whole
 *     tile) cycles to the next mode offline via [AcWidgetProvider]'s central control path.
 * DE: Reines Modus-Homescreen-Widget. Zeigt den aktuellen Betriebsmodus; ein Tippen auf den Knopf (oder
 *     die ganze Kachel) schaltet offline zum nächsten Modus weiter (zentraler Steuer-Pfad von
 *     [AcWidgetProvider]).
 */
class AcWidgetModeProvider : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        renderAll(ctx, mgr, ids)
    }

    companion object {
        fun renderAll(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val snap = WidgetRepo.load(ctx)
            for (id in ids) mgr.updateAppWidget(id, build(ctx, snap))
        }

        // EN: Chip view id paired with the protocol mode value it selects. DE: Chip-View-ID mit dem Protokoll-Moduswert, den sie wählt.
        private val chips = listOf(
            R.id.mode_auto to 1,   // MODE_AUTO
            R.id.mode_cool to 2,   // MODE_COOL
            R.id.mode_dry to 3,    // MODE_DRY
            R.id.mode_heat to 4,   // MODE_HEAT
            R.id.mode_fan to 5,    // MODE_FAN
        )

        private fun build(ctx: Context, snap: WidgetRepo.Snapshot): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_mode)
            // EN: Title shows the current mode (or connection/off state). DE: Titel zeigt den aktuellen Modus (oder Verbindungs-/Aus-Zustand).
            val title = when {
                !snap.present -> ctx.getString(R.string.widget_not_connected)
                !snap.powerOn -> ctx.getString(R.string.widget_off)
                else -> ctx.getString(AcWidgetProvider.modeNameRes(snap.mode))
            }
            v.setTextViewText(R.id.widget_status, title)
            // EN: Highlight the active mode chip; each chip selects its mode directly. DE: Aktiven Modus-Chip hervorheben; jeder Chip wählt seinen Modus direkt.
            val activeMode = snap.mode.takeIf { snap.present && snap.powerOn }
            for ((id, mode) in chips) {
                v.setInt(
                    id, "setBackgroundResource",
                    if (mode == activeMode) R.drawable.widget_chip_active else R.drawable.widget_chip_bg,
                )
                v.setOnClickPendingIntent(id, AcWidgetProvider.broadcastSetMode(ctx, mode))
            }
            return v
        }
    }
}
