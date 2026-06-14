package com.climapilot.free

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.climapilot.free.midea.AcState
import com.climapilot.free.midea.EnergyUsage
import com.climapilot.free.midea.MideaAc
import com.climapilot.free.midea.MideaAcSession
import com.climapilot.free.midea.MideaDevice
import com.climapilot.free.midea.MideaDiscovery
import com.climapilot.free.widget.WidgetRepo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class Status { Idle, Discovering, Connecting, Connected, Error }

/**
 * EN: Fan presets exposed in the UI. The value is the raw protocol fan byte (102 = auto).
 * DE: In der UI angebotene Lüfter-Vorgaben. Der Wert ist das rohe Lüfter-Byte des Protokolls (102 = Auto).
 */
enum class FanPreset(val labelRes: Int, val value: Int) {
    Auto(R.string.fan_auto, 102),
    Silent(R.string.fan_silent, 20),
    Low(R.string.fan_low, 40),
    Medium(R.string.fan_medium, 60),
    High(R.string.fan_high, 80),
    Turbo(R.string.fan_turbo, 100),
}

/**
 * EN: The single source of truth for the UI. Holds discovery results, the live device state and the
 *     user's desired control state, and exposes intent functions the Compose screens call. Network
 *     work runs in coroutines; a Mutex serialises access to the one socket.
 * DE: Die alleinige Wahrheitsquelle für die UI. Hält Suchergebnisse, den Live-Gerätezustand und den
 *     gewünschten Steuerzustand des Nutzers und stellt Funktionen bereit, die die Compose-Screens
 *     aufrufen. Netzwerkarbeit läuft in Coroutinen; ein Mutex serialisiert den Zugriff auf das eine Socket.
 */
class AcViewModel(app: Application) : AndroidViewModel(app) {

    /** EN: Push the current desired/live state to the home-screen widget(s). DE: Den aktuellen Soll-/Live-Zustand an die Homescreen-Widgets senden. */
    private fun publishWidget() {
        val dev = connectedDevice
        WidgetRepo.publish(
            getApplication(),
            present = dev != null,
            name = dev?.name?.ifBlank { "Midea" } ?: "Midea",
            powerOn = powerOn,
            mode = mode,
            targetTemp = tempC,
            indoorTemp = live?.indoorTemp,
            powerW = energy?.powerW?.takeIf { !it.isNaN() },
            device = dev,
        )
    }

    // EN: ---- discovery / connection ---- / DE: ---- Gerätesuche / Verbindung ----
    var devices by mutableStateOf<List<MideaDevice>>(emptyList()); private set
    var status by mutableStateOf(Status.Idle); private set
    var error by mutableStateOf<String?>(null); private set
    var connectedDevice by mutableStateOf<MideaDevice?>(null); private set
    var busy by mutableStateOf(false); private set

    // EN: ---- desired control state (mirrored from the session for the UI) ----
    // DE: ---- gewünschter Steuerzustand (für die UI aus der Sitzung gespiegelt) ----
    var powerOn by mutableStateOf(true); private set
    var mode by mutableStateOf(MideaAc.MODE_COOL); private set
    var tempC by mutableStateOf(24.0); private set
    var fan by mutableStateOf(60); private set
    var swing by mutableStateOf(false); private set
    var eco by mutableStateOf(false); private set
    var beep by mutableStateOf(false); private set
    var rate by mutableStateOf(MideaAc.RATE_OFF); private set

    // EN: ---- live readouts (read back from the device) ---- / DE: ---- Live-Anzeigen (vom Gerät zurückgelesen) ----
    var live by mutableStateOf<AcState?>(null); private set
    var energy by mutableStateOf<EnergyUsage?>(null); private set
    var rateLevels by mutableStateOf(0); private set     // EN: 0 = no gear support, 2, or 5 / DE: 0 = keine Gang-Unterstützung, 2 oder 5

    // EN: Device-specific capabilities (whether to show the toggle) + their current desired state.
    // DE: Gerätespezifische Fähigkeiten (ob der Schalter gezeigt wird) + ihr aktueller Soll-Zustand.
    var capAnion by mutableStateOf(false); private set
    var capSelfClean by mutableStateOf(false); private set
    var capOutSilent by mutableStateOf(false); private set
    var anion by mutableStateOf(false); private set
    var selfClean by mutableStateOf(false); private set
    var outSilent by mutableStateOf(false); private set

