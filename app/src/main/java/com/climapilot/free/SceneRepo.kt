package com.climapilot.free

import android.content.Context
import com.climapilot.free.midea.MideaAc
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * EN: A user-defined "quick scene" — a complete snapshot of the controllable AC state that the user
 *     can re-apply with a single tap. The fields mirror exactly what
 *     [com.climapilot.free.midea.MideaAcSession.apply] drives onto the device.
 *
 * DE: Eine benutzerdefinierte „Schnell-Szene" — eine vollständige Momentaufnahme des steuerbaren
 *     Klima-Zustands, die mit einem einzigen Tippen wieder angewendet werden kann. Die Felder
 *     entsprechen exakt dem, was [com.climapilot.free.midea.MideaAcSession.apply] an das Gerät sendet.
 */
data class Scene(
    // EN: Stable unique id (used as the list key and for deletion). DE: Stabile, eindeutige ID
    // (dient als Listen-Schlüssel und zum Löschen).
    val id: String = UUID.randomUUID().toString(),
    // EN: Display name shown on the chip. DE: Anzeigename, der auf dem Chip erscheint.
    val name: String,
    // EN: Power on/off. DE: Ein/Aus.
    val powerOn: Boolean,
    // EN: Operating mode (see MideaAc.MODE_*). DE: Betriebsmodus (siehe MideaAc.MODE_*).
    val mode: Int,
    // EN: Target temperature in °C. DE: Zieltemperatur in °C.
    val tempC: Double,
    // EN: Fan speed 1–100, or 102 = auto. DE: Lüfterstufe 1–100, oder 102 = Auto.
    val fan: Int,
    // EN: Eco mode flag. DE: Eco-Modus-Schalter.
    val eco: Boolean,
    // EN: Louver swing flag. DE: Lamellen-Swing-Schalter.
    val swing: Boolean,
    // EN: Optional daily schedule as minutes since midnight (e.g. 22*60+30 = 22:30); null = no schedule.
    // DE: Optionaler täglicher Zeitplan als Minuten seit Mitternacht (z. B. 22*60+30 = 22:30); null = kein Zeitplan.
    val scheduleMinutes: Int? = null,
)

/**
 * EN: A tiny SharedPreferences-backed store for the user's custom scenes. Scenes are serialised as a
 *     JSON array using the platform [org.json] API, so no extra third-party dependency is required.
 *     [load] returns null when the user has never had any scenes stored, which lets the caller seed
 *     sensible first-run defaults exactly once.
 *
 * DE: Ein winziger, auf SharedPreferences basierender Speicher für die benutzerdefinierten Szenen.
 *     Die Szenen werden über die plattformeigene [org.json]-API als JSON-Array serialisiert, sodass
 *     keine zusätzliche Fremdbibliothek nötig ist. [load] liefert null, wenn noch nie Szenen
 *     gespeichert wurden — so kann der Aufrufer genau einmal sinnvolle Standard-Szenen anlegen.
 */
object SceneRepo {
    private const val PREFS = "climapilot_scenes"
    private const val KEY = "scenes"

    /**
     * EN: Returns the stored scenes, or null when nothing has ever been saved (first run).
     * DE: Liefert die gespeicherten Szenen, oder null, wenn noch nie etwas gespeichert wurde (erster Start).
     */
    fun load(ctx: Context): List<Scene>? {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return null
        // EN: Tolerate corrupt/old data by returning null instead of crashing.
        // DE: Defekte/alte Daten werden toleriert: lieber null als ein Absturz.
        return runCatching { parse(raw) }.getOrNull()
    }

    /**
     * EN: Persist the full scene list (overwrites the previous value).
     * DE: Speichert die komplette Szenenliste (überschreibt den vorherigen Wert).
     */
    fun save(ctx: Context, scenes: List<Scene>) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, encode(scenes)).apply()
    }

    /** EN: Serialise scenes to a JSON string. DE: Szenen in einen JSON-String serialisieren. */
    private fun encode(scenes: List<Scene>): String {
        val arr = JSONArray()
        scenes.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("power", s.powerOn)
                put("mode", s.mode)
                put("temp", s.tempC)
                put("fan", s.fan)
                put("eco", s.eco)
                put("swing", s.swing)
                put("sched", s.scheduleMinutes ?: -1)
            })
        }
        return arr.toString()
    }

    /** EN: Parse scenes from a JSON string. DE: Szenen aus einem JSON-String einlesen. */
    private fun parse(raw: String): List<Scene> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            // EN: opt* with defaults keeps old saved data forward-compatible.
            // DE: opt* mit Standardwerten hält alte gespeicherte Daten vorwärtskompatibel.
            Scene(
                id = o.optString("id", UUID.randomUUID().toString()),
                name = o.optString("name", "Scene"),
                powerOn = o.optBoolean("power", true),
                mode = o.optInt("mode", MideaAc.MODE_COOL),
                tempC = o.optDouble("temp", 24.0),
                fan = o.optInt("fan", 60),
                eco = o.optBoolean("eco", false),
                swing = o.optBoolean("swing", false),
                scheduleMinutes = o.optInt("sched", -1).takeIf { it >= 0 },
            )
        }
    }
}
