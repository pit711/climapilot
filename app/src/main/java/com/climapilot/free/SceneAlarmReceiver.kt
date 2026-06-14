package com.climapilot.free

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.climapilot.free.midea.MideaAcSession
import com.climapilot.free.midea.MideaDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * EN: Fired by [SceneScheduler] when a scheduled scene is due. It connects to the cached device
 *     locally (offline, using the stored token) and applies the scene, then re-arms tomorrow's alarm.
 *     Network work runs off the main thread via goAsync()/coroutine. For a single-AC setup the first
 *     cached device is used.
 * DE: Wird von [SceneScheduler] ausgelöst, wenn eine geplante Szene fällig ist. Verbindet sich lokal
 *     mit dem gecachten Gerät (offline, mit gespeichertem Token), wendet die Szene an und plant dann
 *     den morgigen Alarm neu. Die Netzwerkarbeit läuft via goAsync()/Coroutine abseits des Main-Threads.
 *     Bei einem Ein-Geräte-Setup wird das erste gecachte Gerät verwendet.
 */
class SceneAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sceneId = intent.getStringExtra(SceneScheduler.EXTRA_SCENE_ID) ?: return
        val appCtx = context.applicationContext
        // EN: Keep the receiver alive while the async work runs. DE: Den Receiver am Leben halten, während die Async-Arbeit läuft.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val scene = SceneRepo.load(appCtx)?.firstOrNull { it.id == sceneId }
                if (scene != null) {
                    applyScene(appCtx, scene)
                    // EN: Re-arm for the next day if the scene still has a time. DE: Für den nächsten Tag neu planen, falls die Szene noch eine Uhrzeit hat.
                    scene.scheduleMinutes?.let { SceneScheduler.schedule(appCtx, scene.id, it) }
                }
            } catch (_: Exception) {
                // EN: Best-effort; nothing to do if the AC is unreachable. DE: Best-Effort; nichts zu tun, wenn die Klima nicht erreichbar ist.
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun applyScene(ctx: Context, scene: Scene) {
        // EN: Resolve a target device from the cached tokens (offline-capable). DE: Ein Zielgerät aus den gecachten Tokens ermitteln (offline-fähig).
        val dev = TokenRepo.list(ctx).firstOrNull() ?: return
        val device = MideaDevice(
            ip = dev.ip, port = dev.port, id = dev.id, sn = "", name = dev.name, type = 0xAC, version = 3,
        )
        val session = MideaAcSession(device, cachedCreds = dev.token to dev.key)
        try {
            session.connect()
            session.powerOn = scene.powerOn
            session.mode = scene.mode
            session.tempC = scene.tempC
            session.fan = scene.fan
            session.eco = scene.eco
            session.swing = if (scene.swing) 0x3F else 0
            session.apply()
        } finally {
            session.close()
        }
    }
}
