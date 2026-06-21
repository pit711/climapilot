package com.climapilot.free

import android.content.Context
import com.climapilot.free.midea.MideaAc

/**
 * EN: Persists the IR-remote mode's assumed state so the app behaves like a real remote that
 *     "remembers" what it last sent. IR is one-way (no readback), so this cached state is what the
 *     control screen shows when re-entering IR mode or after an app restart. Stored in the app's
 *     private SharedPreferences.
 * DE: Speichert den angenommenen Zustand des IR-Fernbedienungs-Modus, damit sich die App wie eine
 *     echte Fernbedienung verhält, die sich das zuletzt Gesendete „merkt". IR ist einweg (kein
 *     Readback), daher ist dieser gemerkte Zustand das, was der Steuer-Bildschirm beim erneuten
 *     Betreten des IR-Modus oder nach einem App-Neustart zeigt. Liegt in den privaten
 *     SharedPreferences der App.
 */
object IrStateRepo {
    private const val PREFS = "climapilot_ir"
    private const val K_POWER = "power"
    private const val K_MODE = "mode"
    private const val K_TEMP = "temp"
    private const val K_FAN = "fan"
    private const val K_QUIET = "quiet"
    private const val K_TURBO = "turbo"
    private const val K_ECONO = "econo"
    private const val K_SWING = "swing"

    /** EN: The full assumed IR state. DE: Der vollständige angenommene IR-Zustand. */
    data class IrState(
        val powerOn: Boolean, val mode: Int, val tempC: Double, val fan: Int,
        val quiet: Boolean, val turbo: Boolean, val econo: Boolean, val swing: Boolean,
    )

    /** EN: Default assumed state used on the very first IR-mode entry. DE: Angenommener Standardzustand beim allerersten Betreten des IR-Modus. */
    val DEFAULT = IrState(
        powerOn = true, mode = MideaAc.MODE_COOL, tempC = 24.0, fan = 102,
        quiet = false, turbo = false, econo = false, swing = false,
    )

    fun load(ctx: Context): IrState {
        val p = prefs(ctx)
        return IrState(
            powerOn = p.getBoolean(K_POWER, DEFAULT.powerOn),
            mode = p.getInt(K_MODE, DEFAULT.mode),
            tempC = p.getFloat(K_TEMP, DEFAULT.tempC.toFloat()).toDouble(),
            fan = p.getInt(K_FAN, DEFAULT.fan),
            quiet = p.getBoolean(K_QUIET, DEFAULT.quiet),
            turbo = p.getBoolean(K_TURBO, DEFAULT.turbo),
            econo = p.getBoolean(K_ECONO, DEFAULT.econo),
            swing = p.getBoolean(K_SWING, DEFAULT.swing),
        )
    }

    fun save(ctx: Context, s: IrState) {
        prefs(ctx).edit()
            .putBoolean(K_POWER, s.powerOn)
            .putInt(K_MODE, s.mode)
            .putFloat(K_TEMP, s.tempC.toFloat())
            .putInt(K_FAN, s.fan)
            .putBoolean(K_QUIET, s.quiet)
            .putBoolean(K_TURBO, s.turbo)
            .putBoolean(K_ECONO, s.econo)
            .putBoolean(K_SWING, s.swing)
            .apply()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