    // EN: Display preferences. DE: Anzeige-Einstellungen.
    var useFahrenheit by mutableStateOf(false); private set
    var pricePerKwh by mutableStateOf(0.0); private set

    // EN: Last custom sleep-timer duration, shown as a saved quick chip (0 = none).
    // DE: Letzte eigene Sleep-Timer-Dauer, als gespeicherter Schnell-Chip angezeigt (0 = keine).
    var sleepCustomMinutes by mutableStateOf(0); private set

    // EN: ---- quick scenes ---- one-tap presets of the full control state, persisted on device.
    // DE: ---- Schnell-Szenen ---- Ein-Tipp-Vorlagen des gesamten Steuerzustands, lokal gespeichert.
    var scenes by mutableStateOf<List<Scene>>(emptyList()); private set

    // ---- sleep timer ----
    var sleepTimerMinutes by mutableStateOf<Int?>(null); private set
    private var sleepJob: Job? = null

    private var session: MideaAcSession? = null
    private var refreshJob: Job? = null
    private val lock = Mutex()   // EN: serialise socket access / DE: Socket-Zugriff serialisieren

    init {
        // EN: Load saved scenes; on first run seed a few useful defaults and persist them once.
        // DE: Gespeicherte Szenen laden; beim ersten Start ein paar nützliche Vorgaben anlegen und einmalig speichern.
        val ctx = getApplication<Application>()
        scenes = SceneRepo.load(ctx) ?: seedScenes(ctx).also { SceneRepo.save(ctx, it) }
        // EN: Make sure any saved daily scene times are armed on every app start. DE: Sicherstellen, dass gespeicherte tägliche Szenenzeiten bei jedem App-Start aktiv sind.
        SceneScheduler.rescheduleAll(ctx, scenes)
        useFahrenheit = SettingsRepo.useFahrenheit(ctx)
        pricePerKwh = SettingsRepo.pricePerKwh(ctx)
        sleepCustomMinutes = SettingsRepo.sleepCustomMinutes(ctx)
        // EN: If a sleep-timer alarm is still pending, resume its on-screen countdown. DE: Falls ein Sleep-Timer-Alarm noch aussteht, dessen Countdown-Anzeige fortsetzen.
        restoreSleepTimer()
    }

    /** EN: Remember a custom sleep duration so it appears as a quick chip next time. DE: Eine eigene Sleep-Dauer merken, damit sie nächstes Mal als Schnell-Chip erscheint. */
    fun setSleepCustom(minutes: Int) {
        sleepCustomMinutes = minutes
        SettingsRepo.setSleepCustomMinutes(getApplication(), minutes)
    }

    /** EN: Switch temperature unit (°C/°F) and persist. DE: Temperatureinheit (°C/°F) umschalten und speichern. */
    fun setFahrenheit(value: Boolean) {
        useFahrenheit = value
        SettingsRepo.setUseFahrenheit(getApplication(), value)
    }

    /** EN: Set the price per kWh for cost estimates and persist. DE: Preis pro kWh für Kostenschätzung setzen und speichern. */
    fun updatePricePerKwh(value: Double) {
        pricePerKwh = value
        SettingsRepo.setPricePerKwh(getApplication(), value)
    }

    /**
     * EN: First-run preset scenes. These give new users something to tap immediately and double as
     *     examples of what a scene captures (power, mode, temperature, fan, eco, swing).
     * DE: Standard-Szenen beim ersten Start. Sie geben neuen Nutzern sofort etwas zum Antippen und
     *     dienen zugleich als Beispiele dafür, was eine Szene speichert (Ein/Aus, Modus, Temperatur,
     *     Lüfter, Eco, Swing).
     */
    private fun seedScenes(ctx: Context): List<Scene> = listOf(
        Scene(name = ctx.getString(R.string.scene_boost), powerOn = true, mode = MideaAc.MODE_COOL, tempC = 18.0, fan = 100, eco = false, swing = false),
        Scene(name = ctx.getString(R.string.scene_comfort), powerOn = true, mode = MideaAc.MODE_AUTO, tempC = 24.0, fan = 60, eco = false, swing = false),
        Scene(name = ctx.getString(R.string.scene_eco_night), powerOn = true, mode = MideaAc.MODE_COOL, tempC = 26.0, fan = 20, eco = true, swing = false),
    )

