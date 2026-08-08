package com.climapilot.free.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.climapilot.free.MainActivity
import com.climapilot.free.R
import com.climapilot.free.SettingsRepo
import com.climapilot.free.SleepTimerScheduler
import com.climapilot.free.TokenRepo
import com.climapilot.free.midea.MideaAcSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * EN: All-in-one home-screen widget (Maxi design): power, target temperature, mode, fan speed,
 *     turbo/eco/swing and sleep-timer quick actions. Every command reuses the app's local LAN protocol
 *     with the cached token, so the widget controls the AC fully OFFLINE. Text comes from string
 *     resources, so the widget follows the device language.
 * DE: Alles-in-einem-Homescreen-Widget (Maxi-Design): Ein/Aus, Solltemperatur, Modus, Lüfterstufe,
 *     Turbo/Eco/Swing und Sleep-Timer-Schnellaktionen. Jeder Befehl nutzt das lokale LAN-Protokoll der
 *     App mit dem gecachten Token, das Widget steuert die Klima also vollständig OFFLINE. Texte stammen
 *     aus String-Ressourcen, das Widget folgt der Gerätesprache.
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
            ACTION_MODE -> cycleMode(ctx)
            ACTION_SET_MODE -> setModeDirect(ctx, intent.getIntExtra(EXTRA_MODE, 2))
            ACTION_SET_FAN -> setFanDirect(ctx, intent.getIntExtra(EXTRA_FAN, 60))
            ACTION_TURBO -> toggleOption(ctx, Option.TURBO)
            ACTION_ECO -> toggleOption(ctx, Option.ECO)
            ACTION_SWING -> toggleOption(ctx, Option.SWING)
            ACTION_SLEEP -> startSleep(ctx, intent.getIntExtra(EXTRA_SLEEP, 30))
        }
    }

    private enum class Action { POWER, UP, DOWN }
    private enum class Option { TURBO, ECO, SWING }

    /**
     * EN: Pull the unit's current state into the session before changing a single field. connect() does
     *     not read state, and every command sends the *whole* frame — so any field left unset goes out as
     *     a session default. Without this, a widget tap that only meant one thing reset the rest.
     * DE: Den aktuellen Gerätezustand in die Sitzung übernehmen, bevor ein einzelnes Feld geändert wird.
     *     connect() liest keinen Zustand, und jeder Befehl sendet den *ganzen* Frame — jedes nicht
     *     gesetzte Feld geht als Sitzungs-Vorgabe hinaus. Ohne das setzt ein Widget-Tipp den Rest zurück.
     */
    private suspend fun MideaAcSession.adoptCurrentState() {
        val s = queryState() ?: return
        powerOn = s.powerOn
        s.mode.takeIf { it in 1..5 }?.let { mode = it }
        tempC = s.targetTemp
        s.fanSpeed.takeIf { it in 1..102 }?.let { fan = it }
        swing = if (s.swingOn) 0x3F else 0
        eco = s.eco
        anion = s.anion
        turbo = s.turbo
    }

    /** EN: Run [mutate] on a freshly adopted session, then apply — the shared offline command path. DE: [mutate] auf einer frisch übernommenen Sitzung ausführen, dann anwenden — der geteilte Offline-Befehlspfad. */
    private fun withDevice(ctx: Context, mutate: suspend MideaAcSession.() -> Unit) {
        val device = WidgetRepo.load(ctx).device ?: run {
            ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val session = MideaAcSession(device, cachedCreds = TokenRepo.load(ctx, device.id))
                session.connect()
                session.adoptCurrentState()
                session.mutate()
                session.close()
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }

    private fun setModeDirect(ctx: Context, mode: Int) {
        WidgetRepo.updateMode(ctx, mode)
        WidgetRepo.updatePowerTarget(ctx, true, WidgetRepo.load(ctx).targetTemp)
        withDevice(ctx) { powerOn = true; this.mode = mode; apply() }
    }

    /** EN: Cycle Auto→Cool→Dry→Heat→Fan→Auto (used by the QS tile and the watch). DE: Auto→Kühlen→Trocknen→Heizen→Lüften→Auto durchschalten (für QS-Kachel und Uhr). */
    private fun cycleMode(ctx: Context) {
        val cur = WidgetRepo.load(ctx).mode
        setModeDirect(ctx, if (cur in 1..4) cur + 1 else 1)
    }

    private fun setFanDirect(ctx: Context, fan: Int) {
        // EN: A fan choice ends turbo (the unit ignores fan changes while boost is on). DE: Eine Lüfterwahl beendet Turbo (bei aktivem Boost ignoriert das Gerät Lüfteränderungen).
        WidgetRepo.updateFanTurbo(ctx, fan, false)
        withDevice(ctx) { turbo = false; this.fan = fan.coerceIn(1, 102); apply() }
    }

    private fun toggleOption(ctx: Context, opt: Option) {
        val snap = WidgetRepo.load(ctx)
        when (opt) {
            Option.TURBO -> {
                val v = !snap.turbo
                WidgetRepo.updateFanTurbo(ctx, snap.fan, v)
                withDevice(ctx) { turbo = v; apply() }
            }
            Option.ECO -> {
                val v = !snap.eco
                WidgetRepo.updateOption(ctx, v, snap.swing)
                // EN: Eco needs a setpoint of >= 24 °C, mirroring the app. DE: Eco braucht Soll >= 24 °C, wie in der App.
                withDevice(ctx) { eco = v; if (v && tempC < 24.0) tempC = 24.0; apply() }
            }
            Option.SWING -> {
                val v = !snap.swing
                WidgetRepo.updateOption(ctx, snap.eco, v)
                withDevice(ctx) { swing = if (v) 0x3F else 0; apply() }
            }
        }
    }

    /**
     * EN: Arm the sleep timer straight from the widget. This only schedules an exact alarm (no LAN
     *     command now); the existing SleepTimerScheduler + receiver switch the unit off offline later.
     * DE: Den Sleep-Timer direkt vom Widget scharfstellen. Es wird nur ein exakter Alarm geplant (kein
     *     LAN-Befehl jetzt); der vorhandene SleepTimerScheduler + Receiver schalten die Anlage später
     *     offline aus.
     */
    private fun startSleep(ctx: Context, minutes: Int) {
        val device = WidgetRepo.load(ctx).device ?: run {
            ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        SleepTimerScheduler.schedule(ctx, device.id, System.currentTimeMillis() + minutes * 60_000L)
        val mgr = AppWidgetManager.getInstance(ctx)
        renderAll(ctx, mgr, mgr.getAppWidgetIds(android.content.ComponentName(ctx, AcWidgetProvider::class.java)))
        AcWidgetSleepProvider.renderAll(ctx, mgr, mgr.getAppWidgetIds(android.content.ComponentName(ctx, AcWidgetSleepProvider::class.java)))
    }

    private fun control(ctx: Context, action: Action) {
        val snap = WidgetRepo.load(ctx)
        if (snap.device == null) {
            ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val newPower = if (action == Action.POWER) !snap.powerOn else snap.powerOn
        val base = snap.targetTemp.roundToInt().toDouble()
        val newTarget = when (action) {
            Action.UP -> (base + 1.0).coerceIn(16.0, 30.0)
            Action.DOWN -> (base - 1.0).coerceIn(16.0, 30.0)
            else -> base
        }
        WidgetRepo.updatePowerTarget(ctx, newPower, newTarget)
        withDevice(ctx) {
            when (action) {
                Action.POWER -> setPower(newPower)
                Action.UP, Action.DOWN -> {
                    // EN: Widget shows the room temperature; the unit gets it calibration-shifted like the app.
                    // DE: Das Widget zeigt die Raumtemperatur; das Gerät bekommt sie kalibriert wie in der App.
                    val shift = SettingsRepo.indoorOffset(ctx, device.id).roundToInt()
                    tempC = (newTarget - shift).coerceIn(16.0, 30.0)
                    apply()
                }
            }
        }
    }

    companion object {
        const val ACTION_POWER = "com.climapilot.free.widget.POWER"
        const val ACTION_TEMP_UP = "com.climapilot.free.widget.TEMP_UP"
        const val ACTION_TEMP_DOWN = "com.climapilot.free.widget.TEMP_DOWN"
        const val ACTION_MODE = "com.climapilot.free.widget.MODE"
        const val ACTION_SET_MODE = "com.climapilot.free.widget.SET_MODE"
        const val ACTION_SET_FAN = "com.climapilot.free.widget.SET_FAN"
        const val ACTION_TURBO = "com.climapilot.free.widget.TURBO"
        const val ACTION_ECO = "com.climapilot.free.widget.ECO"
        const val ACTION_SWING = "com.climapilot.free.widget.SWING"
        const val ACTION_SLEEP = "com.climapilot.free.widget.SLEEP"
        const val EXTRA_MODE = "mode"
        const val EXTRA_FAN = "fan"
        const val EXTRA_SLEEP = "sleep"

        fun renderAll(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val snap = WidgetRepo.load(ctx)
            for (id in ids) mgr.updateAppWidget(id, build(ctx, snap))
        }

        private val modeChips = listOf(
            R.id.mode_auto to 1, R.id.mode_cool to 2, R.id.mode_dry to 3,
            R.id.mode_heat to 4, R.id.mode_fan to 5,
        )
        // EN: Fan chip → protocol fan value (matches the app's FanPreset). DE: Lüfter-Chip → Protokoll-Lüfterwert (wie FanPreset der App).
        private val fanChips = listOf(
            R.id.fan_auto to 102, R.id.fan_min to 1, R.id.fan_silent to 20, R.id.fan_low to 40,
            R.id.fan_medium to 60, R.id.fan_high to 80, R.id.fan_turbo to 100,
        )

        private fun build(ctx: Context, snap: WidgetRepo.Snapshot): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_ac)
            val connected = snap.present && snap.device != null
            val on = connected && snap.powerOn

            v.setTextViewText(R.id.widget_name, snap.name)
            v.setTextViewText(R.id.widget_temp, formatTemp(snap.targetTemp))
            v.setInt(R.id.widget_dot, "setBackgroundResource",
                if (connected) R.drawable.widget_dot_on else R.drawable.widget_dot_off)

            // EN: Power pills — the active side gets the coloured fill. DE: Power-Pillen — die aktive Seite bekommt die farbige Füllung.
            v.setInt(R.id.btn_on, "setBackgroundResource", if (on) R.drawable.wdg_pill_on else R.drawable.wdg_pill_off)
            v.setInt(R.id.btn_off, "setBackgroundResource", if (!on) R.drawable.wdg_pill_on else R.drawable.wdg_pill_off)
            v.setTextColor(R.id.btn_on, if (on) 0xFFFFFFFF.toInt() else 0xFFAFC2C7.toInt())
            v.setTextColor(R.id.btn_off, if (!on) 0xFFFFFFFF.toInt() else 0xFFAFC2C7.toInt())

            // EN: Mode chips — highlight the active mode. DE: Modus-Chips — aktiven Modus hervorheben.
            val activeMode = snap.mode.takeIf { on }
            for ((id, mode) in modeChips) {
                val active = mode == activeMode
                v.setInt(id, "setBackgroundResource", if (active) R.drawable.wdg_chip_on else R.drawable.wdg_chip)
                v.setTextColor(id, if (active) 0xFF04262B.toInt() else 0xFFAFC2C7.toInt())
                v.setOnClickPendingIntent(id, broadcastExtra(ctx, ACTION_SET_MODE, EXTRA_MODE, mode, 10 + mode))
            }

            // EN: Fan chips — highlight the active preset; while turbo owns the fan, dim them all. DE: Lüfter-Chips — aktive Stufe hervorheben; solange Turbo den Lüfter bestimmt, alle dimmen.
            val fanLocked = on && snap.turbo
            for ((id, value) in fanChips) {
                val active = on && !snap.turbo && snap.fan == value
                v.setInt(id, "setBackgroundResource", if (active) R.drawable.wdg_chip_on else R.drawable.wdg_chip)
                v.setTextColor(id, when {
                    active -> 0xFF04262B.toInt()
                    fanLocked -> 0x66AFC2C7.toInt()
                    else -> 0xFFAFC2C7.toInt()
                })
                v.setOnClickPendingIntent(id, broadcastExtra(ctx, ACTION_SET_FAN, EXTRA_FAN, value, 30 + (value % 111)))
            }

            // EN: Option chips — turbo/eco/swing highlight when on. DE: Options-Chips — Turbo/Eco/Swing hervorheben, wenn an.
            optionChip(ctx, v, R.id.opt_turbo, on && snap.turbo, ACTION_TURBO, 61)
            optionChip(ctx, v, R.id.opt_eco, on && snap.eco, ACTION_ECO, 62)
            optionChip(ctx, v, R.id.opt_swing, on && snap.swing, ACTION_SWING, 63)

            // EN: Sleep-timer quick presets. DE: Sleep-Timer-Schnellwahl.
            v.setOnClickPendingIntent(R.id.sleep_30, broadcastExtra(ctx, ACTION_SLEEP, EXTRA_SLEEP, 30, 71))
            v.setOnClickPendingIntent(R.id.sleep_60, broadcastExtra(ctx, ACTION_SLEEP, EXTRA_SLEEP, 60, 72))
            v.setOnClickPendingIntent(R.id.sleep_120, broadcastExtra(ctx, ACTION_SLEEP, EXTRA_SLEEP, 120, 73))

            // EN: Status line — indoor · outdoor · power, or a not-connected / off hint. DE: Statuszeile — Innen · Außen · Leistung, oder Nicht-verbunden/Aus-Hinweis.
            v.setTextViewText(R.id.widget_status, when {
                snap.device == null -> ctx.getString(R.string.widget_not_connected)
                !snap.powerOn -> ctx.getString(R.string.widget_off)
                else -> listOfNotNull(
                    snap.indoorTemp?.let { ctx.getString(R.string.widget_indoor, formatTemp(it)) },
                    snap.outdoorTemp?.let { ctx.getString(R.string.widget_outdoor, formatTemp(it)) },
                    snap.powerW?.let { "${it.roundToInt()} W" },
                ).joinToString("  ·  ")
            })

            v.setOnClickPendingIntent(R.id.btn_on, broadcast(ctx, ACTION_POWER, 1))
            v.setOnClickPendingIntent(R.id.btn_off, broadcast(ctx, ACTION_POWER, 1))
            v.setOnClickPendingIntent(R.id.btn_minus, broadcast(ctx, ACTION_TEMP_DOWN, 2))
            v.setOnClickPendingIntent(R.id.btn_plus, broadcast(ctx, ACTION_TEMP_UP, 3))
            v.setOnClickPendingIntent(R.id.widget_name, openApp(ctx))
            v.setOnClickPendingIntent(R.id.widget_temp, openApp(ctx))
            return v
        }

        private fun optionChip(ctx: Context, v: RemoteViews, id: Int, active: Boolean, action: String, req: Int) {
            v.setInt(id, "setBackgroundResource", if (active) R.drawable.wdg_accent else R.drawable.wdg_chip)
            v.setTextColor(id, if (active) 0xFF5CE1EE.toInt() else 0xFFAFC2C7.toInt())
            v.setOnClickPendingIntent(id, broadcast(ctx, action, req))
        }

        internal fun modeNameRes(mode: Int): Int = when (mode) {
            1 -> R.string.mode_auto
            3 -> R.string.mode_dry
            4 -> R.string.mode_heat
            5 -> R.string.mode_fan
            else -> R.string.mode_cool
        }

        internal fun broadcast(ctx: Context, action: String, req: Int): PendingIntent {
            val intent = Intent(ctx, AcWidgetProvider::class.java).setAction(action)
            return PendingIntent.getBroadcast(ctx, req, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun broadcastExtra(ctx: Context, action: String, extraKey: String, value: Int, req: Int): PendingIntent {
            val intent = Intent(ctx, AcWidgetProvider::class.java).setAction(action).putExtra(extraKey, value)
            return PendingIntent.getBroadcast(ctx, req, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        /** EN: Kept for the small mode widget, which selects a specific mode. DE: Für das kleine Modus-Widget, das einen bestimmten Modus wählt. */
        internal fun broadcastSetMode(ctx: Context, mode: Int): PendingIntent =
            broadcastExtra(ctx, ACTION_SET_MODE, EXTRA_MODE, mode, 10 + mode)

        internal fun openApp(ctx: Context): PendingIntent {
            val intent = Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(ctx, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        internal fun formatTemp(t: Double): String =
            if (t % 1.0 == 0.0) "${t.toInt()}°" else "%.1f°".format(t)
    }
}
