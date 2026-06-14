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
