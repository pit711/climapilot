package com.climapilot.free.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.climapilot.free.midea.MideaDevice

/**
 * EN: Tiny SharedPreferences-backed store the app uses to hand the latest AC state to the home-screen
 *     widget(s). It also keeps the connection coordinates so the widget can issue control commands on
 *     its own. No UI strings live here — the widget localises them at render time.
 * DE: Winziger, auf SharedPreferences basierender Speicher, mit dem die App den aktuellen Klima-Zustand
 *     an die Homescreen-Widgets übergibt. Er hält auch die Verbindungsdaten, damit das Widget selbst
 *     Steuerbefehle senden kann. Keine UI-Texte hier — das Widget lokalisiert sie beim Rendern.
 */
object WidgetRepo {
    private const val PREFS = "midea_widget"
    private const val K_PRESENT = "present"
    private const val K_NAME = "name"
    private const val K_POWER = "power"
    private const val K_MODE = "mode"
    private const val K_TARGET = "target"
    private const val K_INDOOR = "indoor"
    private const val K_POWERW = "powerW"
    private const val K_IP = "ip"
    private const val K_PORT = "port"
    private const val K_ID = "id"
    private const val K_VERSION = "version"

    data class Snapshot(
        val present: Boolean,
        val name: String,
        val powerOn: Boolean,
        val mode: Int,
        val targetTemp: Double,
        val indoorTemp: Double?,
        val powerW: Double?,
        val device: MideaDevice?,
    )

    /** EN: Save the latest state + connection info and refresh any placed widgets. DE: Aktuellen Zustand + Verbindungsinfo speichern und platzierte Widgets aktualisieren. */
    fun publish(
        ctx: Context,
        present: Boolean,
        name: String,
        powerOn: Boolean,
        mode: Int,
        targetTemp: Double,
        indoorTemp: Double?,
        powerW: Double?,
        device: MideaDevice?,
    ) {
        // EN: Never persist the throw-away demo device as a controllable target. DE: Das Wegwerf-Demo-Gerät nie als steuerbares Ziel speichern.
        val controllable = device?.takeIf { it.ip != "Demo" && it.id != 0L }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putBoolean(K_PRESENT, present)
            putString(K_NAME, name)
            putBoolean(K_POWER, powerOn)
            putInt(K_MODE, mode)
            putFloat(K_TARGET, targetTemp.toFloat())
            if (indoorTemp != null) putFloat(K_INDOOR, indoorTemp.toFloat()) else remove(K_INDOOR)
            if (powerW != null) putFloat(K_POWERW, powerW.toFloat()) else remove(K_POWERW)
            if (controllable != null) {
                putString(K_IP, controllable.ip)
                putInt(K_PORT, controllable.port)
                putLong(K_ID, controllable.id)
                putInt(K_VERSION, controllable.version)
            } else {
                remove(K_IP); remove(K_PORT); remove(K_ID); remove(K_VERSION)
            }
            apply()
        }
        notifyWidgets(ctx)
    }

    /** EN: Optimistically store a new power/target after the widget itself issued a command. DE: Neuen Ein-Aus/Soll-Wert optimistisch speichern, nachdem das Widget selbst einen Befehl gesendet hat. */
    fun updatePowerTarget(ctx: Context, powerOn: Boolean, targetTemp: Double) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(K_POWER, powerOn)
            .putFloat(K_TARGET, targetTemp.toFloat())
            .apply()
        notifyWidgets(ctx)
    }

    /** EN: Optimistically store a new mode after the widget cycled it. DE: Neuen Modus optimistisch speichern, nachdem das Widget ihn durchgeschaltet hat. */
    fun updateMode(ctx: Context, mode: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(K_MODE, mode).apply()
        notifyWidgets(ctx)
    }

    fun load(ctx: Context): Snapshot {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ip = p.getString(K_IP, null)
        val device = if (ip != null) MideaDevice(
            ip = ip, port = p.getInt(K_PORT, 6444), id = p.getLong(K_ID, 0),
            sn = "", name = p.getString(K_NAME, "Midea") ?: "Midea",
            type = 0xAC, version = p.getInt(K_VERSION, 3),
        ) else null
        return Snapshot(
            present = p.getBoolean(K_PRESENT, false),
            name = p.getString(K_NAME, "Midea") ?: "Midea",
            powerOn = p.getBoolean(K_POWER, false),
            mode = p.getInt(K_MODE, 2),
            targetTemp = p.getFloat(K_TARGET, 24f).toDouble(),
            indoorTemp = if (p.contains(K_INDOOR)) p.getFloat(K_INDOOR, 0f).toDouble() else null,
            powerW = if (p.contains(K_POWERW)) p.getFloat(K_POWERW, 0f).toDouble() else null,
            device = device,
        )
    }

    private fun notifyWidgets(ctx: Context) {
        // EN: Re-render every widget size that is currently placed. DE: Jede aktuell platzierte Widget-Größe neu rendern.
        val mgr = AppWidgetManager.getInstance(ctx)
        mgr.getAppWidgetIds(ComponentName(ctx, AcWidgetProvider::class.java))
            .takeIf { it.isNotEmpty() }?.let { AcWidgetProvider.renderAll(ctx, mgr, it) }
        mgr.getAppWidgetIds(ComponentName(ctx, AcWidget2x2Provider::class.java))
            .takeIf { it.isNotEmpty() }?.let { AcWidget2x2Provider.renderAll(ctx, mgr, it) }
        mgr.getAppWidgetIds(ComponentName(ctx, AcWidget1x1Provider::class.java))
            .takeIf { it.isNotEmpty() }?.let { AcWidget1x1Provider.renderAll(ctx, mgr, it) }
    }
}