    /**
     * EN: Capture the current control state as a new named scene and persist it.
     * DE: Den aktuellen Steuerzustand als neue, benannte Szene erfassen und speichern.
     */
    fun saveCurrentAsScene(name: String) {
        val scene = Scene(
            name = name.trim().ifBlank { getApplication<Application>().getString(R.string.scene_default_name) },
            powerOn = powerOn, mode = mode, tempC = tempC, fan = fan, eco = eco, swing = swing,
        )
        scenes = scenes + scene
        SceneRepo.save(getApplication(), scenes)
        SceneScheduler.rescheduleAll(getApplication(), scenes)
    }

    /** EN: Delete a scene by id and persist. DE: Eine Szene per ID löschen und speichern. */
    fun deleteScene(id: String) {
        scenes = scenes.filterNot { it.id == id }
        SceneRepo.save(getApplication(), scenes)
        SceneScheduler.rescheduleAll(getApplication(), scenes)
    }

    /**
     * EN: Replace an existing scene (matched by id) with an edited copy and persist.
     * DE: Eine bestehende Szene (per ID) durch eine bearbeitete Kopie ersetzen und speichern.
     */
    fun updateScene(scene: Scene) {
        scenes = scenes.map { if (it.id == scene.id) scene else it }
        SceneRepo.save(getApplication(), scenes)
        SceneScheduler.rescheduleAll(getApplication(), scenes)
    }

    fun discover() {
        if (status == Status.Discovering) return
        error = null
        status = Status.Discovering
        viewModelScope.launch {
            try {
                val found = MideaDiscovery.discover(5000).filter { it.isAc || it.type == 0 }
                devices = found
                status = if (connectedDevice != null) Status.Connected else Status.Idle
                if (found.isEmpty()) error = "Keine Geräte gefunden. Gleiches WLAN? Sonst manuell hinzufügen."
            } catch (e: Exception) {
                status = Status.Error
                error = "Suche fehlgeschlagen: ${e.message}"
            }
        }
    }

    /**
     * EN: Add a device by hand (for when broadcast discovery isn't possible, e.g. the emulator).
     * DE: Ein Gerät von Hand hinzufügen (wenn Broadcast-Suche nicht möglich ist, z. B. im Emulator).
     */
    fun addManualDevice(ip: String, port: Int, id: Long, name: String) {
        val dev = MideaDevice(
            ip = ip, port = port, id = id, sn = "", name = name.ifBlank { "Midea $ip" },
            type = 0xAC, version = 3,
        )
        devices = (devices.filterNot { it.ip == ip } + dev)
    }

    fun connect(device: MideaDevice) {
        error = null
        status = Status.Connecting
        viewModelScope.launch {
            try {
                stopRefresh()
                session?.close()
                // EN: Use the cached token/key if we have one (offline), and persist any fresh one.
                // DE: Falls vorhanden, das gecachte Token/Schlüssel nutzen (offline) und ein frisches speichern.
                val ctx = getApplication<Application>()
                val s = MideaAcSession(
                    device,
                    cachedCreds = TokenRepo.load(ctx, device.id),
                    onCredsFetched = { t, k -> TokenRepo.save(ctx, device.id, device.name, device.ip, device.port, t, k) },
                )
                s.connect()
                session = s
                connectedDevice = device
                status = Status.Connected
                // EN: pull current state + capabilities right away / DE: aktuellen Zustand + Fähigkeiten sofort abrufen
                val caps = s.queryCapabilities()
                rateLevels = caps?.rateLevels ?: 0
                capAnion = caps?.anion == true
                capSelfClean = caps?.selfClean == true
                capOutSilent = caps?.outSilent == true
                refreshOnce()
                live?.let { syncFromState(it) }
                startRefresh()
                publishWidget()
            } catch (e: Exception) {
                status = Status.Error
                error = getApplication<Application>().getString(R.string.error_connect, e.message ?: "")
                session?.close(); session = null; connectedDevice = null
            }
        }
    }

    /**
     * EN: Open the control screen in a safe demo state: no session is created, so no command ever
     *     reaches a real device. Used for the emulator / UI preview.
     * DE: Den Steuerungs-Bildschirm in einem sicheren Demo-Zustand öffnen: Es wird keine Sitzung
     *     erstellt, sodass nie ein Befehl ein echtes Gerät erreicht. Für Emulator / UI-Vorschau.
     */
    fun connectDemo() {
        stopRefresh()
        session?.close(); session = null
        connectedDevice = MideaDevice(
            ip = "Demo", port = 6444, id = 0, sn = "",
            name = getApplication<Application>().getString(R.string.demo_device_name),
            type = 0xAC, version = 3,
        )
        status = Status.Connected
        rateLevels = 2
        // EN: Show all optional toggles in demo so the UI can be explored. DE: In der Demo alle optionalen Schalter zeigen, damit die UI erkundbar ist.
        capAnion = true; capSelfClean = true; capOutSilent = true
        anion = false; selfClean = false; outSilent = false
        live = AcState(
            powerOn = true, mode = MideaAc.MODE_COOL, targetTemp = 24.0, fanSpeed = 60,
            indoorTemp = 23.5, outdoorTemp = 29.0, errorCode = 0,
        )
        energy = EnergyUsage(powerW = 420.0, totalKwh = 137.4, currentKwh = 1.2)
        powerOn = true; mode = MideaAc.MODE_COOL; tempC = 24.0; fan = 60
        publishWidget()
    }

