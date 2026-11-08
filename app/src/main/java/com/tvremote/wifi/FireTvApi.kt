package com.tvremote.wifi

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** An app installed on the TV. */
data class TvApp(val packageName: String, val name: String, val iconUrl: String?)

/** What the TV says it supports over Wi-Fi. */
data class TvCapabilities(val volume: Boolean, val power: Boolean)

/**
 * Fire TV's local remote-control API, the one Amazon's own Fire TV phone app uses.
 * Verified against the Redmi Fire TV (L32MA-FVIN).
 *
 *  - Commands:  https://<tv>:8080  (self-signed certificate)
 *  - Wake:      http://<tv>:8009/apps/FireTVRemote  (DIAL) and Wake-on-LAN
 *  - Auth:      a fixed public API key + a per-phone client token from PIN pairing
 *
 * Every call blocks, so only call it from a background thread or coroutine.
 */
internal class FireTvApi {

    /** The TV answered, but with an error status. (A network failure is a plain IOException.) */
    class HttpError(val code: Int, val body: String) : IOException("HTTP $code $body") {
        val isAuthError: Boolean get() = code == 401 || code == 403
    }

    // ---------------------------------------------------------------- pairing

    /** Asks the TV to show a 4-digit PIN. [phoneName] is shown on the TV. */
    fun displayPin(ip: String, phoneName: String) {
        request(ip, "POST", "/v1/FireTV/pin/display", token = null,
            body = JSONObject().put("friendlyName", phoneName).toString())
    }

    /**
     * Sends the PIN back and returns the client token, or null if the PIN was rejected.
     * The token comes back in the "description" field; "OK" means "not ready yet, retry".
     */
    fun verifyPin(ip: String, pin: String): String? {
        val body = JSONObject().put("pin", pin).toString()
        repeat(3) { attempt ->
            val response = request(ip, "POST", "/v1/FireTV/pin/verify", token = null, body = body)
            val description = description(response)
            if (description.isNotBlank() && description != "OK") return description
            if (attempt < 2) Thread.sleep(1000)
        }
        return null
    }

    // ---------------------------------------------------------------- commands

    /** POST /v1/FireTV?action=… e.g. home, back, menu, dpad_up, select, volume_up, mute, power, sleep. */
    fun action(ip: String, token: String, action: String, keyAction: String? = null) {
        val body = keyAction?.let { JSONObject().put("keyActionType", it).toString() }
        request(ip, "POST", "/v1/FireTV?action=$action", token, body)
    }

    /** Play/pause toggle. */
    fun playPause(ip: String, token: String) {
        request(ip, "POST", "/v1/media?action=play", token, body = null)
    }

    /** Starts rewinding / fast-forwarding. Press again to go faster, play/pause to stop. */
    fun scan(ip: String, token: String, forward: Boolean) {
        val body = JSONObject()
            .put("direction", if (forward) "forward" else "back")
            .put("durationInSeconds", "10")
            .put("speed", "1")
            .toString()
        request(ip, "POST", "/v1/media?action=scan", token, body)
    }

    /** Types one character into the focused text field on the TV. */
    fun text(ip: String, token: String, character: String) {
        request(ip, "POST", "/v1/FireTV/text", token, JSONObject().put("text", character).toString())
    }

    fun launchApp(ip: String, token: String, packageName: String) {
        request(ip, "POST", "/v1/FireTV/app/$packageName", token, body = null, readTimeoutMs = 6000)
    }

    fun apps(ip: String, token: String): List<TvApp> {
        val array = JSONArray(request(ip, "GET", "/v1/FireTV/appsV2", token, body = null, readTimeoutMs = 6000))
        return (0 until array.length())
            .map { array.getJSONObject(it) }
            .filter { it.optBoolean("isInstalled", true) && it.optString("appId").isNotBlank() }
            .map {
                TvApp(
                    packageName = it.getString("appId"),
                    name = it.optString("name").ifBlank { it.getString("appId") },
                    iconUrl = it.optString("tvIconArt").takeIf { url -> url.startsWith("http") },
                )
            }
    }

