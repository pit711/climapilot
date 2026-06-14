package com.climapilot.free.midea

import kotlin.math.floor

/**
 * EN: AC command framing (port of midea-msmart frame.py + AC/command.py SetStateCommand) and a
 *     high-level session that discovers, fetches token/key from the cloud, authenticates and sends
 *     control commands. This object only builds/parses byte frames; the socket work lives in MideaLan.
 *
 * DE: AC-Befehls-Framing (Portierung von midea-msmart frame.py + AC/command.py SetStateCommand) sowie
 *     eine High-Level-Sitzung, die Geräte findet, Token/Schlüssel aus der Cloud holt, sich
 *     authentifiziert und Steuerbefehle sendet. Dieses Objekt baut/parst nur Byte-Frames; die
 *     eigentliche Socket-Arbeit liegt in MideaLan.
 */
object MideaAc {
    const val DEVICE_TYPE = 0xAC
    private const val FRAME_CONTROL = 0x02
    private const val FRAME_QUERY = 0x03
    private const val CONTROL_SOURCE = 0x2

    // EN: Operational modes (AC/device.py OperationalMode).
    // DE: Betriebsmodi (AC/device.py OperationalMode).
    const val MODE_AUTO = 1
    const val MODE_COOL = 2
    const val MODE_DRY = 3
    const val MODE_HEAT = 4
    const val MODE_FAN_ONLY = 5

    // EN: Compressor rate-select (throttle) property. Values: 100 = off/full, 75, 50.
    // DE: Kompressor-Drossel-Eigenschaft (rate-select). Werte: 100 = aus/voll, 75, 50.
    const val PROP_RATE_SELECT = 0x0048
    const val RATE_OFF = 100
    // EN: On the Portasplit the byte value is INVERSE to compressor power (verified vs a Shelly plug):
    //     byte 75 → ~50 % power, byte 50 → ~75 % power. We name the gears by their real effect and
    //     send the byte that actually produces it.
    // DE: Bei der Portasplit ist der Byte-Wert UMGEKEHRT zur Kompressorleistung (gegen eine
    //     Shelly-Steckdose verifiziert): Byte 75 → ~50 % Leistung, Byte 50 → ~75 % Leistung. Wir
    //     benennen die Gänge nach ihrer echten Wirkung und senden das Byte, das sie tatsächlich erzeugt.
    const val RATE_GEAR75 = 50   // EN: ≈ 75 % compressor power / DE: ≈ 75 % Kompressorleistung
    const val RATE_GEAR50 = 75   // EN: ≈ 50 % compressor power / DE: ≈ 50 % Kompressorleistung

    // EN: Buzzer / prompt-tone property. 0 = silent, 1 = beep. (device.py always sends it.)
    // DE: Eigenschaft für Signalton/Quittungston. 0 = stumm, 1 = Piepton. (device.py sendet sie immer.)
    const val PROP_BUZZER = 0x001A

    // EN: Optional, device-specific feature properties (msmart PropertyId). Each is a SetProperties
    //     toggle (0/1) and is also reported in the B5 capabilities response, so we only show a toggle
    //     when the unit actually supports it.
    // DE: Optionale, gerätespezifische Funktions-Eigenschaften (msmart PropertyId). Jede ist ein
    //     SetProperties-Schalter (0/1) und wird auch im B5-Capabilities-Response gemeldet, sodass wir
    //     einen Schalter nur zeigen, wenn das Gerät die Funktion wirklich unterstützt.
    const val PROP_ANION = 0x021E       // EN: ionizer / air purifier / DE: Ionisierer / Luftreiniger
    const val PROP_SELF_CLEAN = 0x0039  // EN: self-clean cycle / DE: Selbstreinigungs-Zyklus
    const val PROP_OUT_SILENT = 0x00CD  // EN: outdoor-unit silent mode / DE: Leise-Modus des Außengeräts

    private var messageId = 0

