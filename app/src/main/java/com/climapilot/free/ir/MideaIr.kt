package com.climapilot.free.ir

/**
 * EN: Encoder for the Midea air-conditioner IR remote protocol → a ConsumerIrManager pattern.
 *     Reference: sheinz/esp-midea-ir and crankyoldgit/IRremoteESP8266 (ir_Midea). 38 kHz carrier,
 *     pulse-distance, MSB-first. A command is 6 bytes — [B0][~B0][B1][~B1][B2][~B2] — sent TWICE:
 *       B0 = 0xB2 (magic)
 *       B1 = fan(high nibble) | state(low nibble)
 *       B2 = temp(high nibble, table) | mode(low nibble)
 *     Timing unit T ≈ 560 µs: header 8T mark + 8T space; bit = 1T mark + (1T=0 / 3T=1) space; a final
 *     stop mark, then a gap before the identical repeat.
 *
 * DE: Encoder für das Midea-Klima-IR-Fernbedienungsprotokoll → ein ConsumerIrManager-Muster.
 *     Referenz: sheinz/esp-midea-ir und IRremoteESP8266 (ir_Midea). 38-kHz-Träger, Pulse-Distance,
 *     MSB-first. Ein Befehl sind 6 Byte — [B0][~B0][B1][~B1][B2][~B2] — ZWEIMAL gesendet (siehe oben).
 *     Zeiteinheit T ≈ 560 µs: Header 8T/8T; Bit = 1T Mark + (1T=0 / 3T=1) Space; Stop-Mark, dann Lücke
 *     vor der identischen Wiederholung.
 */
object MideaIr {
    const val CARRIER_HZ = 38000

    private const val T = 560
    private const val HDR_MARK = 8 * T   // 4480
    private const val HDR_SPACE = 8 * T  // 4480
    private const val BIT_MARK = T       // 560
    private const val ZERO_SPACE = T     // 560
    private const val ONE_SPACE = 3 * T  // 1680
    private const val GAP = 8 * T        // 4480 silence between the two repeats

    // EN: Operating modes (low nibble of B2). DE: Betriebsmodi (unteres Nibble von B2).
    enum class Mode(val nibble: Int) { COOL(0b0000), HEAT(0b1100), AUTO(0b1000), FAN(0b0100), DRY(0b0000) }

    // EN: Fan presets (high nibble of B1). DE: Lüfter-Vorgaben (oberes Nibble von B1).
    enum class Fan(val nibble: Int) { AUTO(0b1011), LOW(0b1001), MEDIUM(0b0101), HIGH(0b0011) }

    private const val STATE_ON = 0b1111
    private const val STATE_OFF = 0b1011

    // EN: 17..30 °C → 4-bit code (gray-ish), index = tempC - 17. DE: 17..30 °C → 4-Bit-Code, Index = tempC - 17.
    private val TEMP_TABLE = intArrayOf(
        0b0000, 0b0001, 0b0011, 0b0010, 0b0110, 0b0111, 0b0101,
        0b0100, 0b1100, 0b1101, 0b1001, 0b1000, 0b1010, 0b1011,
    )

    /**
     * EN: Build the IR pattern (alternating on/off µs, starting with a mark) for a full AC state.
     * DE: Das IR-Muster (abwechselnd an/aus in µs, beginnt mit Mark) für einen vollen Klima-Zustand bauen.
     */
    fun pattern(powerOn: Boolean, mode: Mode, tempC: Int, fan: Fan): IntArray {
        val t = tempC.coerceIn(17, 30)
        val b0 = 0xB2
        val b1 = (fan.nibble shl 4) or (if (powerOn) STATE_ON else STATE_OFF)
        val b2 = (TEMP_TABLE[t - 17] shl 4) or mode.nibble
        return patternRaw(intArrayOf(b0, b0.inv() and 0xFF, b1, b1.inv() and 0xFF, b2, b2.inv() and 0xFF))
    }

    /**
     * EN: Build the IR pattern from an arbitrary 6-byte frame as-is (no inverse computation). Used for
     *     the "special" toggle commands (Quiet/Turbo/Econo) which aren't in the data+inverse state format.
     * DE: Das IR-Muster aus einem beliebigen 6-Byte-Frame bauen (ohne Invers-Berechnung). Für die
     *     „Sonder"-Toggle-Befehle (Quiet/Turbo/Econo), die nicht im Daten+Invers-Format vorliegen.
     */
    fun patternRaw(frame: IntArray): IntArray {
        val out = ArrayList<Int>(220)
        repeat(2) {
            out.add(HDR_MARK); out.add(HDR_SPACE)
            for (byte in frame) {
                for (bit in 7 downTo 0) {              // MSB-first
                    out.add(BIT_MARK)
                    out.add(if ((byte shr bit) and 1 == 1) ONE_SPACE else ZERO_SPACE)
                }
            }
            out.add(BIT_MARK)   // stop mark
            out.add(GAP)        // gap before the repeat (trailing gap on the 2nd is harmless)
        }
        return out.toIntArray()
    }

    // EN: "Special" toggle commands — full 6-byte frames (type byte 0xA2), from IRremoteESP8266 ir_Midea.
    //     These toggle a feature rather than carry full state. DE: „Sonder"-Toggle-Befehle — volle
    //     6-Byte-Frames (Typ-Byte 0xA2), aus IRremoteESP8266 ir_Midea. Schalten ein Feature um, statt den
    //     Vollzustand zu tragen.
    fun quietOn() = patternRaw(intArrayOf(0xA2, 0x12, 0xFF, 0xFF, 0xFF, 0x6E))
    fun quietOff() = patternRaw(intArrayOf(0xA2, 0x13, 0xFF, 0xFF, 0xFF, 0x6F))
    fun toggleTurbo() = patternRaw(intArrayOf(0xA2, 0x09, 0xFF, 0xFF, 0xFF, 0x74))
    fun toggleEcono() = patternRaw(intArrayOf(0xA2, 0x02, 0xFF, 0xFF, 0xFF, 0x7E))
    fun toggleSwing() = patternRaw(intArrayOf(0xA2, 0x01, 0xFF, 0xFF, 0xFF, 0x7C))

    /**
     * EN: Map a ClimaPilot mode (MideaAc.MODE_*) to a Midea IR mode. Dry has no slot in the 2-bit IR mode
     *     field, so it falls back to Cool (and isn't offered in the IR UI).
     * DE: Einen ClimaPilot-Modus (MideaAc.MODE_*) auf einen Midea-IR-Modus abbilden. Trocknen hat im
     *     2-Bit-IR-Modusfeld keinen Platz und fällt auf Kühlen zurück (und wird in der IR-UI nicht angeboten).
     */
    fun modeFromApp(appMode: Int): Mode = when (appMode) {
        1 -> Mode.AUTO   // MODE_AUTO
        4 -> Mode.HEAT   // MODE_HEAT
        5 -> Mode.FAN    // MODE_FAN
        else -> Mode.COOL // MODE_COOL (and MODE_DRY fallback)
    }

    /** EN: Map a ClimaPilot fan value (1–100, or >100 = auto) to a Midea IR fan preset. DE: Einen ClimaPilot-Lüfterwert (1–100, oder >100 = Auto) auf ein Midea-IR-Lüfter-Preset abbilden. */
    fun fanFromApp(appFan: Int): Fan = when {
        appFan > 100 -> Fan.AUTO
        appFan <= 33 -> Fan.LOW
        appFan <= 66 -> Fan.MEDIUM
        else -> Fan.HIGH
    }
}
