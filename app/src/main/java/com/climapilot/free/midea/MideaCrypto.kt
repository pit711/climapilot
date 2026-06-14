package com.climapilot.free.midea

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * EN: Midea LAN crypto. Faithful port of msmart's Security class (mill1000/midea-msmart, lan.py)
 *     plus the CRC8 table and frame checksum used by the AC command framing.
 *     - Discovery replies and V2 command frames: AES-ECB with PKCS7 padding, key = MD5(SIGN_KEY).
 *     - V3 session payloads + key handshake: AES-CBC, IV = 16 zero bytes, manual padding (NoPadding).
 *     - Packet signature: MD5(data + SIGN_KEY).
 *
 * DE: Krypto für das lokale Midea-Protokoll. Originalgetreue Portierung der Security-Klasse von
 *     msmart (mill1000/midea-msmart, lan.py) plus CRC8-Tabelle und Frame-Prüfsumme der AC-Befehle.
 *     - Discovery-Antworten und V2-Befehls-Frames: AES-ECB mit PKCS7-Padding, Schlüssel = MD5(SIGN_KEY).
 *     - V3-Sitzungsdaten + Schlüssel-Handshake: AES-CBC, IV = 16 Null-Bytes, manuelles Padding (NoPadding).
 *     - Paket-Signatur: MD5(data + SIGN_KEY).
 */
object MideaCrypto {
    private val SIGN_KEY = "xhdiwjnchekd4d512chdjx5d8e4c394D2D7S".toByteArray(Charsets.US_ASCII)
    val ENC_KEY: ByteArray = md5(SIGN_KEY)
    private val ZERO_IV = ByteArray(16)

    fun md5(data: ByteArray): ByteArray = MessageDigest.getInstance("MD5").digest(data)
    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    fun sign(data: ByteArray): ByteArray = md5(data + SIGN_KEY)

    /**
     * EN: AES-ECB + PKCS7. Used for discovery replies and V2 command frames.
     * DE: AES-ECB + PKCS7. Wird für Discovery-Antworten und V2-Befehls-Frames verwendet.
     */
    fun encryptAes(data: ByteArray): ByteArray = ecb(Cipher.ENCRYPT_MODE).doFinal(data)
    fun decryptAes(data: ByteArray): ByteArray = ecb(Cipher.DECRYPT_MODE).doFinal(data)

    private fun ecb(mode: Int): Cipher = Cipher.getInstance("AES/ECB/PKCS5Padding").apply {
        init(mode, SecretKeySpec(ENC_KEY, "AES"))
    }

    /**
     * EN: AES-CBC, IV=0, NoPadding. Caller pre-pads to a 16-byte multiple.
     * DE: AES-CBC, IV=0, NoPadding. Der Aufrufer füllt vorab auf ein Vielfaches von 16 Byte auf.
     */
    fun encryptAesCbc(key: ByteArray, data: ByteArray): ByteArray = cbc(Cipher.ENCRYPT_MODE, key).doFinal(data)
    fun decryptAesCbc(key: ByteArray, data: ByteArray): ByteArray = cbc(Cipher.DECRYPT_MODE, key).doFinal(data)

    private fun cbc(mode: Int, key: ByteArray): Cipher = Cipher.getInstance("AES/CBC/NoPadding").apply {
        init(mode, SecretKeySpec(key, "AES"), IvParameterSpec(ZERO_IV))
    }

    /**
     * EN: udpid = let h = sha256(deviceIdBytes); xor(h[0:16], h[16:32]). Identifies the device to
     *     the cloud token endpoint without revealing the raw id.
     * DE: udpid = sei h = sha256(deviceIdBytes); xor(h[0:16], h[16:32]). Identifiziert das Gerät beim
     *     Cloud-Token-Endpunkt, ohne die rohe ID preiszugeben.
     */
    fun udpid(deviceIdBytes: ByteArray): ByteArray {
        val h = sha256(deviceIdBytes)
        return ByteArray(16) { (h[it].toInt() xor h[it + 16].toInt()).toByte() }
    }

    fun xor(a: ByteArray, b: ByteArray): ByteArray = ByteArray(minOf(a.size, b.size)) { (a[it].toInt() xor b[it].toInt()).toByte() }

    // EN: --- AC command framing helpers --- / DE: --- Hilfsfunktionen für das AC-Befehls-Framing ---