    /**
     * EN: Wrap a command payload into a 0xAA AC frame (Frame.tobytes + Command msg-id/crc8).
     * DE: Eine Befehls-Nutzlast in einen 0xAA-AC-Frame verpacken (Frame.tobytes + Command msg-id/crc8).
     */
    private fun frame(frameType: Int, data: ByteArray): ByteArray {
        messageId = (messageId + 1) and 0xFF
        val withId = data + byteArrayOf(messageId.toByte())
        val inner = withId + byteArrayOf(MideaCrypto.crc8(withId).toByte())

        val header = ByteArray(10)
        header[0] = 0xAA.toByte()
        header[1] = (inner.size + 10).toByte()
        header[2] = DEVICE_TYPE.toByte()
        header[8] = 0x00 // protocol version
        header[9] = frameType.toByte()
        val body = header + inner
        val checksum = MideaCrypto.frameChecksum(body.copyOfRange(1, body.size))
        return body + byteArrayOf(checksum.toByte())
    }

    /**
     * EN: Build a SetState control frame (power/mode/temperature/fan/swing/beep/eco in one packet).
     * DE: Einen SetState-Steuer-Frame bauen (Ein-Aus/Modus/Temperatur/Lüfter/Swing/Signalton/Eco in einem Paket).
     *
     * @param tempC EN: target temperature in °C (17–30 primary range, supports .5 steps) /
     *              DE: Zieltemperatur in °C (Hauptbereich 17–30, .5-Schritte möglich)
     * @param fan EN: fan speed 1–100 (custom) or a named value (e.g. 20/40/60/80/100) /
     *            DE: Lüfterstufe 1–100 (frei) oder ein benannter Wert (z. B. 20/40/60/80/100)
     */
    fun buildSetState(
        powerOn: Boolean,
        mode: Int,
        tempC: Double,
        fan: Int,
        swing: Int = 0,
        beep: Boolean = false,
        eco: Boolean = false,
    ): ByteArray {
        val beepBit = if (beep) 0x40 else 0
        val powerBit = if (powerOn) 0x1 else 0

        val integral = floor(tempC).toInt()
        val fractional = tempC - integral
        var temperature: Int
        var temperatureAlt: Int
        if (integral in 17..30) {
            temperature = (integral - 16) and 0xF
            temperatureAlt = 0
        } else {
            temperature = 0
            temperatureAlt = (integral - 12) and 0x1F
        }
        if (fractional > 0) temperature = temperature or 0x10

        val modeBits = (mode and 0x7) shl 5
        val swingMode = 0x30 or (swing and 0x3F)
        val ecoBit = if (eco) 0x80 else 0
        val humidity = 40 and 0x7F

        // EN: Fixed 25-byte SetState payload. Each byte's meaning is noted EN/DE on the right.
        // DE: Feste 25-Byte-SetState-Nutzlast. Die Bedeutung jedes Bytes steht rechts EN/DE.
        val data = byteArrayOf(
            0x40,                                            // EN: set-state opcode / DE: SetState-Opcode
            (CONTROL_SOURCE or beepBit or powerBit).toByte(), // EN: source/beep/power / DE: Quelle/Signalton/Ein-Aus
            (temperature or modeBits).toByte(),              // EN: temp + mode / DE: Temperatur + Modus
            (fan and 0xFF).toByte(),                          // EN: fan speed / DE: Lüfterstufe
            0x7F, 0x7F, 0x00,                                 // EN: on/off timer (disabled) / DE: Ein-/Aus-Timer (deaktiviert)
            swingMode.toByte(),                               // EN: swing / DE: Swing (Lamellen)
            0x00,                                             // EN: follow-me / alt turbo / DE: Follow-me / alt. Turbo
            ecoBit.toByte(),                                  // EN: eco/purifier/aux / DE: Eco/Luftreiniger/Zusatz
            0x00,                                             // EN: sleep/turbo/Fahrenheit (we use Celsius) / DE: Sleep/Turbo/Fahrenheit (wir nutzen Celsius)
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00,
            temperatureAlt.toByte(),                          // EN: alt temperature (extended range) / DE: alt. Temperatur (erweiterter Bereich)
            humidity.toByte(),                                // EN: target humidity / DE: Soll-Luftfeuchte
            0x00,
            0x00,                                             // EN: freeze protection / DE: Frostschutz
            0x00,                                             // EN: independent aux heat / DE: unabhängige Zusatzheizung
            0x00,
        )
        return frame(FRAME_CONTROL, data)
    }

