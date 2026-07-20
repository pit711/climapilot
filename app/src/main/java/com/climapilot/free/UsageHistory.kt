package com.climapilot.free

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * EN: Local, per-device store for the AC history charts. Records a throttled multi-metric sample —
 *     power (W), total kWh, on/off, indoor & outdoor temperature, fan level — keyed by device id, so a
 *     user with several ACs gets separate history (+ a separate filter-runtime counter) per unit. All in
 *     the app's private SQLite DB, offline. Sampling happens in the foreground (via the app) and, when
 *     the history feature is enabled, in the background every ~15 min (see [HistoryPollWorker]).
 * DE: Lokaler Speicher pro Gerät für die Klima-Verlaufs-Charts. Schreibt einen gedrosselten
 *     Mehrgrößen-Messwert — Leistung (W), Gesamt-kWh, Ein/Aus, Innen- & Außentemperatur, Lüfterstufe —
 *     geschlüsselt nach Geräte-ID, sodass bei mehreren Klimas jede Anlage eine eigene Historie (+ eigenen
 *     Filter-Laufzeitzähler) bekommt. Alles in der privaten SQLite-DB, offline. Erfasst wird im
 *     Vordergrund (über die App) und, wenn aktiviert, im Hintergrund alle ~15 min (siehe [HistoryPollWorker]).
 */
object UsageHistory {
    private const val DB = "climapilot_usage.db"
    private const val DB_VERSION = 5 // v5 adds beta-diagnostics columns (comp_hz/comp_w/fan_rpm) via ALTER — data preserved
    private const val TABLE = "samples"
    private const val PREFS = "climapilot_usage"
    private const val MIN_INTERVAL_MS = 55_000L
    // EN: No retention cap — recording is kept indefinitely (the user can clear via Android app storage). DE: Keine Aufbewahrungsgrenze — die Aufzeichnung bleibt unbegrenzt (Löschen über Android-App-Speicher).

    /** EN: Suggested hours between filter cleanings. DE: Empfohlene Stunden zwischen Filter-Reinigungen. */
    const val FILTER_LIMIT_HOURS = 250.0

    data class Sample(
        val ts: Long,
        val powerW: Double,
        val totalKwh: Double,
        val powerOn: Boolean,
        val indoorTemp: Double?,
        val outdoorTemp: Double?,
        val fanSpeed: Int,
        // EN: Beta diagnostics (null when the group wasn't enabled / didn't answer at sample time).
        // DE: Beta-Diagnose (null, wenn die Gruppe beim Messzeitpunkt aus war / nicht antwortete).
        val compressorHz: Double? = null,
        val compressorW: Double? = null,
        val fanRpm: Int? = null,
    )

    private class Helper(ctx: Context) : SQLiteOpenHelper(ctx, DB, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE $TABLE (device_id INTEGER, ts INTEGER, power_w REAL, total_kwh REAL, " +
                    "power_on INTEGER, indoor REAL, outdoor REAL, fan INTEGER, " +
                    "comp_hz REAL, comp_w REAL, fan_rpm INTEGER, PRIMARY KEY(device_id, ts))",
            )
        }
        override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
            if (old == 4) {
                // EN: v4 → v5: append the diagnostics columns, keeping all recorded samples.
                // DE: v4 → v5: Diagnose-Spalten anhängen, alle aufgezeichneten Messwerte bleiben erhalten.
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN comp_hz REAL")
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN comp_w REAL")
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN fan_rpm INTEGER")
                return
            }
            db.execSQL("DROP TABLE IF EXISTS $TABLE"); onCreate(db)
        }
    }

    private var helper: Helper? = null
    private fun db(ctx: Context): SQLiteDatabase =
        (helper ?: Helper(ctx.applicationContext).also { helper = it }).writableDatabase

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun lastKey(id: Long) = "last_record_ts_$id"
    private fun filterKey(id: Long) = "filter_runtime_sec_$id"

    /** EN: Record a sample for [deviceId] (throttled to ~1/min) and accumulate its filter runtime. DE: Einen Messwert für [deviceId] (auf ~1/min gedrosselt) aufzeichnen und dessen Filter-Laufzeit aufsummieren. */
    @Synchronized
    fun record(
        ctx: Context,
        deviceId: Long,
        powerW: Double?,
        totalKwh: Double?,
        powerOn: Boolean,
        indoorTemp: Double?,
        outdoorTemp: Double?,
        fanSpeed: Int,
        compressorHz: Double? = null,
        compressorW: Double? = null,
        fanRpm: Int? = null,
    ) {
        val now = System.currentTimeMillis()
        val p = prefs(ctx)
        val last = p.getLong(lastKey(deviceId), 0L)
        if (now - last < MIN_INTERVAL_MS) return
        if (powerOn && last > 0L) {
            val deltaSec = ((now - last) / 1000L).coerceAtMost(3600L)
            p.edit().putLong(filterKey(deviceId), p.getLong(filterKey(deviceId), 0L) + deltaSec).apply()
        }
        p.edit().putLong(lastKey(deviceId), now).apply()
        runCatching {
            val d = db(ctx)
            d.insertWithOnConflict(TABLE, null, ContentValues().apply {
                put("device_id", deviceId)
                put("ts", now)
                put("power_w", powerW ?: 0.0)
                put("total_kwh", totalKwh ?: 0.0)
                put("power_on", if (powerOn) 1 else 0)
                put("indoor", indoorTemp ?: Double.NaN)
                put("outdoor", outdoorTemp ?: Double.NaN)
                put("fan", fanSpeed)
                // EN: Diagnostics stay NULL when unavailable, so charts can skip those samples cleanly.
                // DE: Diagnose bleibt NULL, wenn nicht verfügbar — Charts überspringen solche Messwerte sauber.
                if (compressorHz != null) put("comp_hz", compressorHz) else putNull("comp_hz")
                if (compressorW != null) put("comp_w", compressorW) else putNull("comp_w")
                if (fanRpm != null) put("fan_rpm", fanRpm) else putNull("fan_rpm")
            }, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    /** EN: Samples for [deviceId] within [fromMs]..[toMs], oldest first. DE: Messwerte für [deviceId] im Bereich [fromMs]..[toMs], älteste zuerst. */
    fun range(ctx: Context, deviceId: Long, fromMs: Long, toMs: Long): List<Sample> = runCatching {
        db(ctx).query(TABLE, null, "device_id = ? AND ts >= ? AND ts <= ?", arrayOf(deviceId.toString(), fromMs.toString(), toMs.toString()), null, null, "ts ASC").use { c ->
            buildList {
                while (c.moveToNext()) {
                    val indoor = c.getDouble(5).takeIf { !it.isNaN() }
                    val outdoor = c.getDouble(6).takeIf { !it.isNaN() }
                    add(Sample(
                        c.getLong(1), c.getDouble(2), c.getDouble(3), c.getInt(4) == 1, indoor, outdoor, c.getInt(7),
                        compressorHz = if (c.isNull(8)) null else c.getDouble(8),
                        compressorW = if (c.isNull(9)) null else c.getDouble(9),
                        fanRpm = if (c.isNull(10)) null else c.getInt(10),
                    ))
                }
            }
        }
    }.getOrDefault(emptyList())

    fun filterHours(ctx: Context, deviceId: Long): Double = prefs(ctx).getLong(filterKey(deviceId), 0L) / 3600.0
    fun resetFilter(ctx: Context, deviceId: Long) = prefs(ctx).edit().putLong(filterKey(deviceId), 0L).apply()
}
