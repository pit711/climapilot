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
import com.climapilot.free.midea.Group1Data
import com.climapilot.free.midea.Group2Data
import com.climapilot.free.midea.Group7Data
import com.climapilot.free.midea.MideaAc
import com.climapilot.free.midea.MideaAcSession
import com.climapilot.free.midea.MideaDevice
import com.climapilot.free.midea.MideaDiscovery
import com.climapilot.free.ir.IrRemote
import com.climapilot.free.ir.MideaIr
import com.climapilot.free.widget.WidgetRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

enum class Status { Idle, Discovering, Connecting, Connected, Error }

/**
 * EN: Fan presets exposed in the UI. The value is the raw protocol fan byte (102 = auto).
 * DE: In der UI angebotene Lüfter-Vorgaben. Der Wert ist das rohe Lüfter-Byte des Protokolls (102 = Auto).
 */
enum class FanPreset(val labelRes: Int, val value: Int) {
    Auto(R.string.fan_auto, 102),
    // EN: 1% — the lowest possible setting, handy for "fan only + minimal airflow" testing. DE: 1% — die niedrigste mögliche Stufe, praktisch zum Testen mit „nur Lüften + minimalem Luftstrom".
    Min(R.string.fan_min, 1),
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
            outdoorTemp = live?.outdoorTemp,
            powerW = energy?.powerW?.takeIf { !it.isNaN() },
            fan = fan,
            turbo = turbo,
            eco = eco,
            swing = swing,
            device = dev,
        )
        // EN: Log a throttled usage sample (power/temps/fan + enabled beta diagnostics) for the History
        //     charts — only when enabled. DE: Einen gedrosselten Messwert (Leistung/Temperaturen/Lüfter +
        //     aktivierte Beta-Diagnose) für die Verlaufs-Charts aufzeichnen — nur wenn aktiviert.
        if (dev != null && historyEnabled) {
            UsageHistory.record(
                getApplication(), dev.id, energy?.powerW, energy?.totalKwh, powerOn,
                live?.indoorTemp, live?.outdoorTemp, fan,
                compressorHz = group1?.compressorFrequency?.toDouble(),
                compressorW = group7?.compressorPower,
                fanRpm = group2?.indoorFanSpeed,
            )
        }
        // EN: Mirror the state to a paired Wear OS watch. DE: Den Zustand an eine gekoppelte Wear-OS-Uhr spiegeln.
        WearSync.publish(getApplication(), dev != null, powerOn, tempC)
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
    var turbo by mutableStateOf(false); private set
    var beep by mutableStateOf(false); private set
    var rate by mutableStateOf(MideaAc.RATE_OFF); private set

    // EN: ---- live readouts (read back from the device) ---- / DE: ---- Live-Anzeigen (vom Gerät zurückgelesen) ----
    var live by mutableStateOf<AcState?>(null); private set
    var energy by mutableStateOf<EnergyUsage?>(null); private set
    var rateLevels by mutableStateOf(0); private set     // EN: 0 = no gear support, 2, or 5 / DE: 0 = keine Gang-Unterstützung, 2 oder 5

    // EN: ---- Beta diagnostics (midea-msmart PR #278 group data) ---- opt-in extra telemetry, each
    //     gated by its own setting; null until first successfully read. DE: ---- Beta-Diagnose
    //     (midea-msmart PR #278 Gruppendaten) ---- freiwillige Zusatz-Telemetrie, je durch eine eigene
    //     Einstellung gesteuert; null, bis zum ersten erfolgreichen Lesen.
    var group1 by mutableStateOf<Group1Data?>(null); private set
    var group2 by mutableStateOf<Group2Data?>(null); private set
    var group7 by mutableStateOf<Group7Data?>(null); private set
    // EN: Enable flags mirrored from SettingsRepo so the refresh loop + UI react immediately. DE: Aus SettingsRepo gespiegelte Schalter, damit Refresh-Schleife + UI sofort reagieren.
    var diagGroup1 by mutableStateOf(true); private set
    var diagGroup2 by mutableStateOf(true); private set
    var diagGroup7 by mutableStateOf(true); private set
    // EN: Live-refresh poll interval in seconds (2–60, default 6); read by the refresh loop each cycle. DE: Poll-Intervall der Live-Aktualisierung in Sekunden (2–60, Standard 6); wird von der Refresh-Schleife je Zyklus gelesen.
    var pollIntervalSec by mutableStateOf(6); private set

    // EN: Device-specific capabilities (whether to show the toggle) + their current desired state.
    // DE: Gerätespezifische Fähigkeiten (ob der Schalter gezeigt wird) + ihr aktueller Soll-Zustand.
    var capAnion by mutableStateOf(false); private set
    var capSelfClean by mutableStateOf(false); private set
    var capOutSilent by mutableStateOf(false); private set
    var anion by mutableStateOf(false); private set
    var selfClean by mutableStateOf(false); private set
    var outSilent by mutableStateOf(false); private set
    // EN: LED-display state. The display command is toggle-only, but the on/off state is reported in the
    //     state frame, so this is read back from the device on connect + refresh (default "on" until read).
    // DE: LED-Anzeige-Zustand. Der Display-Befehl ist nur ein Umschalter, aber der Ein/Aus-Zustand wird im
    //     State-Frame gemeldet, daher wird er bei Connect + Refresh vom Gerät zurückgelesen (Vorgabe „an" bis gelesen).
    var display by mutableStateOf(true); private set

    // EN: Display preferences. DE: Anzeige-Einstellungen.
    var useFahrenheit by mutableStateOf(false); private set
    var pricePerKwh by mutableStateOf(0.0); private set

    // EN: ---- indoor-temperature calibration ---- a manual correction (kelvin, −5…+5) for units whose
    //     built-in sensor reads off the real room temperature. Loaded per device on connect; applied to
    //     every reading the app shows or records (see [calibrated]).
    // DE: ---- Innentemperatur-Kalibrierung ---- eine manuelle Korrektur (Kelvin, −5…+5) für Geräte, deren
    //     eingebauter Fühler neben der echten Raumtemperatur liegt. Beim Verbinden je Gerät geladen; auf
    //     jeden Wert angewandt, den die App anzeigt oder aufzeichnet (siehe [calibrated]).
    var indoorOffset by mutableStateOf(0.0); private set
    // EN: The raw, uncorrected sensor reading — kept so the calibration UI can show "measured → shown".
    // DE: Der rohe, unkorrigierte Fühlerwert — damit die Kalibrier-UI „gemessen → angezeigt" zeigen kann.
    var indoorTempRaw by mutableStateOf<Double?>(null); private set
    // EN: Auto power-off after the AC runs this many hours (0 = off). DE: Auto-Aus, nachdem die Klima so viele Stunden läuft (0 = aus).
    var maxRuntimeHours by mutableStateOf(0); private set
    // EN: Record the AC history + run the background poll (off by default). DE: Klima-Verlauf aufzeichnen + Hintergrund-Poll (standardmäßig aus).
    var historyEnabled by mutableStateOf(false); private set
    // EN: IR-remote mode — transmit-only (IR blaster), no LAN session, no readback; the state shown is assumed. DE: IR-Fernbedienungs-Modus — nur senden (IR-Blaster), keine LAN-Sitzung, kein Readback; der gezeigte Zustand ist angenommen.
    var irMode by mutableStateOf(false); private set
    // EN: IR special-command toggles. IR is one-way, so these reflect what was last sent, not a confirmed device state. DE: IR-Sonderbefehl-Umschalter. IR ist einweg, daher spiegeln sie das zuletzt Gesendete, nicht einen bestätigten Gerätezustand.
    var irQuiet by mutableStateOf(false); private set
    var irTurbo by mutableStateOf(false); private set
    var irEcono by mutableStateOf(false); private set
    var irSwing by mutableStateOf(false); private set

    // EN: Last custom sleep-timer duration, shown as a saved quick chip (0 = none).
    // DE: Letzte eigene Sleep-Timer-Dauer, als gespeicherter Schnell-Chip angezeigt (0 = keine).
    var sleepCustomMinutes by mutableStateOf(0); private set

    // EN: ---- quick scenes ---- one-tap presets of the full control state, persisted on device.
    // DE: ---- Schnell-Szenen ---- Ein-Tipp-Vorlagen des gesamten Steuerzustands, lokal gespeichert.
    var scenes by mutableStateOf<List<Scene>>(emptyList()); private set

    // EN: ---- weekly plan ---- recurring "apply scene X on these weekdays from–to" windows, run in the
    //     background even while idle (see PlanScheduler). DE: ---- Wochenplan ---- wiederkehrende Fenster
    //     „Szene X an diesen Wochentagen von–bis", laufen auch im Standby im Hintergrund (siehe PlanScheduler).
    var plan by mutableStateOf<List<PlanEntry>>(emptyList()); private set

    // EN: ---- in-app updater (GitHub build) ---- a newer release found on GitHub, plus check/download
    //     UI state. DE: ---- In-App-Updater (GitHub-Build) ---- ein auf GitHub gefundenes neueres Release plus
    //     Prüf-/Download-Zustand für die UI.
    var autoUpdateCheck by mutableStateOf(true); private set
    var updateChecking by mutableStateOf(false); private set
    var updateAvailable by mutableStateOf<UpdateChecker.Release?>(null); private set
    // EN: -1 = idle, 0..100 = download progress. DE: -1 = untätig, 0..100 = Download-Fortschritt.
    var updateProgress by mutableStateOf(-1); private set
    // EN: A short user-facing result line (up-to-date / error / hint). DE: Eine kurze Ergebniszeile für den Nutzer (aktuell / Fehler / Hinweis).
    var updateMessage by mutableStateOf<String?>(null); private set
    // EN: Don't keep re-popping the auto dialog after the user dismissed it this session. DE: Den Auto-Dialog nach dem Schließen in dieser Sitzung nicht erneut zeigen.
    private var updateDismissed = false

    // ---- sleep timer ----
    var sleepTimerMinutes by mutableStateOf<Int?>(null); private set
    private var sleepJob: Job? = null

    private var session: MideaAcSession? = null
    // EN: The setpoint we last handed to the unit. A read-back that merely echoes it must not overwrite
    //     the user's target — that matters when the calibration compensation had to be clamped at the
    //     16–30 °C limit and the two therefore no longer differ by exactly the compensation.
    // DE: Der zuletzt an das Gerät übergebene Sollwert. Ein Rücklesen, das ihn nur widerspiegelt, darf das
    //     Ziel des Nutzers nicht überschreiben — wichtig, wenn die Kalibrier-Kompensation an der Grenze
    //     16–30 °C begrenzt werden musste und beide sich daher nicht mehr exakt um sie unterscheiden.
    private var lastSentDeviceTemp: Double? = null
    private var refreshJob: Job? = null
    private val lock = Mutex()   // EN: serialise socket access / DE: Socket-Zugriff serialisieren

    init {
        // EN: Load saved scenes; on first run seed a few useful defaults and persist them once.
        // DE: Gespeicherte Szenen laden; beim ersten Start ein paar nützliche Vorgaben anlegen und einmalig speichern.
        val ctx = getApplication<Application>()
        scenes = SceneRepo.load(ctx) ?: seedScenes(ctx).also { SceneRepo.save(ctx, it) }
        // EN: Make sure any saved daily scene times are armed on every app start. DE: Sicherstellen, dass gespeicherte tägliche Szenenzeiten bei jedem App-Start aktiv sind.
        SceneScheduler.rescheduleAll(ctx, scenes)
        // EN: Load the weekly plan and re-arm its next event (arm only — opening the app never switches
        //     the AC). DE: Den Wochenplan laden und sein nächstes Ereignis neu setzen (nur setzen — das
        //     Öffnen der App schaltet die Klima nie).
        plan = PlanRepo.load(ctx) ?: emptyList()
        PlanScheduler.reschedule(ctx)
        // EN: Quietly check GitHub for a newer build on launch (throttled, opt-out). DE: Beim Start leise auf GitHub nach einem neueren Build prüfen (gedrosselt, abschaltbar).
        autoUpdateCheck = SettingsRepo.autoUpdateCheck(ctx)
        maybeAutoCheckUpdate()
        useFahrenheit = SettingsRepo.useFahrenheit(ctx)
        pricePerKwh = SettingsRepo.pricePerKwh(ctx)
        sleepCustomMinutes = SettingsRepo.sleepCustomMinutes(ctx)
        maxRuntimeHours = SettingsRepo.maxRuntimeHours(ctx)
        historyEnabled = SettingsRepo.historyEnabled(ctx)
        beep = SettingsRepo.beep(ctx)
        diagGroup1 = SettingsRepo.diagGroup1(ctx)
        diagGroup2 = SettingsRepo.diagGroup2(ctx)
        diagGroup7 = SettingsRepo.diagGroup7(ctx)
        pollIntervalSec = SettingsRepo.pollIntervalSec(ctx)
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
     * EN: Apply the indoor-temperature calibration to a device reading. This is the one place the
     *     correction is added, so every consumer downstream — hero readout, widget, history — sees the
     *     same corrected value. The untouched sensor value is kept in [indoorTempRaw] for the
     *     calibration UI.
     * DE: Die Innentemperatur-Kalibrierung auf einen Gerätewert anwenden. Das ist die einzige Stelle, an
     *     der die Korrektur addiert wird — alle Abnehmer (Hero-Anzeige, Widget, Verlauf) sehen damit
     *     denselben korrigierten Wert. Der unveränderte Fühlerwert bleibt für die Kalibrier-UI in
     *     [indoorTempRaw].
     */
    private fun calibrated(s: AcState): AcState {
        indoorTempRaw = s.indoorTemp
        val raw = s.indoorTemp ?: return s
        return if (indoorOffset == 0.0) s else s.copy(indoorTemp = raw + indoorOffset)
    }

    /**
     * EN: Set the indoor-temperature correction for the connected device and persist it. The reading
     *     already on screen is re-corrected right away, so ± taps move the number live instead of only
     *     after the next poll. When the correction changes the whole-degree setpoint shift, the target is
     *     re-sent immediately — otherwise the unit would keep regulating to the old setpoint until the
     *     next temperature tap. Without a device there is nothing to calibrate, so the call is a no-op.
     * DE: Die Innentemperatur-Korrektur für das verbundene Gerät setzen und speichern. Der bereits
     *     angezeigte Wert wird sofort neu korrigiert, sodass ±-Tipper die Zahl live bewegen statt erst
     *     beim nächsten Poll. Ändert die Korrektur die Sollwert-Verschiebung in ganzen Grad, wird das Ziel
     *     sofort neu gesendet — sonst würde das Gerät bis zum nächsten Temperatur-Tipper weiter auf den
     *     alten Sollwert regeln. Ohne Gerät gibt es nichts zu kalibrieren — der Aufruf tut dann nichts.
     */
    fun updateIndoorOffset(value: Double) {
        val dev = connectedDevice ?: return
        val shiftBefore = tempCompensation
        indoorOffset = value.coerceIn(-SettingsRepo.INDOOR_OFFSET_MAX, SettingsRepo.INDOOR_OFFSET_MAX)
        SettingsRepo.setIndoorOffset(getApplication(), dev.id, indoorOffset)
        indoorTempRaw?.let { raw -> live = live?.copy(indoorTemp = raw + indoorOffset) }
        if (tempCompensation != shiftBefore) sendTemp(tempC) else publishWidget()
    }

    /** EN: Set the "auto power-off after N hours" safety duration and persist (0 = off). DE: Die „Auto-Aus nach N Stunden"-Sicherheitsdauer setzen und speichern (0 = aus). */
    fun updateMaxRuntimeHours(value: Int) {
        maxRuntimeHours = value.coerceIn(0, 24)
        SettingsRepo.setMaxRuntimeHours(getApplication(), maxRuntimeHours)
    }

    /** EN: Toggle history recording + the ~15 min background poll, and persist. DE: Verlaufs-Aufzeichnung + ~15-min-Hintergrund-Poll umschalten und speichern. */
    fun updateHistoryEnabled(value: Boolean) {
        historyEnabled = value
        SettingsRepo.setHistoryEnabled(getApplication(), value)
        HistoryPollWorker.setEnabled(getApplication(), value)
    }

    /** EN: Toggle Group 1 beta diagnostics (compressor + refrigerant temps) and persist; clears stale data when off. DE: Gruppe-1-Beta-Diagnose (Kompressor + Kältekreis-Temp.) umschalten und speichern; löscht alte Daten beim Ausschalten. */
    fun updateDiagGroup1(value: Boolean) {
        diagGroup1 = value
        SettingsRepo.setDiagGroup1(getApplication(), value)
        if (!value) group1 = null
    }

    /** EN: Toggle Group 2 beta diagnostics (indoor fan + water pump) and persist. DE: Gruppe-2-Beta-Diagnose (Innenlüfter + Wasserpumpe) umschalten und speichern. */
    fun updateDiagGroup2(value: Boolean) {
        diagGroup2 = value
        SettingsRepo.setDiagGroup2(getApplication(), value)
        if (!value) group2 = null
    }

    /** EN: Toggle Group 7 beta diagnostics (outdoor-unit power) and persist. DE: Gruppe-7-Beta-Diagnose (Außengerät-Leistung) umschalten und speichern. */
    fun updateDiagGroup7(value: Boolean) {
        diagGroup7 = value
        SettingsRepo.setDiagGroup7(getApplication(), value)
        if (!value) group7 = null
    }

    /** EN: Set the live-refresh poll interval (seconds, 2–60) and persist; the loop picks it up next cycle. DE: Das Poll-Intervall der Live-Aktualisierung setzen (Sekunden, 2–60) und speichern; die Schleife übernimmt es im nächsten Zyklus. */
    fun updatePollInterval(value: Int) {
        pollIntervalSec = value.coerceIn(2, 60)
        SettingsRepo.setPollIntervalSec(getApplication(), pollIntervalSec)
    }

    /** EN: Toggle the automatic update check on launch and persist (named update* to avoid the JVM setter clash). DE: Den automatischen Update-Check beim Start umschalten und speichern (update*-Name wegen JVM-Setter-Kollision). */
    fun updateAutoUpdateCheck(value: Boolean) {
        autoUpdateCheck = value
        SettingsRepo.setAutoUpdateCheck(getApplication(), value)
    }

    /** EN: Auto-check at most once every 12 h; never nags about being up to date or about errors. DE: Auto-Check höchstens alle 12 h; meldet nie „aktuell" oder Fehler. */
    private fun maybeAutoCheckUpdate() {
        if (!autoUpdateCheck || updateDismissed) return
        val last = SettingsRepo.lastUpdateCheck(getApplication())
        if (System.currentTimeMillis() - last < 12 * 60 * 60 * 1000L) return
        checkForUpdates(manual = false)
    }

    /**
     * EN: Check GitHub for a newer release. When [manual] is true (the Settings button) the result is
     *     always reported (up to date / error); an auto-check stays silent unless an update is found.
     * DE: GitHub auf ein neueres Release prüfen. Bei [manual] = true (Einstellungen-Knopf) wird das
     *     Ergebnis immer gemeldet (aktuell / Fehler); ein Auto-Check bleibt still, außer es gibt ein Update.
     */
    fun checkForUpdates(manual: Boolean) {
        if (updateChecking) return
        val ctx = getApplication<Application>()
        updateChecking = true
        updateMessage = null
        viewModelScope.launch {
            when (val r = UpdateChecker.check(ctx)) {
                is UpdateChecker.CheckResult.Available -> updateAvailable = r.release
                is UpdateChecker.CheckResult.UpToDate ->
                    if (manual) updateMessage = ctx.getString(R.string.update_uptodate, UpdateChecker.installedVersion(ctx))
                is UpdateChecker.CheckResult.Failed ->
                    if (manual) updateMessage = ctx.getString(R.string.update_error)
            }
            updateChecking = false
            SettingsRepo.setLastUpdateCheck(ctx, System.currentTimeMillis())
        }
    }

    /** EN: Dismiss the available-update prompt for this session. DE: Den Update-Hinweis für diese Sitzung schließen. */
    fun dismissUpdate() {
        updateAvailable = null
        updateDismissed = true
    }

    /**
     * EN: Download the available update and hand it to the system installer. Verifies the APK signer
     *     before installing; if "install unknown apps" isn't granted yet, sends the user to that setting.
     * DE: Das verfügbare Update laden und an den System-Installer übergeben. Prüft den APK-Signierer vor
     *     der Installation; ist „Unbekannte Apps installieren" noch nicht erteilt, wird der Nutzer dorthin geführt.
     */
    fun downloadAndInstallUpdate() {
        val release = updateAvailable ?: return
        if (updateProgress >= 0) return
        val ctx = getApplication<Application>()
        if (!UpdateChecker.canInstall(ctx)) {
            updateMessage = ctx.getString(R.string.update_need_permission)
            UpdateChecker.requestInstallPermission(ctx)
            return
        }
        updateMessage = null
        updateProgress = 0
        viewModelScope.launch {
            val file = UpdateChecker.download(ctx, release) { p -> updateProgress = p }
            updateProgress = -1
            when {
                file == null -> updateMessage = ctx.getString(R.string.update_failed)
                !UpdateChecker.verifyApk(ctx, file) -> updateMessage = ctx.getString(R.string.update_verify_failed)
                else -> UpdateChecker.installApk(ctx, file)
            }
        }
    }

    /**
     * EN: When the AC is switched on, arm a max-runtime off-timer if enabled and none is pending — the
     *     local equivalent of "turn it off when I'm away" (a hard ceiling on how long it runs). Skipped
     *     in demo (no real device) and if the user already set their own sleep timer.
     * DE: Beim Einschalten der Klima einen Max-Laufzeit-Off-Timer aktivieren, falls eingeschaltet und
     *     keiner aussteht — die lokale Entsprechung von „aus, wenn ich weg bin" (harte Obergrenze für die
     *     Laufzeit). Im Demo (kein echtes Gerät) und bei bereits gesetztem eigenem Sleep-Timer übersprungen.
     */
    private fun maybeArmMaxRuntime() {
        val dev = connectedDevice ?: return
        if (dev.id == 0L || maxRuntimeHours <= 0) return
        if (SleepTimerScheduler.triggerAt(getApplication()) != null) return
        startSleepTimer(maxRuntimeHours * 60)
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
        // EN: Plan entries that referenced this scene now resolve to nothing — re-arm so they drop out.
        // DE: Plan-Einträge, die diese Szene nutzten, laufen jetzt ins Leere — neu setzen, damit sie wegfallen.
        PlanScheduler.reschedule(getApplication())
    }

    /**
     * EN: Replace an existing scene (matched by id) with an edited copy and persist.
     * DE: Eine bestehende Szene (per ID) durch eine bearbeitete Kopie ersetzen und speichern.
     */
    fun updateScene(scene: Scene) {
        scenes = scenes.map { if (it.id == scene.id) scene else it }
        SceneRepo.save(getApplication(), scenes)
        SceneScheduler.rescheduleAll(getApplication(), scenes)
        // EN: A scene's settings may have changed — re-arm the plan so it picks up the edit. DE: Die
        //     Einstellungen einer Szene können sich geändert haben — den Plan neu setzen, damit er die Änderung übernimmt.
        PlanScheduler.reschedule(getApplication())
    }

    /**
     * EN: Add a new weekly-plan entry or replace an existing one (matched by id), persist it and re-arm
     *     the next plan event. Saving never sends a command to the AC — the plan only acts at its
     *     scheduled window boundaries — so editing the plan can never switch a running unit by surprise.
     * DE: Einen neuen Wochenplan-Eintrag hinzufügen oder einen bestehenden (per ID) ersetzen, speichern
     *     und das nächste Plan-Ereignis neu setzen. Das Speichern sendet nie einen Befehl an die Klima —
     *     der Plan wirkt nur an seinen geplanten Fenstergrenzen — ein laufendes Gerät kann durch das
     *     Bearbeiten also nie überraschend geschaltet werden.
     */
    fun savePlanEntry(entry: PlanEntry) {
        plan = if (plan.any { it.id == entry.id }) {
            plan.map { if (it.id == entry.id) entry else it }
        } else {
            plan + entry
        }
        PlanRepo.save(getApplication(), plan)
        PlanScheduler.reschedule(getApplication())
    }

    /** EN: Delete a weekly-plan entry by id, persist and re-arm. DE: Einen Wochenplan-Eintrag per ID löschen, speichern und neu setzen. */
    fun deletePlanEntry(id: String) {
        plan = plan.filterNot { it.id == id }
        PlanRepo.save(getApplication(), plan)
        PlanScheduler.reschedule(getApplication())
    }

    /** EN: Enable/disable a plan entry (kept in the list either way) and re-arm. DE: Einen Plan-Eintrag aktivieren/deaktivieren (bleibt so oder so in der Liste) und neu setzen. */
    fun setPlanEntryEnabled(id: String, enabled: Boolean) {
        plan = plan.map { if (it.id == id) it.copy(enabled = enabled) else it }
        PlanRepo.save(getApplication(), plan)
        PlanScheduler.reschedule(getApplication())
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
                    // EN: Match cached creds by id OR ip (issue #8: imported tokens may use a different id encoding). DE: Gecachte Zugangsdaten per ID ODER IP finden (Issue #8: importierte Tokens nutzen evtl. eine andere ID-Kodierung).
                    cachedCreds = TokenRepo.load(ctx, device.id, device.ip),
                    onCredsFetched = { t, k -> TokenRepo.save(ctx, device.id, device.name, device.ip, device.port, t, k) },
                )
                s.connect()
                // EN: Carry the persisted beep preference into the new session so power on/off keeps
                //     chirping after a (re)connect — otherwise a fresh session always starts silent.
                // DE: Die gespeicherte Beep-Einstellung in die neue Sitzung übernehmen, damit Ein/Aus auch
                //     nach einem (Neu-)Connect quittiert — sonst startet eine frische Sitzung immer stumm.
                s.beep = beep
                session = s
                connectedDevice = device
                status = Status.Connected
                // EN: Load this unit's temperature calibration before the first read, so the very first
                //     reading shown is already corrected. DE: Die Temperatur-Kalibrierung dieses Geräts vor
                //     dem ersten Lesen laden, damit schon der erste angezeigte Wert korrigiert ist.
                indoorOffset = SettingsRepo.indoorOffset(ctx, device.id)
                // EN: pull current state + capabilities right away / DE: aktuellen Zustand + Fähigkeiten sofort abrufen
                val caps = s.queryCapabilities()
                rateLevels = caps?.rateLevels ?: 0
                capAnion = caps?.anion == true
                capSelfClean = caps?.selfClean == true
                capOutSilent = caps?.outSilent == true
                // EN: Read back the ACTUAL outdoor-silent state so the toggle matches the device after a
                //     (re)connect (issue #6). It's a capability-gated property, not part of the basic state.
                // DE: Den ECHTEN Außengerät-Leise-Zustand zurücklesen, damit der Schalter nach einem
                //     (Neu-)Connect zum Gerät passt (Issue #6). Kapazitätsabhängige Eigenschaft, nicht im Basiszustand.
                if (capOutSilent) s.queryOutdoorSilent()?.let { outSilent = it }
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
        anion = false; selfClean = false; outSilent = false; display = true
        // EN: The demo unit gets its own calibration slot, so the feature can be tried without hardware.
        // DE: Das Demo-Gerät bekommt einen eigenen Kalibrier-Platz, damit das Feature ohne Hardware ausprobierbar ist.
        indoorOffset = SettingsRepo.indoorOffset(getApplication(), 0L)
        live = calibrated(
            AcState(
                powerOn = true, mode = MideaAc.MODE_COOL, targetTemp = 24.0, fanSpeed = 60,
                indoorTemp = 23.5, outdoorTemp = 29.0, errorCode = 0,
            )
        )
        energy = EnergyUsage(powerW = 420.0, totalKwh = 137.4, currentKwh = 1.2)
        // EN: Plausible beta-diagnostics sample so the Status card can be explored once a group is enabled.
        // DE: Plausible Beta-Diagnose-Beispielwerte, damit die Status-Karte bei aktivierter Gruppe erkundbar ist.
        group1 = Group1Data(
            compressorFrequency = 28, targetCompressorFrequency = 25,
            compressorCurrent = 1, compressorVoltage = 232,
            tempIndoorCoil = 20.5, tempEvaporator = 4.0,
            tempCondenser = 26.0, tempOutdoor = 19.0, tempDischargePipe = 36,
        )
        group2 = Group2Data(targetIndoorFanSpeed = 416, indoorFanSpeed = 424, waterPumpRunning = false)
        group7 = Group7Data(compressorPower = 268.0)
        powerOn = true; mode = MideaAc.MODE_COOL; tempC = 24.0; fan = 60
        publishWidget()
    }

    fun disconnect() {
        stopRefresh()
        cancelSleepTimer()
        viewModelScope.launch { lock.withLock { session?.close() } }
        session = null
        irMode = false
        connectedDevice = null
        live = null; energy = null; indoorTempRaw = null; indoorOffset = 0.0
        group1 = null; group2 = null; group7 = null
        status = Status.Idle
        publishWidget()
    }

    /**
     * EN: Enter IR-remote mode: a transmit-only session that drives any Midea AC in line of sight via the
     *     phone's IR blaster. No LAN, no token, no polling — the shown state is what we last commanded
     *     (IR is one-way). Live readouts (indoor/outdoor/power) are unavailable here.
     * DE: In den IR-Fernbedienungs-Modus wechseln: eine reine Sende-Sitzung, die jede Midea-Klima in
     *     Sichtlinie über den IR-Blaster des Handys steuert. Kein LAN, kein Token, kein Polling — der
     *     gezeigte Zustand ist das zuletzt Befohlene (IR ist Einweg). Live-Werte (Innen/Außen/Leistung) fehlen.
     */
    fun enterIrMode() {
        stopRefresh()
        cancelSleepTimer()
        viewModelScope.launch { lock.withLock { session?.close() } }
        session = null
        irMode = true
        val ctx = getApplication<Application>()
        connectedDevice = MideaDevice(
            ip = "IR", port = 0, id = -1L, sn = "", name = ctx.getString(R.string.ir_remote_title),
            type = 0xAC, version = 3,
        )
        status = Status.Connected
        rateLevels = 0
        capAnion = false; capSelfClean = false; capOutSilent = false
        live = null; energy = null; indoorTempRaw = null; indoorOffset = 0.0
        // EN: Restore the last assumed IR state so the remote "remembers" what it last sent. We only
        //     restore the on-screen state — nothing is transmitted on entry, so opening IR mode never
        //     changes the unit by surprise. DE: Den zuletzt angenommenen IR-Zustand wiederherstellen,
        //     damit sich die Fernbedienung das zuletzt Gesendete „merkt". Es wird nur der Bildschirm-
        //     zustand wiederhergestellt — beim Eintritt wird nichts gesendet, das Öffnen des IR-Modus
        //     verändert das Gerät also nie überraschend.
        val s = IrStateRepo.load(ctx)
        powerOn = s.powerOn; mode = s.mode; tempC = s.tempC; fan = s.fan
        irQuiet = s.quiet; irTurbo = s.turbo; irEcono = s.econo; irSwing = s.swing
        error = null
        publishWidget()
    }

    /** EN: Transmit the current assumed state as one Midea IR frame, then remember it. DE: Den aktuellen angenommenen Zustand als einen Midea-IR-Frame senden und ihn merken. */
    private fun transmitIrState() {
        val ctx = getApplication<Application>()
        val pattern = MideaIr.pattern(
            powerOn = powerOn,
            mode = MideaIr.modeFromApp(mode),
            tempC = tempC.roundToInt().coerceIn(17, 30),
            fan = MideaIr.fanFromApp(fan),
        )
        viewModelScope.launch(Dispatchers.IO) {
            if (!IrRemote.transmit(ctx, pattern)) {
                error = ctx.getString(R.string.ir_no_emitter)
            }
        }
        persistIrState()
    }

    /** EN: Transmit a raw IR special-command frame (Quiet/Turbo/Econo/Swing toggles). DE: Einen rohen IR-Sonderbefehl-Frame senden (Quiet/Turbo/Econo/Swing-Umschalter). */
    private fun transmitIrRaw(pattern: IntArray) {
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            if (!IrRemote.transmit(ctx, pattern)) {
                error = ctx.getString(R.string.ir_no_emitter)
            }
        }
    }

    /** EN: Save the current assumed IR state so it survives leaving/re-entering IR mode and app restarts. DE: Den aktuellen angenommenen IR-Zustand speichern, damit er das Verlassen/erneute Betreten des IR-Modus und App-Neustarts überlebt. */
    private fun persistIrState() {
        IrStateRepo.save(getApplication(), IrStateRepo.IrState(
            powerOn = powerOn, mode = mode, tempC = tempC, fan = fan,
            quiet = irQuiet, turbo = irTurbo, econo = irEcono, swing = irSwing,
        ))
    }

    // EN: IR special-command toggles. IR is one-way: Quiet has explicit on/off frames, while
    //     Turbo/Econo/Swing are single toggle frames that flip the unit on every press. The shown
    //     on/off is therefore our best guess, seeded from the last persisted state.
    // DE: IR-Sonderbefehl-Umschalter. IR ist einweg: Quiet hat eigene An/Aus-Frames, Turbo/Econo/Swing
    //     sind einzelne Toggle-Frames, die das Gerät bei jedem Druck umschalten. Die gezeigte An/Aus ist
    //     daher die beste Annahme, aus dem zuletzt gespeicherten Zustand vorbelegt.
    fun toggleIrQuiet() { irQuiet = !irQuiet; transmitIrRaw(if (irQuiet) MideaIr.quietOn() else MideaIr.quietOff()); persistIrState() }
    fun toggleIrTurbo() { irTurbo = !irTurbo; transmitIrRaw(MideaIr.toggleTurbo()); persistIrState() }
    fun toggleIrEcono() { irEcono = !irEcono; transmitIrRaw(MideaIr.toggleEcono()); persistIrState() }
    fun toggleIrSwing() { irSwing = !irSwing; transmitIrRaw(MideaIr.toggleSwing()); persistIrState() }

    /**
     * EN: Copy device-reported state into the desired-state mirror so the UI matches reality.
     * DE: Den vom Gerät gemeldeten Zustand in den Soll-Zustand übernehmen, damit die UI der Realität entspricht.
     */
    private fun syncFromState(s: AcState) {
        powerOn = s.powerOn
        mode = s.mode.takeIf { it in 1..5 } ?: mode
        // EN: The unit reports its own setpoint; add the calibration compensation back to get the room
        //     temperature the user asked for. When it is only echoing what we just sent, the user's value
        //     stands — otherwise a clamped compensation would drag the displayed target along.
        // DE: Das Gerät meldet seinen eigenen Sollwert; die Kalibrier-Kompensation wieder addieren ergibt
        //     die vom Nutzer gewünschte Raumtemperatur. Spiegelt es nur das eben Gesendete, bleibt der
        //     Nutzerwert stehen — sonst würde eine begrenzte Kompensation das angezeigte Ziel mitziehen.
        if (s.targetTemp != lastSentDeviceTemp) {
            tempC = (s.targetTemp + tempCompensation).coerceIn(16.0, 30.0)
        }
        fan = s.fanSpeed.takeIf { it in 1..102 } ?: fan
        syncOptionsFromState(s)
        // EN: The session mirrors the *device* setpoint — it is what goes back out in the next frame.
        // DE: Die Sitzung spiegelt den *Geräte*-Sollwert — er geht im nächsten Frame wieder hinaus.
        session?.let { it.powerOn = powerOn; it.mode = mode; it.tempC = s.targetTemp; it.fan = fan }
    }

    /**
     * EN: Copy the device-reported option states (swing/eco/ionizer/display) into the UI + session, so the
     *     toggles always match the unit — including changes made on the physical remote. The ionizer is
     *     only adopted when the unit reports the capability. Self-clean / outdoor-silent / gear aren't in
     *     the basic state frame, so they stay best-effort assumed.
     * DE: Die vom Gerät gemeldeten Optionszustände (Swing/Eco/Ionisierer/Display) in UI + Sitzung
     *     übernehmen, damit die Schalter immer zum Gerät passen — auch bei Änderungen an der Fernbedienung.
     *     Der Ionisierer wird nur übernommen, wenn das Gerät die Fähigkeit meldet. Selbstreinigung /
     *     Außen-Leise / Gang stehen nicht im Basis-Frame und bleiben daher Best-Effort-Annahme.
     */
    private fun syncOptionsFromState(s: AcState) {
        swing = s.swingOn
        eco = s.eco
        turbo = s.turbo
        display = s.displayOn
        if (capAnion) anion = s.anion
        session?.let {
            it.swing = if (swing) 0x3F else 0
            it.eco = eco
            it.turbo = turbo
            it.anion = anion
        }
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
        // EN: IR mode: no LAN session — transmit the full assumed state over the IR blaster instead. DE: IR-Modus: keine LAN-Sitzung — stattdessen den vollen angenommenen Zustand über den IR-Blaster senden.
        if (irMode) { transmitIrState(); return }
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

    fun togglePower() { val v = !powerOn; command({ powerOn = v }) { setPower(v) }; if (v) maybeArmMaxRuntime() }
    fun applyMode(m: Int) = command({ mode = m }) { setMode(m) }

    /**
     * EN: Snap a setpoint to whole degrees within the supported range. Midea units only accept integer
     *     setpoints: the protocol does carry a half-degree bit, but the AC ignores it and reports the
     *     value back rounded — so a ".5" step looked like it had applied and then silently snapped back
     *     on the next poll. The app therefore steps in whole degrees everywhere. IR is integer-only too
     *     and additionally can't address anything below 17 °C.
     * DE: Ein Soll auf ganze Grad im unterstützten Bereich einrasten. Midea-Geräte nehmen nur ganzzahlige
     *     Sollwerte an: Das Protokoll führt zwar ein Halbgrad-Bit mit, die Klima ignoriert es aber und
     *     meldet den Wert gerundet zurück — ein „,5"-Schritt sah dadurch aus, als hätte er gegriffen, und
     *     sprang beim nächsten Poll still zurück. Die App arbeitet daher überall in ganzen Grad. IR ist
     *     ebenfalls ganzzahlig und kommt zusätzlich nicht unter 17 °C.
     */
    private fun snapTemp(t: Double, min: Double = 16.0): Double =
        t.roundToInt().toDouble().coerceIn(min, 30.0)

    /**
     * EN: The calibration expressed as a whole-degree setpoint shift. The unit regulates against its own
     *     sensor, so a sensor that reads [indoorOffset] too low makes it stop [indoorOffset] short of the
     *     room temperature the user asked for. Handing it a setpoint lowered by the same amount cancels
     *     that out. Rounded to whole degrees because that is all the unit accepts — a 0.5 K calibration
     *     still refines the *reading*, it just can't be dialled into the setpoint.
     * DE: Die Kalibrierung als Sollwert-Verschiebung in ganzen Grad. Das Gerät regelt gegen seinen eigenen
     *     Fühler; misst dieser um [indoorOffset] zu niedrig, hört es also um [indoorOffset] vor der vom
     *     Nutzer gewünschten Raumtemperatur auf. Ein um denselben Betrag abgesenkter Sollwert hebt das
     *     auf. Auf ganze Grad gerundet, weil das Gerät nur die annimmt — eine 0,5-K-Kalibrierung
     *     verfeinert weiterhin die *Anzeige*, lässt sich nur nicht in den Sollwert einrechnen.
     */
    private val tempCompensation: Int get() = indoorOffset.roundToInt()

    /** EN: Turn a desired room temperature into the setpoint the unit must be given. DE: Eine gewünschte Raumtemperatur in den Sollwert übersetzen, den das Gerät bekommen muss. */
    private fun deviceTemp(userTemp: Double): Double =
        (userTemp - tempCompensation).roundToInt().toDouble().coerceIn(16.0, 30.0)

    /** EN: What the unit is actually being asked to hold — shown in the calibration card. DE: Worauf das Gerät tatsächlich geregelt wird — in der Kalibrier-Karte angezeigt. */
    val deviceTargetTemp: Double get() = deviceTemp(tempC)

    /**
     * EN: Send a desired room temperature: the UI keeps the user's value, the unit gets the compensated
     *     one. Both go through here so no path can forget the calibration.
     * DE: Eine gewünschte Raumtemperatur senden: Die UI behält den Nutzerwert, das Gerät bekommt den
     *     kompensierten. Beide Wege laufen hierüber, damit keiner die Kalibrierung vergessen kann.
     */
    private fun sendTemp(userTemp: Double) {
        val dev = deviceTemp(userTemp)
        lastSentDeviceTemp = dev
        command({ tempC = userTemp }) { setTemp(dev) }
    }

    // EN: Only the direction matters — the step is always 1 K because the unit accepts nothing else
    //     (see snapTemp). DE: Nur die Richtung zählt — der Schritt ist immer 1 K, weil das Gerät nichts
    //     anderes annimmt (siehe snapTemp).
    fun nudgeTemp(direction: Int) =
        sendTemp(snapTemp(tempC + (if (direction > 0) 1.0 else -1.0), min = if (irMode) 17.0 else 16.0))

    fun applyTemp(t: Double) = sendTemp(snapTemp(t))
    fun applyFan(value: Int) = command({ fan = value }) { setFan(value) }
    fun toggleSwing() { val v = !swing; command({ swing = v }) { setSwing(v) } }
    /**
     * EN: Toggle eco/energy-saving. The unit only accepts eco at a target temperature of >= 24 °C
     *     (energy-saving lock); below that it silently ignores the eco bit and the toggle would snap back
     *     off on the next refresh. So when enabling eco we raise the setpoint to 24 °C if it's lower —
     *     exactly what the Midea remote/app does — so eco actually takes effect.
     * DE: Eco/Energiesparen umschalten. Das Gerät akzeptiert Eco nur bei Solltemperatur >= 24 °C
     *     (Energiespar-Sperre); darunter ignoriert es das Eco-Bit still und der Schalter würde beim
     *     nächsten Refresh zurückspringen. Beim Einschalten heben wir das Soll daher auf 24 °C an, falls
     *     niedriger — genau wie die Midea-Fernbedienung/-App — damit Eco wirklich greift.
     */
    fun toggleEco() {
        val v = !eco
        val bumpTemp = v && tempC < 24.0
        // EN: 24 °C is the user's target, so the unit gets it calibration-compensated like any other setpoint.
        // DE: 24 °C ist das Ziel des Nutzers, das Gerät bekommt es also kalibriert wie jeden anderen Sollwert.
        val dev = if (bumpTemp) deviceTemp(24.0).also { lastSentDeviceTemp = it } else null
        command({ eco = v; if (bumpTemp) tempC = 24.0 }) {
            eco = v
            if (dev != null) tempC = dev
            apply()
        }
    }
    /**
     * EN: Toggle the prompt tone. The beep is carried as a bit on control commands; on many units (e.g.
     *     the PortaSplit) the AC only actually chirps on power on/off, not on parameter changes. We persist
     *     the choice and also push the buzzer property for units that use it.
     * DE: Den Signalton umschalten. Der Beep wird als Bit auf Steuerbefehlen mitgeschickt; bei vielen
     *     Geräten (z. B. der PortaSplit) quittiert die Klima nur bei Ein/Aus, nicht bei Parameter-Änderungen.
     *     Wir speichern die Wahl und senden zusätzlich die Buzzer-Property für Geräte, die sie nutzen.
     */
    /**
     * EN: Toggle turbo/boost (issue #13). Turbo means "full power until further notice" — the unit ends
     *     it itself when mode/power changes, and it reports the state in every state frame, so the
     *     toggle self-corrects on the next poll if the unit rejected it (e.g. in fan-only/dry mode).
     * DE: Turbo/Boost umschalten (Issue #13). Turbo heißt „Volllast bis auf Widerruf" — das Gerät beendet
     *     ihn selbst bei Modus-/Power-Wechsel und meldet den Zustand in jedem State-Frame; der Schalter
     *     korrigiert sich beim nächsten Poll also selbst, falls das Gerät ablehnt (z. B. bei Lüften/Entfeuchten).
     */
    fun toggleTurbo() { val v = !turbo; command({ turbo = v }) { setTurbo(v) } }
    fun applyBeep(on: Boolean) = command({ beep = on; session?.beep = on; SettingsRepo.setBeep(getApplication(), on) }) { setBuzzer(on) }
    fun applyRate(value: Int) = command({ rate = value }) { setRate(value) }
    /** EN: Flip the indoor unit's LED display panel; the switch reflects what we last sent. DE: Die LED-Anzeige des Innengeräts umschalten; der Schalter zeigt das zuletzt Gesendete. */
    fun toggleDisplay() { val v = !display; command({ display = v }) { toggleDisplay() } }
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
        // EN: A scene stores the room temperature the user wants, so it is compensated like a manual
        //     setpoint. Scenes saved before the whole-degree fix may still hold a ".5" — snap it first.
        // DE: Eine Szene speichert die vom Nutzer gewünschte Raumtemperatur und wird daher wie ein
        //     manueller Sollwert kompensiert. Vor dem Ganzgrad-Fix gespeicherte Szenen können noch ein
        //     „,5" enthalten — daher zuerst einrasten.
        val t = snapTemp(scene.tempC)
        val dev = deviceTemp(t)
        lastSentDeviceTemp = dev
        command({
            powerOn = scene.powerOn; mode = scene.mode; tempC = t
            fan = scene.fan; eco = scene.eco; swing = scene.swing
        }) {
            powerOn = scene.powerOn; mode = scene.mode; tempC = dev
            fan = scene.fan; eco = scene.eco; swing = if (scene.swing) 0x3F else 0
            apply()
        }
    }

    // EN: ---- live refresh loop (polls state + energy every few seconds) ----
    // DE: ---- Live-Aktualisierungsschleife (fragt Zustand + Energie alle paar Sekunden ab) ----
    private suspend fun refreshOnce() {
        val s = session ?: return
        lock.withLock {
            // EN: Reflect device-reported option states too (also catches physical-remote changes). DE: Auch die vom Gerät gemeldeten Optionszustände spiegeln (erfasst auch Fernbedienungs-Änderungen).
            s.queryState()?.let { live = calibrated(it); syncOptionsFromState(it) }
            s.queryEnergy()?.let { energy = it }
            // EN: Beta diagnostics (PR #278) — only poll a group when the user enabled it (extra round-trips).
            // DE: Beta-Diagnose (PR #278) — eine Gruppe nur abfragen, wenn der Nutzer sie aktiviert hat (Extra-Round-Trips).
            if (diagGroup1) s.queryGroup1()?.let { group1 = it }
            if (diagGroup2) s.queryGroup2()?.let { group2 = it }
            if (diagGroup7) s.queryGroup7()?.let { group7 = it }
        }
        publishWidget()
    }

    private fun startRefresh() {
        stopRefresh()
        refreshJob = viewModelScope.launch {
            while (true) {
                // EN: Interval is user-configurable (Settings); re-read each cycle so changes apply live.
                // DE: Intervall ist vom Nutzer konfigurierbar (Einstellungen); je Zyklus neu gelesen, damit Änderungen sofort greifen.
                delay(pollIntervalSec * 1000L)
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
