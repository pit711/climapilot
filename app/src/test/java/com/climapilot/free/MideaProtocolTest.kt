package com.climapilot.free

import com.climapilot.free.midea.MideaAc
import com.climapilot.free.midea.MideaCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integration tests for the Midea LAN protocol layer: command framing and response parsing.
 * These exercise the real byte math end-to-end without any network or hardware.
 */
class MideaProtocolTest {

    private fun u(b: Byte) = b.toInt() and 0xFF

    @Test
    fun setState_encodesPowerModeTempFan() {
        // power on, COOL, 24.0 °C, fan 60
        val f = MideaAc.buildSetState(powerOn = true, mode = MideaAc.MODE_COOL, tempC = 24.0, fan = 60)
        assertEquals("frame start 0xAA", 0xAA, u(f[0]))
        assertEquals("device type 0xAC", 0xAC, u(f[2]))
        assertEquals("frame type control", 0x02, u(f[9]))
        assertEquals("set-state opcode", 0x40, u(f[10]))
        assertTrue("power bit set", (u(f[11]) and 0x1) == 1)
        // byte2 = temp nibble (24-16=8) | mode<<5 (COOL=2 -> 0x40) = 0x48
        assertEquals(0x48, u(f[12]))
        assertEquals("fan 60", 60, u(f[13]))
    }

    @Test
    fun setState_powerOffClearsPowerBit() {
        val f = MideaAc.buildSetState(powerOn = false, mode = MideaAc.MODE_COOL, tempC = 24.0, fan = 60)
        assertEquals("power bit cleared", 0, u(f[11]) and 0x1)
    }

    @Test
    fun setState_halfDegreeSetsFractionBit() {
        // 25.5 °C -> temp nibble (25-16=9) | 0x10 fraction | mode 0x40 = 0x59
        val f = MideaAc.buildSetState(powerOn = true, mode = MideaAc.MODE_COOL, tempC = 25.5, fan = 40)
        assertEquals(0x59, u(f[12]))
    }

    @Test
    fun parseState_roundTripsAStateResponse() {
        val p = ByteArray(20)
        p[0] = 0xC0.toByte()          // StateResponse marker
        p[1] = 0x01                   // power on
        p[2] = 0x48                   // temp 24 + mode COOL
        p[3] = 60                     // fan
        p[10] = 0x00                  // celsius
        p[11] = 0x61                  // indoor raw -> (97-50)/2 = 23.5
        p[12] = 0x6C                  // outdoor raw -> (108-50)/2 = 29.0
        p[13] = 0x00                  // no alt temp
        p[15] = 0x00                  // no decimals
        p[16] = 0x00                  // no error
        val frame = ByteArray(10) + p + ByteArray(2)

        val s = MideaAc.parseState(frame)
        assertNotNull(s)
        s!!
        assertTrue(s.powerOn)
        assertEquals(MideaAc.MODE_COOL, s.mode)
        assertEquals(24.0, s.targetTemp, 0.001)
        assertEquals(60, s.fanSpeed)
        assertEquals(23.5, s.indoorTemp!!, 0.001)
        assertEquals(29.0, s.outdoorTemp!!, 0.001)
        assertEquals(0, s.errorCode)
    }

    @Test
    fun parseEnergy_decodesPowerAndKwh() {
        val p = ByteArray(20)
        p[0] = 0xC1.toByte()          // group-data response
        p[3] = 0x04                   // energy group
        // total = energy(4): 100*1 + 37 + 0.01*40 = 137.4 kWh
        p[4] = 0x00; p[5] = 0x01; p[6] = 0x37; p[7] = 0x40
        // current = energy(12): 1 + 0.01*20 = 1.2 kWh
        p[12] = 0x00; p[13] = 0x00; p[14] = 0x01; p[15] = 0x20
        // power binary (÷10): 0x001068 = 4200 -> 420.0 W
        p[16] = 0x00; p[17] = 0x10; p[18] = 0x68
        val frame = ByteArray(10) + p + ByteArray(2)

        val e = MideaAc.parseEnergyUsage(frame)
        assertNotNull(e)
        e!!
        assertEquals(420.0, e.powerW, 0.001)
        assertEquals(137.4, e.totalKwh, 0.05)
        assertEquals(1.2, e.currentKwh, 0.05)
    }

    @Test
    fun buildGetGroup_encodesGroupInByte3() {
        // payload byte[3] (frame byte 13) should be 0x40 | group
        for (group in intArrayOf(1, 2, 4, 5, 7)) {
            val f = MideaAc.buildGetGroup(group)
            assertEquals("frame type query", 0x03, u(f[9]))
            assertEquals("group $group byte", 0x40 or group, u(f[13]))
        }
    }

    @Test
    fun parseGroup1_decodesCompressorAndTemps() {
        val p = ByteArray(20)
        p[0] = 0xC1.toByte()          // group-data response
        p[3] = 0x41                   // group byte -> 0x41 & 0xF = 1
        p[4] = 28                     // compressor_frequency = 28 Hz
        p[5] = 25                     // target_compressor_frequency = 25 Hz
        p[7] = 1                      // compressor_current = 1 A
        p[8] = 232.toByte()           // compressor_voltage = 232 V
        p[10] = 71                    // T1 = (71-30)/2 = 20.5
        p[11] = 38                    // T2 = (38-30)/2 = 4.0
        p[12] = 102                   // T3 = (102-50)/2 = 26.0
        p[13] = 88                    // T4 = (88-50)/2 = 19.0
        p[14] = 36                    // TP = 36 °C
        val frame = ByteArray(10) + p + ByteArray(2)

        val g = MideaAc.parseGroup1(frame)
        assertNotNull(g)
        g!!
        assertEquals(28, g.compressorFrequency)
        assertEquals(25, g.targetCompressorFrequency)
        assertEquals(1, g.compressorCurrent)
        assertEquals(232, g.compressorVoltage)
        assertEquals(20.5, g.tempIndoorCoil!!, 0.001)
        assertEquals(4.0, g.tempEvaporator!!, 0.001)
        assertEquals(26.0, g.tempCondenser!!, 0.001)
        assertEquals(19.0, g.tempOutdoor!!, 0.001)
        assertEquals(36, g.tempDischargePipe)
    }