    fun capabilities(ip: String, token: String, connectTimeoutMs: Int = CONNECT_TIMEOUT_MS): TvCapabilities {
        val json = JSONObject(request(ip, "GET", "/v1/FireTV", token, body = null, connectTimeoutMs = connectTimeoutMs))
        val volume = json.optBoolean("isVolumeControlsSupported", true) &&
            json.optString("volumeCapabilityLevelOfSupport") != "NotSupported"
        val power = json.optString("powerCapabilityLevelOfSupport") != "NotSupported"
        return TvCapabilities(volume, power)
    }

    /** True if the TV's control API answers at all (any HTTP status counts). */
    fun isAnswering(ip: String, token: String?): Boolean = try {
        request(ip, "GET", "/v1/FireTV/status", token, body = null, connectTimeoutMs = 800, readTimeoutMs = 1200)
        true
    } catch (e: HttpError) {
        true
    } catch (e: IOException) {
        false
    }

    // ---------------------------------------------------------------- waking

    /** DIAL wake: starts the TV's remote service when it is idle / in screensaver. */
    fun dialWake(ip: String) {
        val c = URL("http://$ip:8009/apps/FireTVRemote").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 2000
            c.readTimeout = 2000
            c.doOutput = true
            c.setFixedLengthStreamingMode(0)
            c.outputStream.close()
            c.responseCode
        } finally {
            c.disconnect()
        }
    }

    /** Wake-on-LAN magic packet: wakes a TV in deep standby. [mac] is like "AA:BB:CC:DD:EE:FF". */
    fun wakeOnLan(mac: String, ip: String) {
        val macBytes = mac.split(':', '-').map { it.toInt(16).toByte() }
        if (macBytes.size != 6) return
        val packet = ByteArray(6 + 16 * 6)
        for (i in 0 until 6) packet[i] = 0xFF.toByte()
        for (i in 0 until 16) macBytes.forEachIndexed { j, b -> packet[6 + i * 6 + j] = b }

        val subnetBroadcast = ip.substringBeforeLast('.') + ".255" // home networks are /24
        DatagramSocket().use { socket ->
            socket.broadcast = true
            for (target in listOf(subnetBroadcast, "255.255.255.255")) {
                runCatching {
                    socket.send(DatagramPacket(packet, packet.size, InetAddress.getByName(target), 9))
                }
            }
        }
    }

    // ---------------------------------------------------------------- HTTP

    private fun request(
        ip: String,
        method: String,
        path: String,
        token: String?,
        body: String?,
        connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
        readTimeoutMs: Int = READ_TIMEOUT_MS,
    ): String {
        val c = URL("https://$ip:8080$path").openConnection() as HttpsURLConnection
        try {
            c.sslSocketFactory = trustAllSocketFactory
            c.hostnameVerifier = HostnameVerifier { _, _ -> true }
            c.requestMethod = method
            c.connectTimeout = connectTimeoutMs
            c.readTimeout = readTimeoutMs
            c.useCaches = false
            c.setRequestProperty("X-Api-Key", API_KEY)
            if (token != null) c.setRequestProperty("X-Client-Token", token)
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.setRequestProperty("Accept", "application/json")
            if (method == "POST") {
                val bytes = body?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
                c.doOutput = true
                c.setFixedLengthStreamingMode(bytes.size)
                c.outputStream.use { it.write(bytes) }
            }
            val code = c.responseCode
            val stream = if (code < 400) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw HttpError(code, text)
            return text
        } finally {
            c.disconnect()
        }
    }

    private fun description(json: String): String =
        runCatching { JSONObject(json).optString("description") }.getOrDefault("")

    companion object {
        /** Public constant used by every Fire TV and Amazon's app; the client token is the real auth. */
        private const val API_KEY = "0987654321"
        private const val CONNECT_TIMEOUT_MS = 1500
        private const val READ_TIMEOUT_MS = 3000

        /**
         * The TV uses a self-signed certificate, so certificate checks are skipped for these
         * LAN connections only (not set as the app-wide default).
         */
        private val trustAllSocketFactory: SSLSocketFactory by lazy {
            val trustAll = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
            }.socketFactory
        }
    }
}
