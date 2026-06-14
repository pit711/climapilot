package com.climapilot.free.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.climapilot.free.MainActivity
import com.climapilot.free.R
import com.climapilot.free.TokenRepo
import com.climapilot.free.midea.MideaAcSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * EN: Home-screen widget: shows the AC's target/indoor temperature and power state, with quick
 *     actions for power and ±0.5 °C. Commands reuse the same local LAN protocol as the app and use the
 *     cached token, so the widget controls the AC fully OFFLINE (no internet needed). All text is
 *     resolved from string resources, so the widget follows the device language.
 * DE: Homescreen-Widget: zeigt Soll-/Innentemperatur und Ein-Aus-Zustand der Klima, mit Schnellaktionen
 *     für Ein/Aus und ±0,5 °C. Die Befehle nutzen dasselbe lokale LAN-Protokoll wie die App und das
 *     gecachte Token, sodass das Widget die Klima vollständig OFFLINE steuert (kein Internet nötig).
 *     Alle Texte stammen aus String-Ressourcen, das Widget folgt also der Gerätesprache.
 */
class AcWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        renderAll(ctx, mgr, ids)
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        when (intent.action) {
            ACTION_POWER -> control(ctx, Action.POWER)
            ACTION_TEMP_UP -> control(ctx, Action.UP)
            ACTION_TEMP_DOWN -> control(ctx, Action.DOWN)
            ACTION_MODE -> control(ctx, Action.MODE)
        }
    }

    private enum class Action { POWER, UP, DOWN, MODE }

    private fun control(ctx: Context, action: Action) {
        val snap = WidgetRepo.load(ctx)
        val device = snap.device ?: run {
            // EN: No controllable device saved yet — open the app instead. DE: Noch kein steuerbares Gerät gespeichert — stattdessen die App öffnen.
            ctx.startActivity(
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        // EN: Optimistically update the widget first for snappy feedback. DE: Das Widget zuerst optimistisch aktualisieren für flotte Rückmeldung.
        val newPower = if (action == Action.POWER) !snap.powerOn else snap.powerOn
        val newTarget = when (action) {
            Action.UP -> (snap.targetTemp + 0.5).coerceIn(16.0, 30.0)
            Action.DOWN -> (snap.targetTemp - 0.5).coerceIn(16.0, 30.0)
            else -> snap.targetTemp
        }
        // EN: Cycle Auto→Cool→Dry→Heat→Fan→Auto. DE: Auto→Kühlen→Trocknen→Heizen→Lüften→Auto durchschalten.
        val newMode = if (action == Action.MODE) (if (snap.mode in 1..4) snap.mode + 1 else 1) else snap.mode
        when (action) {
            Action.MODE -> WidgetRepo.updateMode(ctx, newMode)
            else -> WidgetRepo.updatePowerTarget(ctx, newPower, newTarget)
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // EN: Cached token → connect offline, no cloud. DE: Gecachtes Token → offline verbinden, ohne Cloud.
                val session = MideaAcSession(device, cachedCreds = TokenRepo.load(ctx, device.id))
                session.connect()
                when (action) {
                    Action.POWER -> session.setPower(newPower)
                    Action.UP, Action.DOWN -> { session.tempC = newTarget; session.apply() }
                    Action.MODE -> session.setMode(newMode)
                }
                session.close()
            } catch (_: Exception) {
                // EN: Best-effort: keep the optimistic widget state; the app reconciles on next open.
                // DE: Best-Effort: optimistischen Widget-Zustand behalten; die App gleicht beim nächsten Öffnen ab.
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_POWER = "com.climapilot.free.widget.POWER"
        const val ACTION_TEMP_UP = "com.climapilot.free.widget.TEMP_UP"
        const val ACTION_TEMP_DOWN = "com.climapilot.free.widget.TEMP_DOWN"
        const val ACTION_MODE = "com.climapilot.free.widget.MODE"

        fun renderAll(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val snap = WidgetRepo.load(ctx)
            for (id in ids) mgr.updateAppWidget(id, build(ctx, snap, id))
        }

        private fun build(ctx: Context, snap: WidgetRepo.Snapshot, widgetId: Int): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_ac)
            v.setTextViewText(R.id.widget_name, snap.name)
            v.setTextViewText(R.id.widget_temp, formatTemp(snap.targetTemp))

            val status = when {
                !snap.present -> ctx.getString(R.string.widget_not_connected)
                !snap.powerOn -> ctx.getString(R.string.widget_off)
                else -> {
                    // EN: Show the current mode name first, then indoor temp + power. DE: Erst den aktuellen Modus-Namen, dann Innentemperatur + Leistung.
                    val modeName = ctx.getString(modeNameRes(snap.mode))
                    val indoor = snap.indoorTemp?.let {
                        ctx.getString(R.string.widget_indoor, formatTemp(it))
                    }
                    val power = snap.powerW?.let { "${it.roundToInt()} W" }
                    listOfNotNull(modeName, indoor, power).joinToString("  ·  ")
                }
            }
            v.setTextViewText(R.id.widget_status, status)

            v.setOnClickPendingIntent(R.id.btn_power, broadcast(ctx, ACTION_POWER, 1))
            v.setOnClickPendingIntent(R.id.btn_minus, broadcast(ctx, ACTION_TEMP_DOWN, 2))
            v.setOnClickPendingIntent(R.id.btn_plus, broadcast(ctx, ACTION_TEMP_UP, 3))
            v.setOnClickPendingIntent(R.id.btn_mode, broadcast(ctx, ACTION_MODE, 4))
            v.setOnClickPendingIntent(R.id.widget_root, openApp(ctx))
            return v
        }

        /**
         * EN: Map an operating mode to its display string. Shared with the 1×1/2×2 widgets.
         * DE: Einen Betriebsmodus auf seinen Anzeige-String abbilden. Mit den 1×1/2×2-Widgets geteilt.
         */
        internal fun modeNameRes(mode: Int): Int = when (mode) {
            1 -> R.string.mode_auto
            3 -> R.string.mode_dry
            4 -> R.string.mode_heat
            5 -> R.string.mode_fan
            else -> R.string.mode_cool
        }

        /**
         * EN: A PendingIntent broadcasting a control action to THIS provider, which performs the
         *     actual LAN command centrally — so the 1×1/2×2 widgets reuse the exact same control path.
         * DE: Ein PendingIntent, der eine Steueraktion an DIESEN Provider sendet, der den eigentlichen
         *     LAN-Befehl zentral ausführt — die 1×1/2×2-Widgets nutzen so denselben Steuer-Pfad.
         */
        internal fun broadcast(ctx: Context, action: String, req: Int): PendingIntent {
            val intent = Intent(ctx, AcWidgetProvider::class.java).setAction(action)
            return PendingIntent.getBroadcast(
                ctx, req, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        internal fun openApp(ctx: Context): PendingIntent {
            val intent = Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(
                ctx, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        internal fun formatTemp(t: Double): String =
            if (t % 1.0 == 0.0) "${t.toInt()}°" else "%.1f°".format(t)
    }
}