    /**
     * EN: Build a SetProperties control frame (port of SetPropertiesCommand).
     *     Payload: 0xB0, count, then per property: propId (LE16), valueLen=1, value.
     * DE: Einen SetProperties-Steuer-Frame bauen (Portierung von SetPropertiesCommand).
     *     Nutzlast: 0xB0, Anzahl, dann je Eigenschaft: propId (LE16), valueLen=1, Wert.
     */
    fun buildSetProperties(props: List<Pair<Int, Int>>): ByteArray {
        val out = ArrayList<Byte>()
        out.add(0xB0.toByte()); out.add(props.size.toByte())
        for ((id, v) in props) {
            out.add((id and 0xFF).toByte()); out.add(((id shr 8) and 0xFF).toByte())
            out.add(0x01); out.add((v and 0xFF).toByte())
        }
        return frame(FRAME_CONTROL, out.toByteArray())
    }

    fun buildSetProperty(propId: Int, value: Int): ByteArray = buildSetProperties(listOf(propId to value))

    /**
     * EN: Build a query for the basic state (port of GetStateCommand).
     * DE: Eine Abfrage des Basiszustands bauen (Portierung von GetStateCommand).
     */
    fun buildGetState(): ByteArray {
        val data = byteArrayOf(
            0x41, 0x81.toByte(), 0x00, 0xFF.toByte(), 0x03, 0xFF.toByte(), 0x00,
            0x02,                                   // temperature type: indoor
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0x03,
        )
        return frame(FRAME_QUERY, data)
    }

    private fun parseTemp(data: Int, decimals: Double, fahrenheit: Boolean): Double? {
        if (data == 0xFF) return null
        val t = (data - 50) / 2.0
        if (!fahrenheit && decimals > 0) return t.toInt() + (if (t >= 0) decimals else -decimals)
        if (decimals >= 0.5) return t.toInt() + (if (t >= 0) 0.5 else -0.5)
        return t
    }

    /**
     * EN: Parse a StateResponse frame (port of StateResponse._parse). Returns null if not a state frame.
     * DE: Einen StateResponse-Frame parsen (Portierung von StateResponse._parse). Null, wenn es kein Zustands-Frame ist.
     */
    fun parseState(frame: ByteArray): AcState? {
        if (frame.size < 12) return null
        val p = frame.copyOfRange(10, frame.size - 2)   // strip 10-byte header + 2 trailing bytes
        if (p.size < 17) return null
        if ((p[0].toInt() and 0xFF) != 0xC0) return null   // only parse real StateResponse (0xC0)
        fun b(i: Int) = p[i].toInt() and 0xFF
        val powerOn = (b(1) and 0x1) != 0
        val fahrenheit = (b(10) and 0x4) != 0
        var targetTemp = (b(2) and 0xF) + 16.0 + (if (b(2) and 0x10 != 0) 0.5 else 0.0)
        val altT = b(13) and 0x1F
        if (altT != 0) targetTemp = altT + 12.0 + (if (b(2) and 0x10 != 0) 0.5 else 0.0)
        val mode = (b(2) shr 5) and 0x7
        val fan = b(3) and 0x7F
        val indoor = parseTemp(b(11), (b(15) and 0xF) / 10.0, fahrenheit)
        val outdoor = parseTemp(b(12), (b(15) shr 4) / 10.0, fahrenheit)
        return AcState(powerOn, mode, targetTemp, fan, indoor, outdoor, b(16))
    }

    /**
     * EN: Query energy usage / real-time power (port of GetEnergyUsageCommand).
     * DE: Energieverbrauch / Echtzeit-Leistung abfragen (Portierung von GetEnergyUsageCommand).
     */
    fun buildGetEnergyUsage(): ByteArray {
        val data = ByteArray(20)
        data[0] = 0x41; data[1] = 0x21; data[2] = 0x01; data[3] = 0x44
        return frame(FRAME_QUERY, data)
    }

