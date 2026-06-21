package com.climapilot.free

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * EN: Helpers to get the app exempted from battery optimisation and OEM auto-start killers, so the
 *     time-based features (sleep/off timer, scene schedules) actually fire when the app is closed.
 *     This is the single biggest reliability factor on aggressive OEMs (Xiaomi/HyperOS, Samsung,
 *     Huawei, …). We never request the Play-flagged REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission;
 *     we only *deep-link* the user to the relevant system settings screens, which needs no permission.
 *
 * DE: Hilfsfunktionen, um die App von der Akku-Optimierung und den Autostart-Killern der Hersteller
 *     auszunehmen, damit die zeitbasierten Funktionen (Sleep-/Off-Timer, Szenen-Zeitpläne) auch bei
 *     geschlossener App wirklich auslösen. Das ist der größte Zuverlässigkeitsfaktor bei aggressiven
 *     Herstellern (Xiaomi/HyperOS, Samsung, Huawei, …). Wir fordern nie die Play-markierte
 *     REQUEST_IGNORE_BATTERY_OPTIMIZATIONS-Berechtigung an, sondern verlinken den Nutzer nur per
 *     Deep-Link zu den passenden System-Einstellungen — das braucht keine Berechtigung.
 */
object ReliabilityHelper {

    /** EN: True if the app is already exempt from Doze/battery optimisation. DE: True, wenn die App schon von Doze/Akku-Optimierung ausgenommen ist. */
    fun isIgnoringBatteryOptimizations(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    /**
     * EN: Open the battery-optimisation list so the user can set ClimaPilot to "Don't optimise".
     *     Uses the no-permission settings list, with a fall-back to the app's detail page.
     * DE: Die Akku-Optimierungs-Liste öffnen, damit der Nutzer ClimaPilot auf „Nicht optimieren" setzt.
     *     Nutzt die berechtigungsfreie Einstellungs-Liste, mit Fallback auf die App-Detailseite.
     */
    fun openBatteryOptimizationSettings(ctx: Context) {
        val tries = listOf(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${ctx.packageName}".toUriCompat()),
        )
        launchFirst(ctx, tries)
    }

    /** EN: Some OEMs (notably Xiaomi/MIUI) have a separate auto-start manager that must be enabled too. DE: Manche Hersteller (v. a. Xiaomi/MIUI) haben einen separaten Autostart-Manager, der ebenfalls aktiviert werden muss. */
    fun hasOemAutoStart(): Boolean = oemAutoStartIntents().isNotEmpty()

    /** EN: Open the OEM auto-start manager (best-effort across known vendors). DE: Den Autostart-Manager des Herstellers öffnen (Best-Effort über bekannte Hersteller). */
    fun openAutoStartSettings(ctx: Context): Boolean = launchFirst(ctx, oemAutoStartIntents())

    /**
     * EN: Known per-vendor auto-start / background-management activities. Only those matching the
     *     current manufacturer are offered, newest MIUI path first.
     * DE: Bekannte Autostart-/Hintergrund-Verwaltungs-Activities je Hersteller. Nur die zum aktuellen
     *     Hersteller passenden werden angeboten, neuester MIUI-Pfad zuerst.
     */
    private fun oemAutoStartIntents(): List<Intent> {
        val man = Build.MANUFACTURER.lowercase()
        val comps = when {
            man.contains("xiaomi") || man.contains("redmi") || man.contains("poco") -> listOf(
                "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            )
            man.contains("samsung") -> listOf(
                "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity",
                "com.samsung.android.sm" to "com.samsung.android.sm.ui.battery.BatteryActivity",
            )
            man.contains("huawei") || man.contains("honor") -> listOf(
                "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
            )
            man.contains("oppo") || man.contains("realme") -> listOf(
                "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            )
            man.contains("vivo") -> listOf(
                "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            )
            man.contains("oneplus") -> listOf(
                "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
            )
            else -> emptyList()
        }
        return comps.map { (pkg, cls) ->
            Intent().setClassName(pkg, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** EN: Try each intent in order, return true on the first that launches. DE: Jeden Intent der Reihe nach versuchen, beim ersten erfolgreichen Start true zurückgeben. */
    private fun launchFirst(ctx: Context, intents: List<Intent>): Boolean {
        for (i in intents) {
            if (runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return true
        }
        return false
    }

    private fun String.toUriCompat() = android.net.Uri.parse(this)
}