    fun disconnect() {
        stopRefresh()
        cancelSleepTimer()
        viewModelScope.launch { lock.withLock { session?.close() } }
        session = null
        connectedDevice = null
        live = null; energy = null
        status = Status.Idle
        publishWidget()
    }

    /**
     * EN: Copy device-reported state into the desired-state mirror so the UI matches reality.
     * DE: Den vom Gerät gemeldeten Zustand in den Soll-Zustand übernehmen, damit die UI der Realität entspricht.
     */
    private fun syncFromState(s: AcState) {
        powerOn = s.powerOn
        mode = s.mode.takeIf { it in 1..5 } ?: mode
        tempC = s.targetTemp
        fan = s.fanSpeed.takeIf { it in 1..102 } ?: fan
        session?.let { it.powerOn = powerOn; it.mode = mode; it.tempC = tempC; it.fan = fan }
    }

    // EN: ---- control actions ----
    //     The optimistic UI update always runs; the network command only fires when a real session
    //     exists. In demo mode (session == null) nothing is ever sent to a device.
    // DE: ---- Steueraktionen ----
    //     Die optimistische UI-Aktualisierung läuft immer; der Netzwerkbefehl wird nur ausgelöst, wenn
    //     eine echte Sitzung besteht. Im Demo-Modus (session == null) wird nie etwas an ein Gerät gesendet.
    private fun command(optimistic: () -> Unit = {}, block: suspend MideaAcSession.() -> Unit) {
        optimistic()
        publishWidget()
        val s = session ?: return
        viewModelScope.launch {
            busy = true
            try {
                lock.withLock { s.block() }
                error = null
            } catch (e: Exception) {
                error = getApplication<Application>().getString(R.string.error_command, e.message ?: "")
            } finally {
                busy = false
            }
        }
    }

    fun togglePower() { val v = !powerOn; command({ powerOn = v }) { setPower(v) } }
    fun applyMode(m: Int) = command({ mode = m }) { setMode(m) }
    fun nudgeTemp(delta: Double) { val v = (tempC + delta).coerceIn(16.0, 30.0); command({ tempC = v }) { setTemp(v) } }
    fun applyTemp(t: Double) { val v = t.coerceIn(16.0, 30.0); command({ tempC = v }) { setTemp(v) } }
    fun applyFan(value: Int) = command({ fan = value }) { setFan(value) }
    fun toggleSwing() { val v = !swing; command({ swing = v }) { setSwing(v) } }
    fun toggleEco() { val v = !eco; command({ eco = v }) { setEco(v) } }
    fun applyBeep(on: Boolean) = command({ beep = on; session?.beep = on }) { setBuzzer(on) }
    fun applyRate(value: Int) = command({ rate = value }) { setRate(value) }
    fun toggleAnion() { val v = !anion; command({ anion = v }) { setAnion(v) } }
    fun toggleSelfClean() { val v = !selfClean; command({ selfClean = v }) { setSelfClean(v) } }
    fun toggleOutdoorSilent() { val v = !outSilent; command({ outSilent = v }) { setOutdoorSilent(v) } }

    /**
     * EN: Apply a whole scene (power/mode/temp/fan/eco/swing) in one shot. The optimistic UI update
     *     runs immediately; the device command is sent in a single coherent SetState frame.
     * DE: Eine komplette Szene (Ein-Aus/Modus/Temp/Lüfter/Eco/Swing) auf einmal anwenden. Die
     *     optimistische UI-Aktualisierung erfolgt sofort; an das Gerät geht ein einziger,
     *     zusammenhängender SetState-Frame.
     */
    fun applyScene(scene: Scene) {
        command({
            powerOn = scene.powerOn; mode = scene.mode; tempC = scene.tempC
            fan = scene.fan; eco = scene.eco; swing = scene.swing
        }) {
            powerOn = scene.powerOn; mode = scene.mode; tempC = scene.tempC
            fan = scene.fan; eco = scene.eco; swing = if (scene.swing) 0x3F else 0
            apply()
        }
    }

