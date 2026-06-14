package com.climapilot.free

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * EN: Persistent per-device store for the Midea V3 token + key (plus a little device info for display).
 *     The V3 LAN handshake needs a token/key pair that only Midea's cloud issues. We fetch it once
 *     (online) and cache it here, so every later connection to the same device works fully OFFLINE —
 *     no internet, no cloud. The user can also view/export these credentials from Settings (back them
 *     up, or reuse them in other local-control tools). They are device-local secrets kept only in the
 *     app's private SharedPreferences.
 *
 * DE: Dauerhafter Speicher (pro Gerät) für Mideas V3-Token + Key (plus etwas Geräte-Info zur Anzeige).
 *     Der V3-LAN-Handshake benötigt ein Token/Key-Paar, das nur Mideas Cloud ausstellt. Wir holen es
 *     einmal (online) und speichern es hier, sodass jede spätere Verbindung zum selben Gerät
 *     vollständig OFFLINE funktioniert — ohne Internet, ohne Cloud. Der Nutzer kann diese Zugangsdaten
 *     in den Einstellungen ansehen/exportieren (sichern oder in anderen Tools nutzen). Es sind
 *     gerätelokale Geheimnisse, die nur in den privaten SharedPreferences der App liegen.
 */
object TokenRepo {
    private const val PREFS = "climapilot_tokens"
    private const val KEY = "devices"

    /** EN: One cached device with its credentials. DE: Ein gecachtes Gerät samt Zugangsdaten. */
    data class Entry(
        val id: Long,
        val name: String,
        val ip: String,
        val port: Int,
        val token: String,
        val key: String,
    )

    /**
     * EN: Load the cached (token, key) for a device id, or null if we've never connected online yet.
     * DE: Das gecachte (Token, Key) für eine Geräte-ID laden, oder null, wenn noch nie online verbunden wurde.
     */
    fun load(ctx: Context, deviceId: Long): Pair<String, String>? {
        val e = list(ctx).firstOrNull { it.id == deviceId } ?: return null
        return e.token to e.key
    }

    /**
     * EN: Persist a freshly fetched token/key for a device (upsert by id) so future connects stay offline.
     * DE: Ein frisch geholtes Token/Key für ein Gerät speichern (per ID einfügen/ersetzen), damit künftige Verbindungen offline bleiben.
     */
    fun save(ctx: Context, id: Long, name: String, ip: String, port: Int, token: String, key: String) {
        // EN: Replace any prior entry for the same device — matched by id OR ip, since the same unit
        //     can be saved under two id encodings (little/big-endian) yet has one ip.
        // DE: Jeden früheren Eintrag desselben Geräts ersetzen — per ID ODER IP, da dasselbe Gerät unter
        //     zwei ID-Kodierungen (Little/Big-Endian) gespeichert sein kann, aber nur eine IP hat.
        val entries = list(ctx).filterNot { it.id == id || it.ip == ip } + Entry(id, name, ip, port, token, key)
        persist(ctx, entries)
    }

    /** EN: All cached devices (for the Settings export list). DE: Alle gecachten Geräte (für die Export-Liste in den Einstellungen). */
    fun list(ctx: Context): List<Entry> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val parsed = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Entry(
                    id = o.getLong("id"),
                    name = o.optString("name"),
                    ip = o.optString("ip"),
                    port = o.optInt("port", 6444),
                    token = o.optString("token"),
                    key = o.optString("key"),
                )
            }
            // EN: Clean up any legacy duplicates by collapsing per ip (last entry wins).
            // DE: Etwaige Alt-Duplikate bereinigen, indem pro IP zusammengefasst wird (letzter Eintrag gewinnt).
            parsed.associateBy { it.ip }.values.toList()
        }.getOrDefault(emptyList())
    }

    /** EN: Forget a device's cached credentials. DE: Die gecachten Zugangsdaten eines Geräts vergessen. */
    fun clear(ctx: Context, id: Long) {
        persist(ctx, list(ctx).filterNot { it.id == id })
    }

    /**
     * EN: Parse a previously exported credential block ("name:/ip:/port:/id:/token:/key:" lines) and
     *     save it. Lenient about order and extra lines. Returns true if the required fields were found.
     * DE: Einen zuvor exportierten Zugangsdaten-Block ("name:/ip:/port:/id:/token:/key:"-Zeilen) parsen
     *     und speichern. Tolerant gegenüber Reihenfolge und Zusatzzeilen. true, wenn die Pflichtfelder gefunden wurden.
     */
    fun importText(ctx: Context, text: String): Boolean {
        val map = text.lineSequence().mapNotNull { line ->
            val i = line.indexOf(':')
            if (i <= 0) null else line.substring(0, i).trim().lowercase() to line.substring(i + 1).trim()
        }.toMap()
        val id = map["id"]?.toLongOrNull() ?: return false
        val ip = map["ip"]?.takeIf { it.isNotBlank() } ?: return false
        val token = map["token"]?.takeIf { it.isNotBlank() } ?: return false
        val key = map["key"]?.takeIf { it.isNotBlank() } ?: return false
        val port = map["port"]?.toIntOrNull() ?: 6444
        val name = map["name"]?.takeIf { it.isNotBlank() } ?: "Midea"
        save(ctx, id, name, ip, port, token, key)
        return true
    }

    /**
     * EN: Format one device's credentials as a copy-/share-friendly text block.
     * DE: Die Zugangsdaten eines Geräts als kopier-/teilbaren Textblock formatieren.
     */
    fun exportText(e: Entry): String = buildString {
        appendLine("# ClimaPilot token export")
        appendLine("name:  ${e.name}")
        appendLine("ip:    ${e.ip}")
        appendLine("port:  ${e.port}")
        appendLine("id:    ${e.id}")
        appendLine("token: ${e.token}")
        appendLine("key:   ${e.key}")
    }.trimEnd()

    private fun persist(ctx: Context, entries: List<Entry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id); put("name", e.name); put("ip", e.ip)
                put("port", e.port); put("token", e.token); put("key", e.key)
            })
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }
}
