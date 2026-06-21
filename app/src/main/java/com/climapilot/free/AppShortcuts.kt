package com.climapilot.free

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * EN: Publishes launcher long-press shortcuts (dynamic, so the scene one reflects the user's first
 *     scene by name). Action shortcuts go through [ShortcutActionActivity] → [AcCommandWorker]; the
 *     demo shortcut opens [MainActivity] in demo mode. Refreshed on app start.
 * DE: Veröffentlicht Launcher-Shortcuts (Icon lange drücken; dynamisch, damit der Szenen-Shortcut die
 *     erste Szene des Nutzers namentlich zeigt). Aktions-Shortcuts laufen über
 *     [ShortcutActionActivity] → [AcCommandWorker]; der Demo-Shortcut öffnet [MainActivity] im
 *     Demo-Modus. Wird beim App-Start aktualisiert.
 */
object AppShortcuts {

    fun refresh(ctx: Context) {
        val shortcuts = mutableListOf<ShortcutInfoCompat>()

        // EN: "Off" — robust power-off via the worker, no app UI. DE: „Aus" — robustes Ausschalten über den Worker, ohne App-UI.
        if (TokenRepo.list(ctx).isNotEmpty()) {
            shortcuts += ShortcutInfoCompat.Builder(ctx, "ac_off")
                .setShortLabel(ctx.getString(R.string.shortcut_off))
                .setLongLabel(ctx.getString(R.string.shortcut_off))
                .setIcon(IconCompat.createWithResource(ctx, R.drawable.ic_w_power))
                .setIntent(
                    Intent(ctx, ShortcutActionActivity::class.java)
                        .setAction(Intent.ACTION_VIEW)
                        .putExtra(ShortcutActionActivity.EXTRA_ACTION, ShortcutActionActivity.ACTION_POWER_OFF),
                )
                .build()

            // EN: "Apply <first scene>" — only if the user has scenes. DE: „<erste Szene> anwenden" — nur wenn der Nutzer Szenen hat.
            SceneRepo.load(ctx)?.firstOrNull()?.let { scene ->
                shortcuts += ShortcutInfoCompat.Builder(ctx, "ac_scene")
                    .setShortLabel(scene.name.ifBlank { ctx.getString(R.string.scene_default_name) })
                    .setLongLabel(ctx.getString(R.string.shortcut_scene, scene.name))
                    .setIcon(IconCompat.createWithResource(ctx, R.drawable.ic_w_power))
                    .setIntent(
                        Intent(ctx, ShortcutActionActivity::class.java)
                            .setAction(Intent.ACTION_VIEW)
                            .putExtra(ShortcutActionActivity.EXTRA_ACTION, ShortcutActionActivity.ACTION_SCENE)
                            .putExtra(ShortcutActionActivity.EXTRA_SCENE_ID, scene.id),
                    )
                    .build()
            }
        }

        // EN: "Demo" — always available, opens the UI without a device. DE: „Demo" — immer verfügbar, öffnet die UI ohne Gerät.
        shortcuts += ShortcutInfoCompat.Builder(ctx, "demo")
            .setShortLabel(ctx.getString(R.string.shortcut_demo))
            .setLongLabel(ctx.getString(R.string.shortcut_demo))
            .setIcon(IconCompat.createWithResource(ctx, R.mipmap.ic_launcher))
            .setIntent(
                Intent(ctx, MainActivity::class.java)
                    .setAction(Intent.ACTION_VIEW)
                    .putExtra("demo", true),
            )
            .build()

        runCatching { ShortcutManagerCompat.setDynamicShortcuts(ctx, shortcuts) }
    }
}
