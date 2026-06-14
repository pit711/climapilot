package com.climapilot.free

import android.content.Context

/**
 * EN: Identifies each reorderable section card on the control screen. Stored by name, so renaming an
 *     entry would reset its saved position (intended — names are the stable on-disk key).
 * DE: Bezeichnet jede umsortierbare Abschnitts-Karte im Steuer-Bildschirm. Wird per Name gespeichert,
 *     d. h. ein Umbenennen würde die gespeicherte Position zurücksetzen (so gewollt — der Name ist der
 *     stabile Schlüssel auf der Platte).
 */
enum class CardId { Mode, Fan, Status, Options, Gear, Scenes, Sleep }

/**
 * EN: Persists the user's custom card order for the control screen (long-press a card to drag it).
 *     [load] is forward/backward compatible: unknown saved names are dropped and newly added cards are
 *     appended in their default position, so updates never corrupt a saved layout.
 * DE: Speichert die benutzerdefinierte Kartenreihenfolge des Steuer-Bildschirms (Karte lange drücken
 *     zum Ziehen). [load] ist vorwärts-/rückwärtskompatibel: unbekannte gespeicherte Namen werden
 *     verworfen und neu hinzugekommene Karten an ihrer Standardposition angehängt, sodass Updates ein
 *     gespeichertes Layout nie beschädigen.
 */
object CardOrderRepo {
    private const val PREFS = "climapilot_layout"
    private const val KEY = "card_order"

    /** EN: Default top-to-bottom order. DE: Standard-Reihenfolge von oben nach unten. */
    private val DEFAULT = listOf(
        CardId.Mode, CardId.Fan, CardId.Status, CardId.Options, CardId.Gear, CardId.Scenes, CardId.Sleep,
    )

    fun load(ctx: Context): List<CardId> {
        val raw = prefs(ctx).getString(KEY, null) ?: return DEFAULT
        val saved = raw.split(",").mapNotNull { name -> runCatching { CardId.valueOf(name.trim()) }.getOrNull() }
        // EN: Append any card types not present in the saved order (e.g. added in an update).
        // DE: Alle Kartentypen anhängen, die in der gespeicherten Reihenfolge fehlen (z. B. neu in einem Update).
        return (saved + DEFAULT.filter { it !in saved }).distinct()
    }

    fun save(ctx: Context, order: List<CardId>) {
        prefs(ctx).edit().putString(KEY, order.joinToString(",") { it.name }).apply()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
