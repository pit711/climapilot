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
    private const val K_BEEP = "beep"
    private const val K_DIAG_GROUP1 = "diag_group1"
    private const val K_DIAG_GROUP2 = "diag_group2"
    private const val K_DIAG_GROUP7 = "diag_group7"
    private const val K_POLL_INTERVAL = "poll_interval_sec"
    private const val K_INDOOR_OFFSET = "indoor_offset_"

    /** EN: Largest indoor-temperature correction we allow, in kelvin. DE: Größte erlaubte Innentemperatur-Korrektur in Kelvin. */
    const val INDOOR_OFFSET_MAX = 5.0

    /** EN: Step size of the calibration stepper, matching the sensor's 0.5 K resolution. DE: Schrittweite des Kalibrier-Reglers, passend zur 0,5-K-Auflösung des Fühlers. */
    const val INDOOR_OFFSET_STEP = 0.5

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

    /** EN: Prompt tone — when on, control commands carry the beep bit so the AC chirps (on many units only on power on/off). DE: Signalton — wenn an, tragen Steuerbefehle das Beep-Bit, sodass die Klima quittiert (bei vielen Geräten nur bei Ein/Aus). */
    fun beep(ctx: Context): Boolean = prefs(ctx).getBoolean(K_BEEP, false)

    fun setBeep(ctx: Context, value: Boolean) =
        prefs(ctx).edit().putBoolean(K_BEEP, value).apply()

    // EN: ---- Beta: extra diagnostics (midea-msmart PR #278 group data). ON by default (user request);
    //     each adds one query per refresh and can be turned off per group. Enabled groups are also
    //     recorded into the usage history.
    // DE: ---- Beta: Zusatz-Diagnose (midea-msmart PR #278 Gruppendaten). Standardmäßig AN
    //     (User-Wunsch); jede fügt eine Abfrage pro Refresh hinzu und ist einzeln abschaltbar.
    //     Aktivierte Gruppen werden zusätzlich im Verlauf aufgezeichnet.

    /** EN: Group 1 — compressor + refrigerant-circuit temperatures. DE: Gruppe 1 — Kompressor + Kältekreis-Temperaturen. */
    fun diagGroup1(ctx: Context): Boolean = prefs(ctx).getBoolean(K_DIAG_GROUP1, true)

    fun setDiagGroup1(ctx: Context, value: Boolean) =
        prefs(ctx).edit().putBoolean(K_DIAG_GROUP1, value).apply()

    /** EN: Group 2 — indoor fan speed + condensate pump. DE: Gruppe 2 — Innenlüfterdrehzahl + Kondensatpumpe. */
    fun diagGroup2(ctx: Context): Boolean = prefs(ctx).getBoolean(K_DIAG_GROUP2, true)

    fun setDiagGroup2(ctx: Context, value: Boolean) =
        prefs(ctx).edit().putBoolean(K_DIAG_GROUP2, value).apply()

    /** EN: Group 7 — outdoor-unit power (W). DE: Gruppe 7 — Außengerät-Leistung (W). */
    fun diagGroup7(ctx: Context): Boolean = prefs(ctx).getBoolean(K_DIAG_GROUP7, true)

    fun setDiagGroup7(ctx: Context, value: Boolean) =
        prefs(ctx).edit().putBoolean(K_DIAG_GROUP7, value).apply()

    /**
     * EN: Live-refresh poll interval in seconds (how often state/energy/diagnostics are queried while
     *     the app is connected). Clamped to 2–60; default 6 (the pre-0.6.5 fixed value).
     * DE: Poll-Intervall der Live-Aktualisierung in Sekunden (wie oft Zustand/Energie/Diagnose
     *     abgefragt werden, solange die App verbunden ist). Begrenzt auf 2–60; Standard 6 (der feste
     *     Wert vor 0.6.5).
     */
    fun pollIntervalSec(ctx: Context): Int = prefs(ctx).getInt(K_POLL_INTERVAL, 6).coerceIn(2, 60)

    fun setPollIntervalSec(ctx: Context, value: Int) =
        prefs(ctx).edit().putInt(K_POLL_INTERVAL, value.coerceIn(2, 60)).apply()

    /**
     * EN: Manual indoor-temperature calibration for one device, in kelvin (−5…+5, 0 = off). Many units
     *     read a degree or two off the real room temperature; this correction is added to the sensor
     *     value wherever the indoor temperature is shown or recorded, so the reading — and with it the
     *     "how far am I from the setpoint?" judgement — matches a room thermometer. Stored per device
     *     because the error belongs to that unit's sensor, not to the app.
     * DE: Manuelle Innentemperatur-Kalibrierung für ein Gerät, in Kelvin (−5…+5, 0 = aus). Viele Geräte
     *     messen ein bis zwei Grad neben der echten Raumtemperatur; diese Korrektur wird überall dort auf
     *     den Fühlerwert addiert, wo die Innentemperatur angezeigt oder aufgezeichnet wird — damit der
     *     Wert und die Einschätzung „wie weit bin ich vom Soll?" zum Raumthermometer passen. Pro Gerät
     *     gespeichert, weil der Fehler zum Fühler dieses Geräts gehört, nicht zur App.
     */
    fun indoorOffset(ctx: Context, deviceId: Long): Double =
        prefs(ctx).getFloat(K_INDOOR_OFFSET + deviceId, 0f).toDouble()
            .coerceIn(-INDOOR_OFFSET_MAX, INDOOR_OFFSET_MAX)

    fun setIndoorOffset(ctx: Context, deviceId: Long, value: Double) =
        prefs(ctx).edit()
            .putFloat(K_INDOOR_OFFSET + deviceId, value.coerceIn(-INDOOR_OFFSET_MAX, INDOOR_OFFSET_MAX).toFloat())
            .apply()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