    /**
     * EN: Parse energy usage from an EnergyUsageResponse (group-data 4), or null.
     * DE: Energieverbrauch aus einer EnergyUsageResponse (Gruppendaten 4) parsen, sonst null.
     */
    fun parseEnergyUsage(frame: ByteArray): EnergyUsage? {
        if (frame.size < 12) return null
        val p = frame.copyOfRange(10, frame.size - 2)
        if (p.size < 19) return null
        if ((p[0].toInt() and 0xFF) != 0xC1) return null      // group-data response
        if ((p[3].toInt() and 0xF) != 4) return null          // energy group
        fun bcd(d: Int) = 10 * ((d shr 4) and 0xF) + (d and 0xF)
        fun energy(o: Int) = 10000.0 * bcd(p[o].toInt() and 0xFF) + 100.0 * bcd(p[o + 1].toInt() and 0xFF) +
            bcd(p[o + 2].toInt() and 0xFF) + 0.01 * bcd(p[o + 3].toInt() and 0xFF)
        // EN: Power is binary-encoded (÷10), NOT BCD — BCD under-reports ~3.4× (validated vs a Shelly plug).
        // DE: Die Leistung ist binär kodiert (÷10), NICHT BCD — BCD meldet ~3,4× zu wenig (gegen eine Shelly-Steckdose verifiziert).
        val d16 = p[16].toInt() and 0xFF; val d17 = p[17].toInt() and 0xFF; val d18 = p[18].toInt() and 0xFF
        val power = ((d16 shl 16) or (d17 shl 8) or d18) / 10.0
        val total = energy(4)
        val current = energy(12)
        // EN: Treat all-zero as "no energy monitoring". / DE: Komplett null = „keine Energiemessung".
        if (power == 0.0 && total == 0.0 && current == 0.0) return EnergyUsage(Double.NaN, Double.NaN, Double.NaN)
        return EnergyUsage(power, total, current)
    }

    /**
     * EN: Query capabilities (port of GetCapabilitiesCommand, B5). The B5 capabilities can span two
     *     pages; [additional] = true requests the second page.
     * DE: Fähigkeiten abfragen (Portierung von GetCapabilitiesCommand, B5). Die B5-Fähigkeiten können
     *     sich über zwei Seiten erstrecken; [additional] = true fordert die zweite Seite an.
     */
    fun buildGetCapabilities(additional: Boolean = false): ByteArray =
        frame(FRAME_QUERY, byteArrayOf(0xB5.toByte(), 0x01, if (additional) 0x01 else 0x00))

    /**
     * EN: Parse a CapabilitiesResponse (0xB5) into the feature flags we care about: compressor
     *     rate-select (gear: 0/2/5), ionizer (anion), self-clean and outdoor-silent. Each capability
     *     is a 2-byte id + 1-byte size + value bytes; support is read from the first value byte.
     * DE: Eine CapabilitiesResponse (0xB5) in die für uns relevanten Funktions-Flags parsen:
     *     Kompressor-Drossel (Gang: 0/2/5), Ionisierer (Anion), Selbstreinigung und Außengerät-Leise.
     *     Jede Fähigkeit ist 2-Byte-ID + 1-Byte-Größe + Wert-Bytes; die Unterstützung steht im ersten Wert-Byte.
     */
    fun parseCapabilities(frame: ByteArray): AcCapabilities? {
        if (frame.size < 13) return null
        val p = frame.copyOfRange(10, frame.size - 2)
        if (p.size < 3 || (p[0].toInt() and 0xFF) != 0xB5) return null
        val count = p[1].toInt() and 0xFF
        var i = 2
        var n = 0
        var rateLevels = 0
        var anion = false
        var selfClean = false
        var outSilent = false
        while (n < count && i + 3 <= p.size) {
            val id = (p[i].toInt() and 0xFF) or ((p[i + 1].toInt() and 0xFF) shl 8)
            val size = p[i + 2].toInt() and 0xFF
            if (size == 0) { i += 3; n++; continue }
            if (i + 3 + size > p.size) break
            val value = p[i + 3].toInt() and 0xFF
            when (id) {
                PROP_RATE_SELECT -> rateLevels = when { value == 1 -> 2; value >= 2 -> 5; else -> 0 }
                PROP_ANION -> if (value == 1) anion = true
                PROP_SELF_CLEAN -> if (value == 1) selfClean = true
                PROP_OUT_SILENT -> if (value == 1 || value == 3) outSilent = true
            }
            i += 3 + size; n++
        }
        return AcCapabilities(rateLevels, anion, selfClean, outSilent)
    }
}

