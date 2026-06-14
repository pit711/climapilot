package com.climapilot.free

import android.content.Context

/**
 * EN: App-wide display preferences: temperature unit (°C/°F) and an optional electricity price per
 *     kWh used to estimate running cost. Stored in the app's private SharedPreferences.
 * DE: App-weite Anzeige-Einstellungen: Temperatureinheit (°C/°F) und ein optionaler Strompreis pro
 *     kWh zur Kostenschätzung. Liegt in den privaten SharedPreferences der App.
 */
object SettingsRepo {
    private const val PREFS = "climapilot_settings"
    private const val K_FAHRENHEIT = "fahrenheit"
    private const val K_PRICE = "price_per_kwh"
    private const val K_SLEEP_CUSTOM = "sleep_custom_minutes"

    /** EN: true = show temperatures in °F. DE: true = Temperaturen in °F anzeigen. */
    fun useFahrenheit(ctx: Context): Boolean = prefs(ctx).getBoolean(K_FAHRENHEIT, false)

    fun setUseFahrenheit(ctx: Context, value: Boolean) =
        prefs(ctx).edit().putBoolean(K_FAHRENHEIT, value).apply()

    /** EN: Price per kWh (0 = not set → no cost shown). DE: Preis pro kWh (0 = nicht gesetzt → keine Kosten). */
    fun pricePerKwh(ctx: Context): Double = prefs(ctx).getFloat(K_PRICE, 0f).toDouble()

    fun setPricePerKwh(ctx: Context, value: Double) =
        prefs(ctx).edit().putFloat(K_PRICE, value.toFloat()).apply()

    /** EN: Last custom sleep-timer duration (0 = none), kept as a quick chip. DE: Letzte eigene Sleep-Timer-Dauer (0 = keine), als Schnell-Chip gemerkt. */
    fun sleepCustomMinutes(ctx: Context): Int = prefs(ctx).getInt(K_SLEEP_CUSTOM, 0)

    fun setSleepCustomMinutes(ctx: Context, value: Int) =
        prefs(ctx).edit().putInt(K_SLEEP_CUSTOM, value).apply()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
