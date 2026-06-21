package com.climapilot.free

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.climapilot.free.midea.MideaAcSession
import com.climapilot.free.midea.MideaDevice
import com.climapilot.free.midea.MideaDiscovery
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * EN: Performs a one-shot AC command (sleep-timer power-off or scheduled scene) in the background,
 *     robustly. This replaces the old "do the network work directly inside a BroadcastReceiver's
 *     goAsync()" approach, which had three reliability holes: (1) a single connection attempt that was
 *     silently swallowed if the Wi-Fi/LAN wasn't ready the instant the alarm fired, (2) a ~10 s
 *     execution budget the system enforces on broadcasts (a cloud token refresh alone can exceed it),
 *     and (3) no retry. As a [CoroutineWorker] it instead: waits for a network (NetworkType.CONNECTED),
 *     holds a Wi-Fi lock so the radio stays up, retries with backoff via [Result.retry], runs well
 *     beyond 10 s, and — on a connect failure — re-discovers the unit on the LAN by id in case its
 *     DHCP address changed. The exact *timing* is still owned by the AlarmManager alarm that enqueues
 *     this worker; the worker only needs to run promptly afterwards.
 *
 * DE: Führt einen einmaligen Klima-Befehl (Sleep-Timer-Ausschalten oder geplante Szene) zuverlässig im
 *     Hintergrund aus. Ersetzt den alten Ansatz, die Netzwerkarbeit direkt im goAsync() eines
 *     BroadcastReceivers zu erledigen, der drei Zuverlässigkeitslücken hatte: (1) ein einziger
 *     Verbindungsversuch, der still verschluckt wurde, falls WLAN/LAN im Auslösemoment noch nicht
 *     bereit war, (2) das ~10-s-Zeitbudget, das das System Broadcasts auferlegt (allein ein
 *     Cloud-Token-Refresh kann das sprengen), und (3) keinen Retry. Als [CoroutineWorker] wartet er
 *     stattdessen auf ein Netz (NetworkType.CONNECTED), hält einen WLAN-Lock, wiederholt mit Backoff
 *     über [Result.retry], läuft deutlich länger als 10 s und sucht das Gerät bei einem
 *     Verbindungsfehler per id neu im LAN (falls sich seine DHCP-Adresse geändert hat). Das genaue
 *     *Timing* übernimmt weiterhin der AlarmManager-Alarm, der diesen Worker einreiht; der Worker muss
 *     nur zeitnah danach laufen.
 */
class AcCommandWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val op = inputData.getString(KEY_OP) ?: return Result.failure()
        val deviceId = inputData.getLong(KEY_DEVICE_ID, 0L)

        // EN: Keep the Wi-Fi radio awake for the duration — it can sleep independently of the CPU and
        //     would otherwise drop the LAN socket. DE: Das WLAN-Radio während der Arbeit wachhalten —
        //     es kann unabhängig von der CPU schlafen und würde sonst das LAN-Socket fallenlassen.
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF
        val lock = wifi?.createWifiLock(mode, "climapilot:ac-command")?.apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }
        try {
            val ok = runCatching { execute(op, deviceId) }.getOrDefault(false)
            return when {
                ok -> {
                    if (op == OP_POWER_OFF) SleepTimerScheduler.clear(applicationContext)
                    Result.success()
                }
                // EN: Transient failure — let WorkManager retry with backoff (network may return).
                // DE: Vorübergehender Fehler — WorkManager mit Backoff erneut versuchen lassen (Netz kommt evtl. wieder).
                runAttemptCount < MAX_ATTEMPTS -> Result.retry()
                else -> {
                    // EN: Give up after enough tries; don't leave a stale pending sleep timer behind.
                    // DE: Nach genug Versuchen aufgeben; keinen veralteten Sleep-Timer-State zurücklassen.
                    if (op == OP_POWER_OFF) SleepTimerScheduler.clear(applicationContext)
                    Result.failure()
                }
            }
        } finally {
            runCatching { lock?.release() }
        }
    }

    /** EN: Resolve the target device and run the op, relocating it on the LAN if the cached IP is stale. DE: Zielgerät ermitteln und Befehl ausführen, das Gerät bei veralteter IP im LAN neu lokalisieren. */
    private suspend fun execute(op: String, deviceId: Long): Boolean {
        val dev = TokenRepo.list(applicationContext).firstOrNull { it.id == deviceId }
            ?: TokenRepo.list(applicationContext).firstOrNull()
            ?: return false

        var device = MideaDevice(
            ip = dev.ip, port = dev.port, id = dev.id, sn = "", name = dev.name, type = 0xAC, version = 3,
        )
        repeat(2) { attempt ->
            try {
                runOnce(device, dev.token to dev.key, op)
                return true
            } catch (_: Exception) {
                // EN: Connect failed — the unit's DHCP address may have changed. Re-discover by id.
                // DE: Verbindung fehlgeschlagen — die DHCP-Adresse des Geräts hat sich evtl. geändert. Per id neu suchen.
                val found = runCatching { MideaDiscovery.discover(4000) }.getOrNull()
                    ?.firstOrNull { it.id == dev.id }
                if (found != null && (found.ip != device.ip || found.port != device.port)) {
                    device = device.copy(ip = found.ip, port = found.port)
                    // EN: Persist the new address so the next connect (and the UI) uses it directly.
                    // DE: Die neue Adresse speichern, damit der nächste Connect (und die UI) sie direkt nutzt.
                    TokenRepo.save(applicationContext, dev.id, dev.name, found.ip, found.port, dev.token, dev.key)
                } else if (attempt == 0) {
                    // EN: Same address — give Wi-Fi a moment to (re)associate, then retry once. DE: Gleiche Adresse — dem WLAN kurz Zeit zum (Neu-)Verbinden geben, dann einmal wiederholen.
                    delay(2500)
                }
            }
        }
        return false
    }

    /** EN: One connect + command cycle against the given address. DE: Ein Verbinden-und-Befehl-Zyklus gegen die angegebene Adresse. */
    private suspend fun runOnce(device: MideaDevice, creds: Pair<String, String>, op: String) {
        val session = MideaAcSession(device, cachedCreds = creds)
        try {
            session.connect()
            when (op) {
                OP_POWER_OFF -> {
                    // EN: Carry over the current mode/temp/fan so the off command doesn't reset them. DE: Aktuellen Modus/Temp/Lüfter übernehmen, damit der Aus-Befehl sie nicht zurücksetzt.
                    session.queryState()?.let { s ->
                        s.mode.takeIf { it in 1..5 }?.let { session.mode = it }
                        session.tempC = s.targetTemp
                        s.fanSpeed.takeIf { it in 1..102 }?.let { session.fan = it }
                    }
                    session.setPower(false)
                }
                OP_APPLY_SCENE -> {
                    val sceneId = inputData.getString(KEY_SCENE_ID)
                    val scene = SceneRepo.load(applicationContext)?.firstOrNull { it.id == sceneId } ?: return
                    session.powerOn = scene.powerOn
                    session.mode = scene.mode
                    session.tempC = scene.tempC
                    session.fan = scene.fan
                    session.eco = scene.eco
                    session.swing = if (scene.swing) 0x3F else 0
                    session.apply()
                }
            }
        } finally {
            session.close()
        }
    }

    companion object {
        const val OP_POWER_OFF = "POWER_OFF"
        const val OP_APPLY_SCENE = "APPLY_SCENE"
        private const val KEY_OP = "op"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_SCENE_ID = "scene_id"
        // EN: Unique-work name for the sleep-timer power-off (one at a time). DE: Eindeutiger Work-Name für das Sleep-Timer-Ausschalten (immer nur eins).
        private const val WORK_SLEEP_OFF = "ac_sleep_off"
        // EN: doWork runs up to this many times (initial + retries) before giving up. DE: doWork läuft bis zu so oft (erster Versuch + Retries), bevor aufgegeben wird.
        private const val MAX_ATTEMPTS = 5

        /** EN: Robustly power off [deviceId] (sleep timer). DE: [deviceId] zuverlässig ausschalten (Sleep-Timer). */
        fun enqueuePowerOff(ctx: Context, deviceId: Long) = enqueue(
            ctx, WORK_SLEEP_OFF,
            Data.Builder().putString(KEY_OP, OP_POWER_OFF).putLong(KEY_DEVICE_ID, deviceId).build(),
        )

        /** EN: Cancel a sleep-timer power-off that may still be pending/retrying (user cancelled). DE: Ein evtl. noch ausstehendes/wiederholendes Sleep-Timer-Ausschalten abbrechen (Nutzer hat abgebrochen). */
        fun cancelPowerOff(ctx: Context) {
            WorkManager.getInstance(ctx.applicationContext).cancelUniqueWork(WORK_SLEEP_OFF)
        }

        /** EN: Robustly apply the scene [sceneId] to the first cached device. DE: Die Szene [sceneId] zuverlässig auf das erste gecachte Gerät anwenden. */
        fun enqueueScene(ctx: Context, sceneId: String) = enqueue(
            ctx, "ac_scene_$sceneId",
            Data.Builder().putString(KEY_OP, OP_APPLY_SCENE).putLong(KEY_DEVICE_ID, 0L)
                .putString(KEY_SCENE_ID, sceneId).build(),
        )

        private fun enqueue(ctx: Context, uniqueName: String, data: Data) {
            val req = OneTimeWorkRequestBuilder<AcCommandWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setInputData(data)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.SECONDS)
                .build()
            // EN: REPLACE so a re-fired alarm supersedes any still-pending run for the same target.
            // DE: REPLACE, damit ein erneut ausgelöster Alarm einen noch ausstehenden Lauf desselben Ziels ersetzt.
            WorkManager.getInstance(ctx.applicationContext)
                .enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, req)
        }
    }
}
