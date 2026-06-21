package com.climapilot.free

import android.content.Context
import android.os.Build

/**
 * EN: Decides when to (rarely, tastefully) show the donation bottom sheet. Tracks distinct usage days,
 *     not raw launches, so it only triggers for people who actually use ClimaPilot. Guardrails: never
 *     before [MIN_INSTALL_DAYS] of install age, a quiet period after every update, a hard cap of
 *     [MAX_PROMPTS] over the app's lifetime, a [SNOOZE_DAYS]-of-use gap between prompts, and a permanent
 *     "already supported" off switch. The free app's promise is "no nagging" — err on the side of rare.
 * DE: Entscheidet, wann das Spenden-Bottom-Sheet (selten, dezent) erscheint. Zählt verschiedene
 *     Nutzungstage statt roher Starts, sodass es nur bei echten Nutzern auslöst. Schutzschranken: nie vor
 *     [MIN_INSTALL_DAYS] Installationsalter, eine Ruhephase nach jedem Update, eine harte Obergrenze von
 *     [MAX_PROMPTS] über die Lebensdauer, ein Abstand von [SNOOZE_DAYS] Nutzungstagen zwischen Prompts und
 *     ein dauerhaftes „schon unterstützt"-Aus. Das Free-Versprechen ist „kein Nerven" — lieber zu selten.
 */
object DonationPrompt {
    private const val PREFS = "climapilot_donate"
    private const val K_INSTALL_DAY = "install_day"
    private const val K_LAST_DAY = "last_day"
    private const val K_DAYS_USED = "days_used"
    private const val K_PROMPT_COUNT = "prompt_count"
    private const val K_SNOOZE_UNTIL = "snooze_until_days"
    private const val K_NEVER = "never"
    private const val K_UPDATE_DAY = "update_day"
    private const val K_VERSION = "version_code"

    // EN: Tunables — see the recommendation. DE: Stellschrauben — siehe Empfehlung.
    private const val FIRST_THRESHOLD_DAYS = 10  // first prompt after 10 distinct usage days
    private const val MIN_INSTALL_DAYS = 7        // never within the first week of install
    private const val QUIET_AFTER_UPDATE_DAYS = 14 // no prompt for 14 days after an update
    private const val SNOOZE_DAYS = 30            // gap (in usage days) before a second prompt
    private const val MAX_PROMPTS = 2             // at most twice over the app's lifetime

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun today() = System.currentTimeMillis() / 86_400_000L

    /** EN: Record one app-use day and detect updates. Call once per app open. DE: Einen Nutzungstag erfassen und Updates erkennen. Einmal pro App-Start aufrufen. */
    fun recordUsage(ctx: Context) {
        val p = prefs(ctx)
        val today = today()
        val e = p.edit()
        if (!p.contains(K_INSTALL_DAY)) e.putLong(K_INSTALL_DAY, today)
        if (p.getLong(K_LAST_DAY, -1L) != today) {
            e.putLong(K_LAST_DAY, today)
            e.putInt(K_DAYS_USED, p.getInt(K_DAYS_USED, 0) + 1)
        }
        val vc = versionCode(ctx)
        if (p.getInt(K_VERSION, -1) != vc) {
            e.putInt(K_VERSION, vc)
            e.putLong(K_UPDATE_DAY, today)
        }
        e.apply()
    }

    /** EN: True if the donation sheet may be shown right now. DE: True, wenn das Spenden-Sheet jetzt gezeigt werden darf. */
    fun shouldShow(ctx: Context): Boolean {
        val p = prefs(ctx)
        val today = today()
        if (p.getBoolean(K_NEVER, false)) return false
        if (p.getInt(K_PROMPT_COUNT, 0) >= MAX_PROMPTS) return false
        val install = p.getLong(K_INSTALL_DAY, today)
        if (today - install < MIN_INSTALL_DAYS) return false
        val updateDay = p.getLong(K_UPDATE_DAY, install)
        if (today - updateDay < QUIET_AFTER_UPDATE_DAYS) return false
        val daysUsed = p.getInt(K_DAYS_USED, 0)
        val snoozeUntil = p.getInt(K_SNOOZE_UNTIL, FIRST_THRESHOLD_DAYS)
        return daysUsed >= snoozeUntil
    }

    /** EN: The sheet is being shown — count it and push the next one out by [SNOOZE_DAYS] of use. DE: Das Sheet wird gezeigt — zählen und das nächste um [SNOOZE_DAYS] Nutzungstage verschieben. */
    fun markShown(ctx: Context) {
        val p = prefs(ctx)
        p.edit()
            .putInt(K_PROMPT_COUNT, p.getInt(K_PROMPT_COUNT, 0) + 1)
            .putInt(K_SNOOZE_UNTIL, p.getInt(K_DAYS_USED, 0) + SNOOZE_DAYS)
            .apply()
    }

    /** EN: User said "already supported / don't show again" — never prompt again. DE: Nutzer sagte „schon unterstützt / nicht mehr zeigen" — nie wieder fragen. */
    fun markNever(ctx: Context) = prefs(ctx).edit().putBoolean(K_NEVER, true).apply()

    private fun versionCode(ctx: Context): Int = runCatching {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode.toInt()
        else @Suppress("DEPRECATION") info.versionCode
    }.getOrDefault(0)
}
