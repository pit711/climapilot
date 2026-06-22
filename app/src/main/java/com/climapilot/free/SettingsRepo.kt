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
    private const val K_MAX_RUNTIME = "max_runtime_hours"
    private const val K_APP_LOCK = "app_lock"
    private const val K_HISTORY = "history_enabled"
    private const val K_AUTO_UPDATE = "auto_update_check"
    private const val K_LAST_UPDATE_CHECK = "last_update_check"

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

    /** EN: Auto power-off after the AC has been running this many hours (0 = off). DE: Auto-Aus, nachdem die Klima so viele Stunden läuft (0 = aus). */
    fun maxRuntimeHours(ctx: Context): Int = prefs(ctx).getInt(K_MAX_RUNTIME, 0)

    fun setMaxRuntimeHours(ctx: Context, value: Int) =
        prefs(ctx).edit().putInt(K_MAX_RUNTIME, value).apply()

    /** EN: Require biometric/PIN unlock when opening the app. DE: Beim Öffnen der App per Biometrie/PIN entsperren verlangen. */
    fun appLock(ctx: Context): Boolean = prefs(ctx).getBoolean(K_APP_LOCK, false)

    fun setAppLock(ctx: Context, value: Boolean) =
        prefs(ctx).edit().putBoolean(K_APP_LOCK, value).apply()

    /** EN: Record the AC history + run the ~15 min background poll (off by default — opt-in). DE: Klima-Verlauf aufzeichnen + den ~15-min-Hintergrund-Poll laufen lassen (standardmäßig aus — Opt-in). */
    fun historyEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(K_HISTORY, false)

    fun setHistoryEnabled(ctx: Context, value: Boolean) =
        prefs(ctx).edit().putBoolean(K_HISTORY, value).apply()

    /** EN: Auto-check GitHub for a newer release on launch (GitHub/sideload build; on by default). DE: Beim Start automatisch auf GitHub nach einem neueren Release prüfen (GitHub-/Sideload-Build; standardmäßig an). */
    fun autoUpdateCheck(ctx: Context): Boolean = prefs(ctx).getBoolean(K_AUTO_UPDATE, true)

    fun setAutoUpdateCheck(ctx: Context, value: Boolean) =
        prefs(ctx).edit().putBoolean(K_AUTO_UPDATE, value).apply()

    /** EN: Epoch millis of the last update check (0 = never), used to throttle the auto-check. DE: Epoch-Millis der letzten Update-Prüfung (0 = nie), drosselt den Auto-Check. */
    fun lastUpdateCheck(ctx: Context): Long = prefs(ctx).getLong(K_LAST_UPDATE_CHECK, 0L)

    fun setLastUpdateCheck(ctx: Context, value: Long) =
        prefs(ctx).edit().putLong(K_LAST_UPDATE_CHECK, value).apply()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
