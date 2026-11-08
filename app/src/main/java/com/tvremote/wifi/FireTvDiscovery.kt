package com.tvremote.wifi

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL

/** A TV found on the Wi-Fi network. */
data class FoundTv(
    val ip: String,
    val name: String,
    val model: String? = null,
    val mac: String? = null,
    val uuid: String? = null,
)

/**
 * Finds TVs with an SSDP search for the DIAL service, the same way Amazon's Fire TV app does.
 * Each answer points at a small XML file that holds the TV's friendly name.
 */
internal object FireTvDiscovery {

    private const val SEARCH =
        "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: 239.255.255.250:1900\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: 1\r\n" +
            "ST: urn:dial-multiscreen-org:service:dial:1\r\n\r\n"

    private val HEADER = Regex("^([^:\\r\\n]+):(.*)$", RegexOption.MULTILINE)
    private val MAC = Regex("MAC=([0-9A-Fa-f]{2}(?:[:-][0-9A-Fa-f]{2}){5})")

    private data class Reply(val ip: String, val location: String?, val mac: String?, val uuid: String?)

    suspend fun search(context: Context, listenMs: Long = 3000): List<FoundTv> = withContext(Dispatchers.IO) {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val lock = wifi?.createMulticastLock("tvremote-ssdp")?.apply {
            setReferenceCounted(false)
            acquire()
        }
        try {
            val replies = collectReplies(listenMs)
            coroutineScope {
                replies.map { reply -> async { describe(reply) } }.awaitAll()
            }
        } finally {
            lock?.release()
        }
    }

    private fun collectReplies(listenMs: Long): List<Reply> {
        val found = LinkedHashMap<String, Reply>()
        DatagramSocket().use { socket ->
            socket.soTimeout = 300
            val message = SEARCH.toByteArray()
            val group = InetAddress.getByName("239.255.255.250")
            repeat(3) {
                socket.send(DatagramPacket(message, message.size, group, 1900))
                Thread.sleep(80)
            }
            val buffer = ByteArray(4096)
            val end = System.currentTimeMillis() + listenMs
            while (System.currentTimeMillis() < end) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (e: SocketTimeoutException) {
                    continue
                }
                val text = String(packet.data, 0, packet.length)
                val headers = HEADER.findAll(text).associate { it.groupValues[1].trim().uppercase() to it.groupValues[2].trim() }
                val ip = packet.address?.hostAddress ?: continue
                val uuid = headers["USN"]?.substringAfter("uuid:", "")?.substringBefore("::")?.takeIf { it.isNotBlank() }
                val mac = headers["WAKEUP"]?.let { MAC.find(it)?.groupValues?.get(1) }
                // The same TV can answer from two addresses; the uuid identifies it.
                found.putIfAbsent(uuid ?: ip, Reply(ip, headers["LOCATION"], mac, uuid))
            }
        }
        return found.values.toList()
    }

    private fun describe(reply: Reply): FoundTv {
        var name: String? = null
        var model: String? = null
        val location = reply.location
        if (location != null) {
            runCatching {
                val c = URL(location).openConnection() as HttpURLConnection
                try {
                    c.connectTimeout = 2000
                    c.readTimeout = 2000
                    val xml = c.inputStream.bufferedReader().use { it.readText() }
                    name = tag(xml, "friendlyName")
                    model = tag(xml, "modelName")
                } finally {
                    c.disconnect()
                }
            }
        }
        return FoundTv(
            ip = reply.ip,
            name = name ?: "TV at ${reply.ip}",
            model = model,
            mac = reply.mac,
            uuid = reply.uuid,
        )
    }

    private fun tag(xml: String, name: String): String? =
        Regex("<$name>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)
            ?.replace("&apos;", "'")?.replace("&quot;", "\"")?.replace("&lt;", "<")
            ?.replace("&gt;", ">")?.replace("&amp;", "&")
            ?.trim()?.takeIf { it.isNotEmpty() }
}