    // EN: ---- live refresh loop (polls state + energy every few seconds) ----
    // DE: ---- Live-Aktualisierungsschleife (fragt Zustand + Energie alle paar Sekunden ab) ----
    private suspend fun refreshOnce() {
        val s = session ?: return
        lock.withLock {
            s.queryState()?.let { live = it }
            s.queryEnergy()?.let { energy = it }
        }
        publishWidget()
    }

    private fun startRefresh() {
        stopRefresh()
        refreshJob = viewModelScope.launch {
            while (true) {
                delay(6000)
                runCatching { refreshOnce() }
            }
        }
    }

    private fun stopRefresh() { refreshJob?.cancel(); refreshJob = null }

    fun refreshNow() = viewModelScope.launch { runCatching { refreshOnce() } }

    // EN: ---- sleep timer: power the unit off after N minutes ----
    //     The actual power-off is an AlarmManager alarm (see SleepTimerScheduler), so it still fires
    //     when the app is closed. The coroutine here only drives the on-screen remaining-time display.
    // DE: ---- Sleep-Timer: das Gerät nach N Minuten ausschalten ----
    //     Das eigentliche Ausschalten ist ein AlarmManager-Alarm (siehe SleepTimerScheduler), läuft
    //     also auch bei geschlossener App. Die Coroutine hier treibt nur die Restzeit-Anzeige.
    fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        val dev = connectedDevice ?: return
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        // EN: Demo device has no cached token; skip the real alarm but still show the countdown. DE: Das Demo-Gerät hat kein gecachtes Token; echten Alarm überspringen, aber Countdown trotzdem zeigen.
        if (dev.id != 0L) SleepTimerScheduler.schedule(getApplication(), dev.id, triggerAt)
        startSleepDisplay(triggerAt)
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel(); sleepJob = null
        sleepTimerMinutes = null
        SleepTimerScheduler.cancel(getApplication())
    }

    /** EN: Restore the countdown display from a pending alarm when the app reopens. DE: Die Countdown-Anzeige aus einem ausstehenden Alarm wiederherstellen, wenn die App neu öffnet. */
    private fun restoreSleepTimer() {
        val triggerAt = SleepTimerScheduler.triggerAt(getApplication()) ?: return
        if (triggerAt <= System.currentTimeMillis()) {
            // EN: Already due — the alarm has (or will have) handled the power-off. DE: Bereits fällig — der Alarm hat das Ausschalten erledigt (oder erledigt es).
            SleepTimerScheduler.clear(getApplication())
            return
        }
        startSleepDisplay(triggerAt)
    }

    /** EN: Tick the remaining minutes from an absolute trigger time until it elapses. DE: Die Restminuten aus einer absoluten Auslösezeit herunterzählen, bis sie verstrichen ist. */
    private fun startSleepDisplay(triggerAt: Long) {
        sleepJob?.cancel()
        sleepJob = viewModelScope.launch {
            while (true) {
                val remaining = triggerAt - System.currentTimeMillis()
                if (remaining <= 0) {
                    // EN: If the app is still connected at expiry, power off over the live session and
                    //     cancel the alarm — the alarm receiver would otherwise open a second, conflicting
                    //     connection to the same AC. When the app is closed, the alarm handles it instead.
                    // DE: Ist die App bei Ablauf noch verbunden, über die offene Sitzung ausschalten und
                    //     den Alarm abbrechen — sonst öffnet der Alarm-Receiver eine zweite, kollidierende
                    //     Verbindung zur selben Klima. Bei geschlossener App übernimmt stattdessen der Alarm.
                    if (session != null) {
                        SleepTimerScheduler.cancel(getApplication())
                        command({ powerOn = false }) { setPower(false) }
                    }
                    sleepTimerMinutes = null
                    break
                }
                // EN: round up so "1m" shows until the last minute actually elapses. DE: aufrunden, damit „1m" bis zum tatsächlichen Ablauf der letzten Minute angezeigt wird.
                sleepTimerMinutes = ((remaining + 59_999) / 60_000).toInt()
                delay(1_000)
            }
        }
    }

    override fun onCleared() {
        stopRefresh(); sleepJob?.cancel()
        session?.close()
    }
}
