package com.climapilot.free

import android.app.Activity
import android.os.Bundle

/**
 * EN: Invisible trampoline for launcher long-press shortcuts that should perform an action rather than
 *     open a screen. It hands the action to [AcCommandWorker] (robust: waits for network, retries, Wi-Fi
 *     lock) and finishes immediately, so the user just sees a quick flash, not the full app. Declared
 *     with a translucent, no-history theme in the manifest.
 * DE: Unsichtbares Trampolin für Launcher-Shortcuts (Icon lange drücken), die eine Aktion ausführen
 *     statt einen Bildschirm zu öffnen. Es übergibt die Aktion an [AcCommandWorker] (robust: wartet aufs
 *     Netz, Retries, WLAN-Lock) und beendet sich sofort, sodass der Nutzer nur kurz aufblitzt, nicht die
 *     ganze App sieht. Im Manifest mit transluzentem, verlaufsfreiem Theme deklariert.
 */
class ShortcutActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent?.getStringExtra(EXTRA_ACTION)) {
            ACTION_POWER_OFF -> AcCommandWorker.enqueuePowerOff(applicationContext, 0L)
            ACTION_SCENE -> intent.getStringExtra(EXTRA_SCENE_ID)
                ?.let { AcCommandWorker.enqueueScene(applicationContext, it) }
        }
        finish()
    }

    companion object {
        const val EXTRA_ACTION = "shortcut_action"
        const val EXTRA_SCENE_ID = "shortcut_scene_id"
        const val ACTION_POWER_OFF = "power_off"
        const val ACTION_SCENE = "scene"
    }
}