    /**
     * EN: Frame checksum: (~sum + 1) & 0xFF over the given bytes (two's-complement of the byte sum).
     * DE: Frame-Prüfsumme: (~sum + 1) & 0xFF über die gegebenen Bytes (Zweierkomplement der Byte-Summe).
     */
    fun frameChecksum(data: ByteArray): Int {
        var sum = 0
        for (b in data) sum += b.toInt() and 0xFF
        return (sum.inv() + 1) and 0xFF
    }

    private val CRC8_854_TABLE = intArrayOf(
        0x00, 0x5E, 0xBC, 0xE2, 0x61, 0x3F, 0xDD, 0x83, 0xC2, 0x9C, 0x7E, 0x20, 0xA3, 0xFD, 0x1F, 0x41,
        0x9D, 0xC3, 0x21, 0x7F, 0xFC, 0xA2, 0x40, 0x1E, 0x5F, 0x01, 0xE3, 0xBD, 0x3E, 0x60, 0x82, 0xDC,
        0x23, 0x7D, 0x9F, 0xC1, 0x42, 0x1C, 0xFE, 0xA0, 0xE1, 0xBF, 0x5D, 0x03, 0x80, 0xDE, 0x3C, 0x62,
        0xBE, 0xE0, 0x02, 0x5C, 0xDF, 0x81, 0x63, 0x3D, 0x7C, 0x22, 0xC0, 0x9E, 0x1D, 0x43, 0xA1, 0xFF,
        0x46, 0x18, 0xFA, 0xA4, 0x27, 0x79, 0x9B, 0xC5, 0x84, 0xDA, 0x38, 0x66, 0xE5, 0xBB, 0x59, 0x07,
        0xDB, 0x85, 0x67, 0x39, 0xBA, 0xE4, 0x06, 0x58, 0x19, 0x47, 0xA5, 0xFB, 0x78, 0x26, 0xC4, 0x9A,
        0x65, 0x3B, 0xD9, 0x87, 0x04, 0x5A, 0xB8, 0xE6, 0xA7, 0xF9, 0x1B, 0x45, 0xC6, 0x98, 0x7A, 0x24,
        0xF8, 0xA6, 0x44, 0x1A, 0x99, 0xC7, 0x25, 0x7B, 0x3A, 0x64, 0x86, 0xD8, 0x5B, 0x05, 0xE7, 0xB9,
        0x8C, 0xD2, 0x30, 0x6E, 0xED, 0xB3, 0x51, 0x0F, 0x4E, 0x10, 0xF2, 0xAC, 0x2F, 0x71, 0x93, 0xCD,
        0x11, 0x4F, 0xAD, 0xF3, 0x70, 0x2E, 0xCC, 0x92, 0xD3, 0x8D, 0x6F, 0x31, 0xB2, 0xEC, 0x0E, 0x50,
        0xAF, 0xF1, 0x13, 0x4D, 0xCE, 0x90, 0x72, 0x2C, 0x6D, 0x33, 0xD1, 0x8F, 0x0C, 0x52, 0xB0, 0xEE,
        0x32, 0x6C, 0x8E, 0xD0, 0x53, 0x0D, 0xEF, 0xB1, 0xF0, 0xAE, 0x4C, 0x12, 0x91, 0xCF, 0x2D, 0x73,
        0xCA, 0x94, 0x76, 0x28, 0xAB, 0xF5, 0x17, 0x49, 0x08, 0x56, 0xB4, 0xEA, 0x69, 0x37, 0xD5, 0x8B,
        0x57, 0x09, 0xEB, 0xB5, 0x36, 0x68, 0x8A, 0xD4, 0x95, 0xCB, 0x29, 0x77, 0xF4, 0xAA, 0x48, 0x16,
        0xE9, 0xB7, 0x55, 0x0B, 0x88, 0xD6, 0x34, 0x6A, 0x2B, 0x75, 0x97, 0xC9, 0x4A, 0x14, 0xF6, 0xA8,
        0x74, 0x2A, 0xC8, 0x96, 0x15, 0x4B, 0xA9, 0xF7, 0xB6, 0xE8, 0x0A, 0x54, 0xD7, 0x89, 0x6B, 0x35,
    )

    fun crc8(data: ByteArray): Int {
        var crc = 0
        for (m in data) crc = CRC8_854_TABLE[(crc xor (m.toInt() and 0xFF)) and 0xFF]
        return crc
    }
}
