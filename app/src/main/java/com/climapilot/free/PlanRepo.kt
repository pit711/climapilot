package com.climapilot.free

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * EN: What happens at the end of a planned window.
 *     OFF   = turn the AC off when the window ends (e.g. "cool 6–18, then off").
 *     LEAVE = do nothing at the end; the window only defines when to switch the AC *to* the scene and
 *             it keeps running until the next planned event (or the user changes it).
 * DE: Was am Ende eines geplanten Fensters passiert.
 *     OFF   = die Klima ausschalten, wenn das Fenster endet (z. B. „6–18 kühlen, danach aus").
 *     LEAVE = am Ende nichts tun; das Fenster legt nur fest, wann auf die Szene umgeschaltet wird, und
 *             sie läuft bis zum nächsten geplanten Ereignis weiter (oder bis der Nutzer etwas ändert).
 */
enum class EndAction { OFF, LEAVE }

/**
 * EN: One recurring entry of the weekly day-planner: "apply scene X on these weekdays from start to end".
 *     Days use ISO numbering (1 = Monday … 7 = Sunday). Times are minutes since midnight. If [endMinutes]
 *     is less than or equal to [startMinutes] the window runs past midnight into the next day (e.g. 22:00→06:00).
 *     The entry references a [Scene] by id rather than embedding it, so editing a scene updates every plan
 *     entry that uses it.
 *
 * DE: Ein wiederkehrender Eintrag des Wochen-Tagesplaners: „wende Szene X an diesen Wochentagen von
 *     Start bis Ende an". Tage in ISO-Zählung (1 = Montag … 7 = Sonntag). Zeiten sind Minuten seit
 *     Mitternacht. Ist [endMinutes] kleiner oder gleich [startMinutes], läuft das Fenster über Mitternacht
 *     in den Folgetag (z. B. 22:00→06:00). Der Eintrag verweist über die id auf eine [Scene], statt sie
 *     einzubetten — so wirkt das Bearbeiten einer Szene auf jeden Planeintrag, der sie nutzt.
 */
data class PlanEntry(
    val id: String = UUID.randomUUID().toString(),
    // EN: Scene applied when the window starts. DE: Szene, die beim Start des Fensters angewendet wird.
    val sceneId: String,
    // EN: Weekdays this entry runs on (ISO 1=Mon … 7=Sun). DE: Wochentage, an denen der Eintrag läuft (ISO 1=Mo … 7=So).
    val days: Set<Int>,
    // EN: Window start, minutes since midnight. DE: Fensterbeginn, Minuten seit Mitternacht.
    val startMinutes: Int,
    // EN: Window end, minutes since midnight (≤ start ⇒ next day). DE: Fensterende, Minuten seit Mitternacht (≤ Start ⇒ Folgetag).
    val endMinutes: Int,
    // EN: What to do when the window ends. DE: Was am Ende des Fensters geschieht.
    val endAction: EndAction = EndAction.OFF,
    // EN: A disabled entry is kept but never fires. DE: Ein deaktivierter Eintrag bleibt erhalten, löst aber nie aus.
    val enabled: Boolean = true,
)

/**
 * EN: SharedPreferences/JSON-backed store for the weekly plan, mirroring [SceneRepo] so it needs no
 *     third-party dependency. [load] returns null only on a parse error; an empty plan is a valid,
 *     stored empty list.
 *
 * DE: Auf SharedPreferences/JSON basierender Speicher für den Wochenplan, analog zu [SceneRepo], damit
 *     keine Fremdbibliothek nötig ist. [load] liefert nur bei einem Parse-Fehler null; ein leerer Plan
 *     ist eine gültige, gespeicherte leere Liste.
 */
object PlanRepo {
    private const val PREFS = "climapilot_plan"
    private const val KEY = "entries"

    /** EN: Returns the stored plan (possibly empty), or null if nothing readable was saved. DE: Liefert den gespeicherten Plan (evtl. leer), oder null, wenn nichts Lesbares gespeichert wurde. */
    fun load(ctx: Context): List<PlanEntry>? {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return null
        return runCatching { parse(raw) }.getOrNull()
    }

    /** EN: Persist the whole plan (overwrites the previous value). DE: Den ganzen Plan speichern (überschreibt den vorherigen Wert). */
    fun save(ctx: Context, plan: List<PlanEntry>) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, encode(plan)).apply()
    }

    private fun encode(plan: List<PlanEntry>): String {
        val arr = JSONArray()
        plan.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id)
                put("scene", e.sceneId)
                put("days", JSONArray().also { d -> e.days.sorted().forEach(d::put) })
                put("start", e.startMinutes)
                put("end", e.endMinutes)
                put("endAct", e.endAction.name)
                put("on", e.enabled)
            })
        }
        return arr.toString()
    }

    private fun parse(raw: String): List<PlanEntry> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val daysArr = o.optJSONArray("days") ?: JSONArray()
            val days = (0 until daysArr.length()).map { daysArr.getInt(it) }
                .filter { it in 1..7 }.toSortedSet()
            PlanEntry(
                id = o.optString("id", UUID.randomUUID().toString()),
                sceneId = o.optString("scene", ""),
                days = days,
                startMinutes = o.optInt("start", 8 * 60).coerceIn(0, 1439),
                endMinutes = o.optInt("end", 18 * 60).coerceIn(0, 1439),
                endAction = runCatching { EndAction.valueOf(o.optString("endAct", "OFF")) }
                    .getOrDefault(EndAction.OFF),
                enabled = o.optBoolean("on", true),
            )
        }
    }
}
