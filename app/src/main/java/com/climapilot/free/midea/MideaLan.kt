package com.climapilot.free.midea

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom

/** EN: Any LAN protocol-level failure. / DE: Jeder Fehler auf LAN-Protokollebene. */
class MideaProtocolError(msg: String) : Exception(msg)

/**
 * EN: V3 LAN session to a Midea device (TCP 6444). Port of mill1000/midea-msmart lan.py
 *     (_LanProtocolV3 + _Packet + Security). Blocking sockets are wrapped in coroutines (Dispatchers.IO).
 *     Layering, outermost first: 8370 transport framing → encrypted payload → V2 inner packet → AC frame.
 *
 * DE: V3-LAN-Sitzung zu einem Midea-Gerät (TCP 6444). Portierung von mill1000/midea-msmart lan.py
 *     (_LanProtocolV3 + _Packet + Security). Blockierende Sockets sind in Coroutinen gekapselt
 *     (Dispatchers.IO). Schichtung von außen nach innen: 8370-Transport-Framing → verschlüsselte
 *     Nutzlast → V2-Innenpaket → AC-Frame.
 */
class MideaLan(private val ip: String, private val port: Int, private val deviceId: Long) {
    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var localKey: ByteArray? = null
    private var packetId = 0
    private val rng = SecureRandom()

    private val HANDSHAKE_REQUEST = 0x0
    private val HANDSHAKE_RESPONSE = 0x1
    private val ENCRYPTED_RESPONSE = 0x3
    private val ENCRYPTED_REQUEST = 0x6

    val authenticated: Boolean get() = localKey != null

    suspend fun connectAndAuthenticate(tokenHex: String, keyHex: String) = withContext(Dispatchers.IO) {
        close()
        val s = Socket()
        s.connect(InetSocketAddress(ip, port), 3000)
        s.soTimeout = 3000
        socket = s; input = s.getInputStream(); output = s.getOutputStream()

        val token = hexToBytes(tokenHex)
        val key = hexToBytes(keyHex)
        // EN: Handshake: send token, receive 64-byte payload, derive the per-session local key.
        // DE: Handshake: Token senden, 64-Byte-Nutzlast empfangen, den sitzungslokalen Schlüssel ableiten.
        writePacket(encodeHandshake(token))
        val resp = readPacket()
        val payload = decodeHandshakeResponse(resp)
        if (payload.size != 64) throw MideaProtocolError("bad handshake length ${payload.size}")
        val enc = payload.copyOfRange(0, 32)
        val rxHash = payload.copyOfRange(32, 64)
        val dec = MideaCrypto.decryptAesCbc(key, enc)
        if (!MideaCrypto.sha256(dec).contentEquals(rxHash)) throw MideaProtocolError("handshake hash mismatch")
        localKey = MideaCrypto.xor(dec, key)
    }

    /**
     * EN: Send one AC command frame and return the decoded response frame (may be empty).
     * DE: Einen AC-Befehls-Frame senden und den dekodierten Antwort-Frame zurückgeben (ggf. leer).
     */
    suspend fun sendCommand(frame: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        val lk = localKey ?: throw MideaProtocolError("not authenticated")
        val v2 = packetEncode(frame)
        writePacket(encodeEncrypted(lk, v2))
        val resp = readPacket()
        val inner = decodeEncrypted(lk, resp)
        packetDecode(inner)
    }

    fun close() {
        runCatching { socket?.close() }
        socket = null; input = null; output = null; localKey = null
    }

    // EN: ---------- 8370 transport framing (outermost layer) ----------
    // DE: ---------- 8370-Transport-Framing (äußerste Schicht) ----------
    private fun header(length: Int, extra: Int): ByteArray {
        val h = ByteArray(6)
        h[0] = 0x83.toByte(); h[1] = 0x70
        h[2] = ((length shr 8) and 0xFF).toByte(); h[3] = (length and 0xFF).toByte()
        h[4] = 0x20; h[5] = extra.toByte()
        return h
    }

    private fun nextId(): ByteArray {
        val id = packetId
        packetId = (packetId + 1) and 0xFFF
        return byteArrayOf(((id shr 8) and 0xFF).toByte(), (id and 0xFF).toByte())
    }

    private fun encodeHandshake(data: ByteArray): ByteArray =
        header(data.size, HANDSHAKE_REQUEST) + nextId() + data

    private fun encodeEncrypted(key: ByteArray, data: ByteArray): ByteArray {
        val remainder = (data.size + 2) % 16
        val pad = if (remainder != 0) 16 - remainder else 0
        val length = data.size + pad + 32
        val hdr = header(length, (pad shl 4) or ENCRYPTED_REQUEST)
        val padBytes = ByteArray(pad).also { rng.nextBytes(it) }
        val payload = nextId() + data + padBytes
        val calcHash = MideaCrypto.sha256(hdr + payload)
        return hdr + MideaCrypto.encryptAesCbc(key, payload) + calcHash
    }

    private fun decodeHandshakeResponse(packet: ByteArray): ByteArray {
        checkHeader(packet)
        if ((packet[5].toInt() and 0xF) != HANDSHAKE_RESPONSE)
            throw MideaProtocolError("expected handshake response, got 0x%X".format(packet[5].toInt() and 0xF))
        return packet.copyOfRange(8, packet.size) // EN: skip 6-byte header + 2-byte packet id / DE: 6-Byte-Header + 2-Byte-Paket-ID überspringen
    }