/**
 * EN: AC energy usage (W and kWh). NaN = unavailable / device has no energy monitoring.
 * DE: Energieverbrauch der Klima (W und kWh). NaN = nicht verfügbar / Gerät hat keine Energiemessung.
 */
data class EnergyUsage(val powerW: Double, val totalKwh: Double, val currentKwh: Double)

/**
 * EN: Live device state read back from the AC (the snapshot driving the UI readouts).
 * DE: Live-Gerätezustand, von der Klima zurückgelesen (die Momentaufnahme für die UI-Anzeigen).
 */
data class AcState(
    val powerOn: Boolean,
    val mode: Int,
    val targetTemp: Double,
    val fanSpeed: Int,
    val indoorTemp: Double?,
    val outdoorTemp: Double?,
    val errorCode: Int,
)

/**
 * EN: Optional, device-specific capabilities read from the B5 response. Drives which extra toggles
 *     the UI shows (we never offer a feature the unit didn't report).
 * DE: Optionale, gerätespezifische Fähigkeiten aus dem B5-Response. Bestimmt, welche zusätzlichen
 *     Schalter die UI zeigt (wir bieten nie eine Funktion an, die das Gerät nicht gemeldet hat).
 */
data class AcCapabilities(
    val rateLevels: Int = 0,
    val anion: Boolean = false,
    val selfClean: Boolean = false,
    val outSilent: Boolean = false,
)

/** EN: Connection state for the UI. / DE: Verbindungszustand für die UI. */
enum class MideaConnState { Idle, Discovering, Connecting, Connected, Error }

/**
 * EN: One AC session: discover → cloud token → authenticate → control. Holds the last state we set
 *     for the controlled device, so each command we build is internally coherent.
 * DE: Eine AC-Sitzung: Finden → Cloud-Token → Authentifizieren → Steuern. Hält den zuletzt
 *     gesetzten Zustand des gesteuerten Geräts, damit jeder gebaute Befehl in sich stimmig ist.
 */