    @Test
    fun parseGroup2_decodesFanAndPump() {
        val p = ByteArray(20)
        p[0] = 0xC1.toByte()
        p[3] = 0x42                   // group 2
        p[4] = 52                     // target fan = 52 * 8 = 416
        p[5] = 53                     // actual fan = 53 * 8 = 424
        p[8] = 0x10                   // bit 4 set -> pump running
        val frame = ByteArray(10) + p + ByteArray(2)

        val g = MideaAc.parseGroup2(frame)
        assertNotNull(g)
        g!!
        assertEquals(416, g.targetIndoorFanSpeed)
        assertEquals(424, g.indoorFanSpeed)
        assertTrue(g.waterPumpRunning!!)
    }

    @Test
    fun parseGroup7_decodesPowerLittleEndian() {
        val p = ByteArray(20)
        p[0] = 0xC1.toByte()
        p[3] = 0x47                   // group 7
        p[10] = 13; p[11] = 1         // power = 13 + 256*1 = 269 W
        val frame = ByteArray(10) + p + ByteArray(2)

        val g = MideaAc.parseGroup7(frame)
        assertNotNull(g)
        assertEquals(269.0, g!!.compressorPower!!, 0.001)
    }

    @Test
    fun parseGroup_rejectsWrongGroup() {
        // A group-7 frame must not parse as group 1.
        val p = ByteArray(20).also { it[0] = 0xC1.toByte(); it[3] = 0x47 }
        val frame = ByteArray(10) + p + ByteArray(2)
        assertEquals(null, MideaAc.parseGroup1(frame))
        assertEquals(null, MideaAc.parseGroup2(frame))
        assertNotNull(MideaAc.parseGroup7(frame))
    }

    @Test
    fun buildGetProperties_encodesHeaderAndIds() {
        val f = MideaAc.buildGetProperties(listOf(MideaAc.PROP_OUT_SILENT))
        assertEquals("frame type query", 0x03, u(f[9]))
        assertEquals("0xB1 opcode", 0xB1, u(f[10]))
        assertEquals("count 1", 1, u(f[11]))
        assertEquals("prop id lo", 0xCD, u(f[12]))
        assertEquals("prop id hi", 0x00, u(f[13]))
    }

    @Test
    fun parseProperties_decodesOutSilentValue() {
        // 0xB1 response: header, count=1, then [id LE16][result][size][value]
        val p = ByteArray(10)
        p[0] = 0xB1.toByte()
        p[1] = 1                       // one property
        p[2] = 0xCD.toByte(); p[3] = 0x00   // PROP_OUT_SILENT (0x00CD)
        p[4] = 0x00                    // result ok
        p[5] = 1                       // size
        p[6] = 3                       // value 3 = on
        val frame = ByteArray(10) + p + ByteArray(2)

        val props = MideaAc.parseProperties(frame)
        assertNotNull(props)
        assertEquals(3, props!![MideaAc.PROP_OUT_SILENT])
    }

    @Test
    fun parseProperties_skipsErrorResult() {
        // result bit 0x10 marks a failed property — value must not be reported
        val p = ByteArray(10)
        p[0] = 0xB1.toByte()
        p[1] = 1
        p[2] = 0xCD.toByte(); p[3] = 0x00
        p[4] = 0x11                    // error result
        p[5] = 1
        p[6] = 3
        val frame = ByteArray(10) + p + ByteArray(2)

        val props = MideaAc.parseProperties(frame)
        assertNotNull(props)
        assertEquals(null, props!![MideaAc.PROP_OUT_SILENT])
    }

    @Test
    fun parseState_rejectsNonStateFrame() {
        val p = ByteArray(20).also { it[0] = 0x00 }
        assertEquals(null, MideaAc.parseState(ByteArray(10) + p + ByteArray(2)))
    }

    @Test
    fun frameChecksum_knownVector() {
        // (~sum + 1) & 0xFF over [1,2] = (~3 + 1) & 0xFF = 0xFD
        assertEquals(0xFD, MideaCrypto.frameChecksum(byteArrayOf(0x01, 0x02)))
    }

    @Test
    fun crc8_isDeterministic() {
        val data = byteArrayOf(0x40, 0x03, 0x48, 0x3C, 0x7F)
        assertEquals(MideaCrypto.crc8(data), MideaCrypto.crc8(data))
        assertEquals(0, MideaCrypto.crc8(ByteArray(0)))
    }

    @Test
    fun udpid_isStableAndXorFolded() {
        val id = byteArrayOf(1, 2, 3, 4, 5, 6)
        val a = MideaCrypto.udpid(id)
        assertEquals(16, a.size)
        assertTrue(a.contentEquals(MideaCrypto.udpid(id)))
    }
}
