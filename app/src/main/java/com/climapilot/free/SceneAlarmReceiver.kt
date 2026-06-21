package com.climapilot.free

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * EN: Fired by [SceneScheduler] when a scheduled scene is due. The network apply (connect + command,
 *     with retries and a Wi-Fi lock) is handed to [AcCommandWorker] so it survives the ~10 s broadcast
 *     budget and a momentarily unavailable LAN. The cheap, network-free part — re-arming tomorrow's
 *     alarm — stays here so the daily schedule keeps rolling regardless of whether the AC was reachable.
 * DE: Wird von [SceneScheduler] ausgelöst, wenn eine geplante Szene fällig ist. Das Anwenden im Netz
 *     (Verbinden + Befehl, mit Retries und WLAN-Lock) übernimmt [AcCommandWorker], damit es das
 *     ~10-s-Broadcast-Budget und ein kurz nicht erreichbares LAN übersteht. Der günstige, netzfreie
 *     Teil — den morgigen Alarm neu setzen — bleibt hier, damit der Tagesplan weiterläuft, egal ob die
 *     Klima erreichbar war.
 */
class SceneAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sceneId = intent.getStringExtra(SceneScheduler.EXTRA_SCENE_ID) ?: return
        val appCtx = context.applicationContext
        // EN: Apply the scene robustly in the background. DE: Die Szene zuverlässig im Hintergrund anwenden.
        AcCommandWorker.enqueueScene(appCtx, sceneId)
        // EN: Re-arm for the next day if the scene still has a time. DE: Für den nächsten Tag neu planen, falls die Szene noch eine Uhrzeit hat.
        SceneRepo.load(appCtx)?.firstOrNull { it.id == sceneId }?.scheduleMinutes
            ?.let { SceneScheduler.schedule(appCtx, sceneId, it) }
    }
}
