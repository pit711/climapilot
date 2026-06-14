package com.climapilot.free.midea

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** EN: Any failure talking to the NetHome Plus cloud. / DE: Jeder Fehler bei der Kommunikation mit der NetHome-Plus-Cloud. */
class MideaCloudError(msg: String) : Exception(msg)

/**
 * EN: Minimal NetHome Plus cloud client. Port of mill1000/midea-msmart cloud.py (NetHomePlusCloud).
 *     Uses the library's bundled default account for the region, so the user never logs in — but it
 *     still needs internet access to mapp.appsmb.com to fetch the per-device token + key. That token
 *     is the ONLY thing fetched online; all actual AC control afterwards is purely local (LAN).
 *
 * DE: Minimaler NetHome-Plus-Cloud-Client. Portierung von mill1000/midea-msmart cloud.py
 *     (NetHomePlusCloud). Nutzt das mitgelieferte Standardkonto der Region, sodass sich der Nutzer
 *     nie anmelden muss — benötigt aber Internetzugang zu mapp.appsmb.com, um Token + Schlüssel pro
 *     Gerät zu holen. Dieses Token ist das EINZIGE, was online geholt wird; die eigentliche
 *     Klima-Steuerung danach läuft rein lokal (LAN).
 */
class MideaCloud(region: String = "DE") {
    private val baseUrl = "https://mapp.appsmb.com"
    private val appId = "1017"
    private val appKey = "3742e9e5842d4ad59c2db887e12449f9"
    private val deviceId = randomHex(8)

    private val account: String
    private val password: String
    private var loginId: String? = null
    private var sessionId: String = ""

    init {
        val creds = mapOf(
            "DE" to ("nethome+de@mailinator.com" to "password1"),
            "KR" to ("nethome+sea@mailinator.com" to "password1"),
            "US" to ("nethome+us@mailinator.com" to "password1"),
        )[region] ?: throw MideaCloudError("Unknown region $region")
        account = creds.first
        password = creds.second
    }

    /**
     * EN: Fetch the token + key (hex strings) for a device's udpid (logs in lazily if needed).
     * DE: Token + Schlüssel (Hex-Strings) für die udpid eines Geräts holen (meldet sich bei Bedarf an).
     */
    suspend fun getToken(udpidHex: String): Pair<String, String> = withContext(Dispatchers.IO) {
        login()
        val result = apiRequest("/v1/iot/secure/getToken", mapOf("udpid" to udpidHex))
        val list = result.optJSONArray("tokenlist") ?: throw MideaCloudError("no tokenlist")
        for (i in 0 until list.length()) {
            val t = list.getJSONObject(i)
            if (t.optString("udpId") == udpidHex) return@withContext t.getString("token") to t.getString("key")
        }
        throw MideaCloudError("no token/key for udpid")
    }

    /**
     * EN: Log in once (two steps: resolve loginId, then authenticate) and cache the session id.
     * DE: Einmal anmelden (zwei Schritte: loginId ermitteln, dann authentifizieren) und die Session-ID zwischenspeichern.
     */
    private fun login() {
        if (sessionId.isNotEmpty()) return
        if (loginId == null) {
            val r = apiRequest("/v1/user/login/id/get", mapOf("loginAccount" to account))
            loginId = r.getString("loginId")
        }
        val r = apiRequest(
            "/v1/user/login",
            mapOf("loginAccount" to account, "password" to encryptPassword(loginId!!, password)),
        )
        sessionId = r.getString("sessionId")
    }

    /**
     * EN: POST a signed, form-encoded request and return the `result` object when errorCode == 0.
     * DE: Eine signierte, formularkodierte POST-Anfrage senden und das `result`-Objekt zurückgeben, wenn errorCode == 0.
     */
    private fun apiRequest(endpoint: String, extra: Map<String, String>): JSONObject {
        val body = LinkedHashMap<String, String>()
        body["appId"] = appId
        body["src"] = appId
        body["format"] = "2"
        body["clientType"] = "1"
        body["language"] = "en_US"
        body["deviceId"] = deviceId
        body["stamp"] = timestamp()
        body["sessionId"] = sessionId
        body.putAll(extra)
        body["sign"] = sign(endpoint, body)

        val conn = (URL(baseUrl + endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connectTimeout = 10000
            readTimeout = 10000
            doOutput = true
        }
        val form = body.entries.joinToString("&") {
            URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
        }
        conn.outputStream.use { it.write(form.toByteArray()) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        if (code !in 200..299) throw MideaCloudError("HTTP $code: $text")

        val json = JSONObject(text)
        if (json.optString("errorCode", "0") != "0")
            throw MideaCloudError("API ${json.optString("errorCode")}: ${json.optString("msg")}")
        return json.getJSONObject("result")
    }

    /**
     * EN: sign = sha256(path + sortedDecodedQuery + appKey). The body must exclude the sign field itself.
     * DE: sign = sha256(path + sortierte_dekodierte_Query + appKey). Der Body darf das sign-Feld selbst nicht enthalten.
     */
    private fun sign(path: String, body: Map<String, String>): String {
        val query = body.entries.sortedBy { it.key }.joinToString("&") { "${it.key}=${it.value}" }
        return hex(MideaCrypto.sha256((path + query + appKey).toByteArray(Charsets.US_ASCII)))
    }

    private fun encryptPassword(loginId: String, pw: String): String {
        val m1 = hex(MideaCrypto.sha256(pw.toByteArray(Charsets.US_ASCII)))
        return hex(MideaCrypto.sha256((loginId + m1 + appKey).toByteArray(Charsets.US_ASCII)))
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())

    private fun randomHex(bytes: Int): String {
        val b = ByteArray(bytes); java.security.SecureRandom().nextBytes(b); return hex(b)
    }

    private fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }
}