    private fun decodeEncrypted(key: ByteArray, packet: ByteArray): ByteArray {
        checkHeader(packet)
        val type = packet[5].toInt() and 0xF
        if (type != ENCRYPTED_RESPONSE) throw MideaProtocolError("expected encrypted response, got 0x%X".format(type))
        val hdr = packet.copyOfRange(0, 6)
        val payload = packet.copyOfRange(6, packet.size - 32)
        val rxHash = packet.copyOfRange(packet.size - 32, packet.size)
        val dec = MideaCrypto.decryptAesCbc(key, payload)
        if (!MideaCrypto.sha256(hdr + dec).contentEquals(rxHash)) throw MideaProtocolError("response hash mismatch")
        val pad = (hdr[5].toInt() and 0xFF) shr 4
        val end = if (pad > 0) dec.size - pad else dec.size
        return dec.copyOfRange(2, end) // EN: strip 2-byte packet id + padding / DE: 2-Byte-Paket-ID + Padding entfernen
    }

    private fun checkHeader(packet: ByteArray) {
        if (packet.size < 6 || packet[0] != 0x83.toByte() || packet[1] != 0x70.toByte())
            throw MideaProtocolError("invalid 8370 start")
        if (packet[4] != 0x20.toByte()) throw MideaProtocolError("invalid magic byte")
    }

    // EN: ---------- socket read/write of a full 8370 packet ----------
    // DE: ---------- Socket-Lesen/-Schreiben eines vollständigen 8370-Pakets ----------
    private fun writePacket(data: ByteArray) {
        val out = output ?: throw MideaProtocolError("not connected")
        out.write(data); out.flush()
    }

    private fun readPacket(): ByteArray {
        val ins = input ?: throw MideaProtocolError("not connected")
        val buf = ArrayList<Byte>(128)
        val tmp = ByteArray(512)
        // EN: First read until the 6-byte header is available, then read the declared remainder.
        // DE: Erst lesen, bis der 6-Byte-Header vorliegt, dann den angegebenen Rest nachlesen.
        while (buf.size < 6) {
            val n = ins.read(tmp); if (n < 0) throw MideaProtocolError("connection closed")
            for (i in 0 until n) buf.add(tmp[i])
        }
        val totalSize = (((buf[2].toInt() and 0xFF) shl 8) or (buf[3].toInt() and 0xFF)) + 8
        while (buf.size < totalSize) {
            val n = ins.read(tmp); if (n < 0) throw MideaProtocolError("connection closed")
            for (i in 0 until n) buf.add(tmp[i])
        }
        return ByteArray(totalSize) { buf[it] }
    }

    // EN: ---------- V2 inner packet (_Packet): wraps the AC frame with id/timestamp + MD5 signature ----------
    // DE: ---------- V2-Innenpaket (_Packet): umhüllt den AC-Frame mit ID/Zeitstempel + MD5-Signatur ----------
    private fun packetEncode(command: ByteArray): ByteArray {
        val enc = MideaCrypto.encryptAes(command)
        val length = 40 + enc.size + 16
        val bb = ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN)
        bb.put(byteArrayOf(0x5A, 0x5A, 0x01, 0x11))
        bb.putShort(length.toShort())          // packet size LE
        bb.put(byteArrayOf(0x20, 0x00))         // magic
        bb.put(ByteArray(4))                    // message id
        bb.put(timestamp())                     // 8-byte timestamp
        bb.put(deviceIdBytes())                 // 8-byte device id LE
        bb.put(ByteArray(12))
        val header = bb.array()
        val packet = header + enc
        return packet + MideaCrypto.sign(packet)
    }

    private fun packetDecode(data: ByteArray): ByteArray {
        if (data.size < 6 || data[0] != 0x5A.toByte() || data[1] != 0x5A.toByte())
            throw MideaProtocolError("unsupported inner packet")
        val length = (data[4].toInt() and 0xFF) or ((data[5].toInt() and 0xFF) shl 8)
        val packet = data.copyOfRange(0, length)
        val encryptedFrame = packet.copyOfRange(40, packet.size - 16)
        val rxHash = packet.copyOfRange(packet.size - 16, packet.size)
        if (!MideaCrypto.sign(packet.copyOfRange(0, packet.size - 16)).contentEquals(rxHash))
            throw MideaProtocolError("inner md5 mismatch")
        return MideaCrypto.decryptAes(encryptedFrame)
    }

    private fun deviceIdBytes(): ByteArray = ByteArray(8) { ((deviceId shr (8 * it)) and 0xFF).toByte() }

    /**
     * EN: 8-byte timestamp: [centisec, sec, min, hour, day, month, year%100, year/100], UTC.
     * DE: 8-Byte-Zeitstempel: [Hundertstelsek., Sek., Min., Std., Tag, Monat, Jahr%100, Jahr/100], UTC.
     */
    private fun timestamp(): ByteArray {
        val c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        val cs = c.get(java.util.Calendar.MILLISECOND) / 10
        val year = c.get(java.util.Calendar.YEAR)
        return byteArrayOf(
            cs.toByte(),
            c.get(java.util.Calendar.SECOND).toByte(),
            c.get(java.util.Calendar.MINUTE).toByte(),
            c.get(java.util.Calendar.HOUR_OF_DAY).toByte(),
            c.get(java.util.Calendar.DAY_OF_MONTH).toByte(),
            (c.get(java.util.Calendar.MONTH) + 1).toByte(),
            (year % 100).toByte(),
            (year / 100).toByte(),
        )
    }

    private fun hexToBytes(s: String): ByteArray =
        ByteArray(s.length / 2) { ((Character.digit(s[it * 2], 16) shl 4) + Character.digit(s[it * 2 + 1], 16)).toByte() }
}