class MideaAcSession(
    val device: MideaDevice,
    region: String = "DE",
    // EN: Previously persisted (token, key) for this device. When present, connect() tries it first
    //     and authenticates fully offline — no cloud, no internet.
    // DE: Zuvor gespeichertes (Token, Key) für dieses Gerät. Falls vorhanden, probiert connect() es
    //     zuerst und authentifiziert vollständig offline — ohne Cloud, ohne Internet.
    private val cachedCreds: Pair<String, String>? = null,
    // EN: Invoked with a freshly fetched (token, key) so the caller can persist it for next time.
    // DE: Wird mit einem frisch geholten (Token, Key) aufgerufen, damit der Aufrufer es für das nächste Mal speichern kann.
    private val onCredsFetched: ((token: String, key: String) -> Unit)? = null,
) {
    private val cloud = MideaCloud(region)
    private val lan = MideaLan(device.ip, device.port, device.id)
    private var token: String? = null
    private var key: String? = null

    // EN: Last-known set state — we drive state explicitly rather than echoing device reports.
    // DE: Zuletzt gesetzter Zustand — wir steuern den Zustand aktiv, statt Gerätemeldungen zu spiegeln.
    var powerOn = true
    var mode = MideaAc.MODE_COOL
    var tempC = 24.0
    var fan = 60
    var swing = 0
    var eco = false
    var beep = false   // EN: when true, the unit chirps on every command / DE: wenn true, piept das Gerät bei jedem Befehl

    val authenticated: Boolean get() = lan.authenticated

    /**
     * EN: Fetch token/key from the cloud (trying both udpid byte orders) and authenticate the LAN session.
     * DE: Token/Schlüssel aus der Cloud holen (beide udpid-Byte-Reihenfolgen versuchen) und die LAN-Sitzung authentifizieren.
     */
    suspend fun connect() {
        if (device.version != 3) {
            // EN: V2 devices need no token/key — not implemented here (most modern units are V3).
            // DE: V2-Geräte brauchen kein Token/Schlüssel — hier nicht umgesetzt (die meisten neuen Geräte sind V3).
            throw MideaProtocolError("Nur V3-Geräte werden unterstützt (Gerät meldet V${device.version}).")
        }
        // EN: Fast offline path — reuse a previously persisted token/key, so no internet is needed.
        //     If it fails (stale token or device unreachable), fall through to the cloud path below.
        // DE: Schneller Offline-Pfad — zuvor gespeichertes Token/Schlüssel wiederverwenden, sodass kein
        //     Internet nötig ist. Schlägt es fehl (veraltetes Token oder Gerät nicht erreichbar), unten
        //     auf den Cloud-Pfad zurückfallen.
        cachedCreds?.let { (t, k) ->
            try {
                lan.connectAndAuthenticate(t, k)
                token = t; key = k
                return
            } catch (_: Exception) {
                // EN: ignore and try the cloud / DE: ignorieren und die Cloud versuchen
            }
        }
        var lastError: Exception? = null
        for (endian in listOf("little", "big")) {
            val idBytes = ByteArray(6) {
                if (endian == "little") ((device.id shr (8 * it)) and 0xFF).toByte()
                else ((device.id shr (8 * (5 - it))) and 0xFF).toByte()
            }
            val udpid = MideaCrypto.udpid(idBytes).joinToString("") { "%02x".format(it) }
            try {
                val (t, k) = cloud.getToken(udpid)
                lan.connectAndAuthenticate(t, k)
                token = t; key = k   // EN: keep in-memory for transparent reconnects / DE: für transparente Reconnects im Speicher behalten
                onCredsFetched?.invoke(t, k)   // EN: let the caller persist it for offline reuse / DE: dem Aufrufer das Speichern für die Offline-Nutzung ermöglichen
                return
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: MideaProtocolError("Authentifizierung fehlgeschlagen")
    }

    /**
     * EN: Re-open the socket and re-authenticate with the cached token/key (cheap, ~0.5 s).
     * DE: Socket neu öffnen und mit zwischengespeichertem Token/Schlüssel neu authentifizieren (günstig, ~0,5 s).
     */
    private suspend fun reauth() {
        val t = token; val k = key
        if (t == null || k == null) throw MideaProtocolError("Keine Sitzungsdaten – erst verbinden.")
        lan.connectAndAuthenticate(t, k)
    }

    /**
     * EN: Send a command, transparently reconnecting once if the (often idle-timed-out) V3 socket was
     *     closed by the device — mirrors msmart's auto-reconnect in send().
     * DE: Einen Befehl senden und einmal transparent neu verbinden, falls das (oft per Leerlauf-Timeout)
     *     vom Gerät geschlossene V3-Socket weg ist — entspricht dem Auto-Reconnect in msmarts send().
     */
    private suspend fun send(frame: ByteArray) {
        try {
            lan.sendCommand(frame)
        } catch (e: Exception) {
            reauth()
            lan.sendCommand(frame)
        }
    }

    /**
     * EN: Push the full current state to the device in one SetState frame.
     * DE: Den kompletten aktuellen Zustand in einem SetState-Frame an das Gerät senden.
     */
    suspend fun apply() {
        send(MideaAc.buildSetState(powerOn, mode, tempC, fan, swing, beep = beep, eco = eco))
    }

    // EN: Convenience setters — update one field, then re-send the whole state.
    // DE: Komfort-Setter — ein Feld ändern, dann den gesamten Zustand erneut senden.
    suspend fun setPower(on: Boolean) { powerOn = on; apply() }
    suspend fun setMode(m: Int) { mode = m; apply() }
    suspend fun setTemp(temp: Double) { tempC = temp.coerceIn(16.0, 30.0); apply() }
    suspend fun setCool(temp: Double) { mode = MideaAc.MODE_COOL; tempC = temp; apply() }
    suspend fun setFan(level: Int) { fan = level.coerceIn(1, 100); apply() }
    suspend fun setSwing(on: Boolean) { swing = if (on) 0x3F else 0; apply() }
    suspend fun setEco(on: Boolean) { eco = on; apply() }

    /**
     * EN: Compressor throttle: 100 = off/full, 75, 50. Buzzer is kept off in the same command.
     * DE: Kompressor-Drossel: 100 = aus/voll, 75, 50. Der Signalton bleibt im selben Befehl aus.
     */
    suspend fun setRate(rate: Int) {
        send(MideaAc.buildSetProperties(listOf(MideaAc.PROP_RATE_SELECT to rate, MideaAc.PROP_BUZZER to 0)))
    }

    /**
     * EN: Globally enable/disable the prompt tone (buzzer).
     * DE: Den Quittungston (Signalton) global ein-/ausschalten.
     */
    suspend fun setBuzzer(on: Boolean) {
        send(MideaAc.buildSetProperties(listOf(MideaAc.PROP_BUZZER to if (on) 1 else 0)))
    }

    /** EN: Ionizer / air purifier on/off (only on units that report it). DE: Ionisierer / Luftreiniger ein/aus (nur bei Geräten, die ihn melden). */
    suspend fun setAnion(on: Boolean) {
        send(MideaAc.buildSetProperties(listOf(MideaAc.PROP_ANION to if (on) 1 else 0, MideaAc.PROP_BUZZER to 0)))
    }

    /** EN: Start/stop the self-clean cycle. DE: Den Selbstreinigungs-Zyklus starten/stoppen. */
    suspend fun setSelfClean(on: Boolean) {
        send(MideaAc.buildSetProperties(listOf(MideaAc.PROP_SELF_CLEAN to if (on) 1 else 0, MideaAc.PROP_BUZZER to 0)))
    }

    /** EN: Outdoor-unit silent mode on/off. DE: Leise-Modus des Außengeräts ein/aus. */
    suspend fun setOutdoorSilent(on: Boolean) {
        send(MideaAc.buildSetProperties(listOf(MideaAc.PROP_OUT_SILENT to if (on) 1 else 0, MideaAc.PROP_BUZZER to 0)))
    }

    /**
     * EN: Read the current device state (power/mode/temps/fan). Null on failure.
     * DE: Den aktuellen Gerätezustand lesen (Ein-Aus/Modus/Temperaturen/Lüfter). Null bei Fehler.
     */
    suspend fun queryState(): AcState? = try {
        MideaAc.parseState(lan.sendCommand(MideaAc.buildGetState()))
    } catch (e: Exception) {
        runCatching { reauth(); MideaAc.parseState(lan.sendCommand(MideaAc.buildGetState())) }.getOrNull()
    }

    /**
     * EN: Read energy usage (power W + kWh). Best indicator whether the compressor runs.
     * DE: Energieverbrauch lesen (Leistung W + kWh). Bester Indikator, ob der Kompressor läuft.
     */
    suspend fun queryEnergy(): EnergyUsage? = try {
        MideaAc.parseEnergyUsage(lan.sendCommand(MideaAc.buildGetEnergyUsage()))
    } catch (e: Exception) {
        runCatching { reauth(); MideaAc.parseEnergyUsage(lan.sendCommand(MideaAc.buildGetEnergyUsage())) }.getOrNull()
    }

    /**
     * EN: Read the device's optional capabilities (gear + ionizer/self-clean/outdoor-silent). Queries
     *     both B5 pages and merges them, since some features live on the second page.
     * DE: Die optionalen Fähigkeiten des Geräts lesen (Drossel + Ionisierer/Selbstreinigung/Außen-Leise).
     *     Fragt beide B5-Seiten ab und führt sie zusammen, da manche Funktionen auf der zweiten Seite liegen.
     */
    suspend fun queryCapabilities(): AcCapabilities? {
        val first = try {
            MideaAc.parseCapabilities(lan.sendCommand(MideaAc.buildGetCapabilities(false)))
        } catch (e: Exception) {
            runCatching { reauth(); MideaAc.parseCapabilities(lan.sendCommand(MideaAc.buildGetCapabilities(false))) }.getOrNull()
        }
        // EN: Best-effort second page; older units may not answer. DE: Best-Effort zweite Seite; ältere Geräte antworten evtl. nicht.
        val second = runCatching { MideaAc.parseCapabilities(lan.sendCommand(MideaAc.buildGetCapabilities(true))) }.getOrNull()
        if (first == null && second == null) return null
        return AcCapabilities(
            rateLevels = maxOf(first?.rateLevels ?: 0, second?.rateLevels ?: 0),
            anion = (first?.anion ?: false) || (second?.anion ?: false),
            selfClean = (first?.selfClean ?: false) || (second?.selfClean ?: false),
            outSilent = (first?.outSilent ?: false) || (second?.outSilent ?: false),
        )
    }

    fun close() = lan.close()
}
