package com.tvremote.wifi

import android.content.Context
import android.os.Build
import android.util.Log
import com.tvremote.hid.RemoteButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Controls a Fire TV over Wi-Fi: PIN pairing, commands, typing, app launch, and waking
 * the TV when it is asleep. Commands run strictly in order on one background queue.
 */
class WifiRemote(context: Context) {

    enum class Status { NOT_PAIRED, CHECKING, CONNECTED, ASLEEP, WAKING, UNREACHABLE, AUTH_FAILED }

    data class State(val status: Status, val tvName: String? = null, val message: String? = null)

    data class PairedTv(val ip: String, val name: String, val token: String, val mac: String?, val uuid: String?)

    sealed interface Pairing {
        data object Idle : Pairing
        data class Requesting(val tv: FoundTv) : Pairing
        data class EnterPin(val tv: FoundTv, val error: String? = null) : Pairing
        data class Verifying(val tv: FoundTv) : Pairing
        data class Failed(val tv: FoundTv, val message: String) : Pairing
    }

    data class Scan(val scanning: Boolean = false, val results: List<FoundTv> = emptyList(), val done: Boolean = false)

    private val appContext = context.applicationContext
    private val api = FireTvApi()
    private val prefs = appContext.getSharedPreferences("wifi_remote", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var paired: PairedTv? = loadPaired()

    private val _state = MutableStateFlow(
        paired?.let { State(Status.CHECKING, it.name) } ?: State(Status.NOT_PAIRED)
    )
    val state: StateFlow<State> = _state.asStateFlow()

    private val _pairing = MutableStateFlow<Pairing>(Pairing.Idle)
    val pairing: StateFlow<Pairing> = _pairing.asStateFlow()

    private val _scan = MutableStateFlow(Scan())
    val scan: StateFlow<Scan> = _scan.asStateFlow()

    private val _apps = MutableStateFlow<List<TvApp>>(emptyList())
    val apps: StateFlow<List<TvApp>> = _apps.asStateFlow()

    private val _capabilities = MutableStateFlow<TvCapabilities?>(null)
    val capabilities: StateFlow<TvCapabilities?> = _capabilities.asStateFlow()

    val isPaired: Boolean get() = paired != null
    val pairedTv: PairedTv? get() = paired

    // ---------------------------------------------------------------- command queue

    private val queue = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val pending = AtomicInteger(0)
    private val repeatJobs = ConcurrentHashMap<RemoteButton, Job>()

    init {
        scope.launch {
            for (job in queue) {
                try {
                    job()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "command failed", e)
                } finally {
                    pending.decrementAndGet()
                }
            }
        }
    }

    private fun enqueue(job: suspend () -> Unit) {
        pending.incrementAndGet()
        queue.trySend(job)
    }

    // ---------------------------------------------------------------- lifecycle

    /** Checks whether the paired TV answers. Doesn't wake it (opening the app shouldn't turn the TV on). */
    fun start() {
        val tv = paired ?: run {
            _state.value = State(Status.NOT_PAIRED)
            return
        }
        scope.launch { check(tv, wakeIfNeeded = false) }
    }

    /** Stops hold-to-repeat (called when switching to Bluetooth or leaving the app). */
    fun stop() {
        repeatJobs.values.forEach { it.cancel() }
        repeatJobs.clear()
    }

    fun close() {
        stop()
        scope.cancel()
    }

    /** Retry button: wake the TV if needed, then reconnect. */
    fun reconnect() {
        val tv = paired ?: return
        scope.launch { check(tv, wakeIfNeeded = true) }
    }

    fun forget() {
        stop()
        paired = null
        prefs.edit().clear().apply()
        _apps.value = emptyList()
        _capabilities.value = null
        _state.value = State(Status.NOT_PAIRED)
    }

    private suspend fun check(tv: PairedTv, wakeIfNeeded: Boolean) {
        _state.value = State(Status.CHECKING, tv.name)
        try {
            _capabilities.value = api.capabilities(tv.ip, tv.token)
            onReachable(tv)
            return
        } catch (e: FireTvApi.HttpError) {
            handleHttpError(tv, e)
            return
        } catch (e: IOException) {
            // fall through
        }
        if (!wakeIfNeeded) {
            _state.value = State(Status.ASLEEP, tv.name, "${tv.name} isn't answering. It may be asleep or off. Press any button to wake it.")
            return
        }
        if (wakeOrRelocate(tv)) {
            val current = paired ?: return
            runCatching { _capabilities.value = api.capabilities(current.ip, current.token) }
            onReachable(current)
        }
    }

    // ---------------------------------------------------------------- discovery & pairing

    fun scan() {
        if (_scan.value.scanning) return
        _scan.value = _scan.value.copy(scanning = true)
        scope.launch {
            val results = runCatching { FireTvDiscovery.search(appContext) }.getOrDefault(emptyList())
            _scan.value = Scan(scanning = false, results = results, done = true)
            // If the paired TV got a new IP address from the router, follow it.
            val tv = paired
            if (tv?.uuid != null) {
                results.firstOrNull { it.uuid == tv.uuid && it.ip != tv.ip }?.let { moved ->
                    savePaired(tv.copy(ip = moved.ip))
                }
            }
        }
    }

    /** Step 1: ask [tv] to show a PIN. */
    fun beginPairing(tv: FoundTv) {
        _pairing.value = Pairing.Requesting(tv)
        scope.launch {
            val shown = try {
                api.displayPin(tv.ip, phoneLabel())
                true
            } catch (e: FireTvApi.HttpError) {
                false
            } catch (e: IOException) {
                // Probably idle/asleep: wake it, then try once more.
                wake(tv.ip, tv.mac, token = null) && runCatching { api.displayPin(tv.ip, phoneLabel()) }.isSuccess
            }
            _pairing.value = if (shown) {
                Pairing.EnterPin(tv)
            } else {
                Pairing.Failed(tv, "Couldn't reach ${tv.name}. Check that the TV is on and on the same Wi-Fi as this phone.")
            }
        }
    }

    /** Step 2: send the PIN the user read off the TV. */
    fun submitPin(pin: String) {
        val tv = when (val p = _pairing.value) {
            is Pairing.EnterPin -> p.tv
            else -> return
        }
        _pairing.value = Pairing.Verifying(tv)
        scope.launch {
            val token = try {
                api.verifyPin(tv.ip, pin.trim())
            } catch (e: IOException) {
                null
            }
            if (token == null) {
                _pairing.value = Pairing.EnterPin(tv, error = "That PIN didn't work. Check the TV and try again.")
                return@launch
            }
            val newTv = PairedTv(tv.ip, tv.name, token, tv.mac, tv.uuid)
            savePaired(newTv)
            _pairing.value = Pairing.Idle
            check(newTv, wakeIfNeeded = false)
        }
    }

    fun cancelPairing() {
        _pairing.value = Pairing.Idle
    }

    // ---------------------------------------------------------------- buttons

    fun isSupported(button: RemoteButton): Boolean = when (button) {
        RemoteButton.NEXT, RemoteButton.PREVIOUS, RemoteButton.BACKSPACE -> false
        RemoteButton.VOLUME_UP, RemoteButton.VOLUME_DOWN, RemoteButton.MUTE ->
            _capabilities.value?.volume != false // an unknown capability counts as supported
        else -> true
    }

    /** Finger down. D-pad and volume repeat while held (as separate presses, so nothing gets stuck). */
    fun press(button: RemoteButton) {
        if (!isSupported(button)) return
        enqueue { sendButton(button) }
        if (button in REPEATING) {
            repeatJobs.remove(button)?.cancel()
            repeatJobs[button] = scope.launch {
                delay(HOLD_DELAY_MS)
                while (isActive) {
                    // Skip a repeat if the TV is still busy with the last one.
                    if (pending.get() == 0) enqueue { sendButton(button) }
                    delay(REPEAT_MS)
                }
            }
        }
    }

    /** Finger up. */
    fun release(button: RemoteButton) {
        repeatJobs.remove(button)?.cancel()
    }

    fun tap(button: RemoteButton, times: Int = 1, gapMs: Long = 180) {
        if (!isSupported(button)) return
        enqueue {
            repeat(times.coerceIn(1, 50)) { i ->
                if (i > 0) delay(gapMs)
                if (!sendButton(button)) return@enqueue
            }
        }
    }

    /** Types text into the focused field on the TV (one request per character, like Amazon's app). */
    fun typeText(text: String) {
        if (text.isEmpty()) return
        enqueue {
            for (codePoint in text.codePoints().toArray()) {
                val ch = String(Character.toChars(codePoint))
                val ok = withTv { tv -> api.text(tv.ip, tv.token, ch) } != null
                if (!ok) break
            }
        }
    }

    fun launchApp(packageName: String) {
        enqueue { withTv { tv -> api.launchApp(tv.ip, tv.token, packageName) } }
    }

    /** Turns the TV on (wakes it) without toggling it off if it's already on. */
    fun powerOn() {
        val tv = paired ?: return
        enqueue {
            if (api.isAnswering(tv.ip, tv.token)) {
                onReachable(tv)
            } else if (wakeOrRelocate(tv)) {
                paired?.let { onReachable(it) }
            }
        }
    }

    fun refreshApps() {
        val tv = paired ?: return
        scope.launch { loadApps(tv) }
    }

    /** Best match for a spoken app name, e.g. "hotstar" -> JioHotstar. */
    fun findApp(spoken: String): TvApp? {
        val query = normalize(spoken)
        if (query.isEmpty()) return null
        val words = spoken.lowercase(Locale.ROOT).split(Regex("\\s+")).map(::normalize).filter { it.isNotEmpty() }
        return _apps.value
            .map { app -> app to normalize(app.name) }
            .mapNotNull { (app, name) ->
                val score = when {
                    name == query -> 0
                    name.startsWith(query) -> 1
                    name.contains(query) -> 2
                    words.isNotEmpty() && words.all { name.contains(it) } -> 3
                    normalize(app.packageName).contains(query) -> 4
                    else -> return@mapNotNull null
                }
                Triple(app, score, name.length)
            }
            .minWithOrNull(compareBy({ it.second }, { it.third }))
            ?.first
    }

    // ---------------------------------------------------------------- sending

    /** Sends one button press. Returns false if the TV couldn't be reached. */
    private suspend fun sendButton(button: RemoteButton): Boolean {
        val result: Unit? = when (button) {
            RemoteButton.UP -> withTv { api.action(it.ip, it.token, "dpad_up") }
            RemoteButton.DOWN -> withTv { api.action(it.ip, it.token, "dpad_down") }
            RemoteButton.LEFT -> withTv { api.action(it.ip, it.token, "dpad_left") }
            RemoteButton.RIGHT -> withTv { api.action(it.ip, it.token, "dpad_right") }
            RemoteButton.OK, RemoteButton.ENTER -> pressSelect()
            RemoteButton.BACK -> withTv { api.action(it.ip, it.token, "back") }
            RemoteButton.HOME -> withTv { api.action(it.ip, it.token, "home") }
            RemoteButton.MENU -> withTv { api.action(it.ip, it.token, "menu") }
            RemoteButton.PLAY_PAUSE -> withTv { api.playPause(it.ip, it.token) }
            RemoteButton.REWIND -> withTv { api.scan(it.ip, it.token, forward = false) }
            RemoteButton.FAST_FORWARD -> withTv { api.scan(it.ip, it.token, forward = true) }
            RemoteButton.VOLUME_UP -> withTv { api.action(it.ip, it.token, "volume_up") }
            RemoteButton.VOLUME_DOWN -> withTv { api.action(it.ip, it.token, "volume_down") }
            RemoteButton.MUTE -> withTv { api.action(it.ip, it.token, "mute") }
            // If the TV is asleep, waking it *is* the power press, so don't send "power" again
            // afterwards (that would switch it straight back off).
            RemoteButton.POWER -> withTv(resendAfterWake = false) {
                val power = _capabilities.value?.power != false
                api.action(it.ip, it.token, if (power) "power" else "sleep")
            }
            RemoteButton.NEXT, RemoteButton.PREVIOUS, RemoteButton.BACKSPACE -> Unit
        }
        return result != null
    }

    /** OK is a real key press (down, ~220 ms, up), like Amazon's app. The key-up is retried so it never sticks. */
    private suspend fun pressSelect(): Unit? {
        withTv { api.action(it.ip, it.token, "select", "keyDown") } ?: return null
        delay(KEY_PRESS_MS)
        val tv = paired ?: return null
        repeat(3) { attempt ->
            try {
                api.action(tv.ip, tv.token, "select", "keyUp")
                return Unit
            } catch (e: IOException) {
                if (attempt < 2) delay(150)
            }
        }
        return null
    }

    /**
     * Runs [block] against the paired TV. If the TV doesn't answer, wakes it (DIAL + Wake-on-LAN),
     * follows it to a new IP if it moved, and retries once. Returns null on failure.
     */
    private suspend fun <T> withTv(resendAfterWake: Boolean = true, block: (PairedTv) -> T): T? {
        val tv = paired ?: return null
        try {
            return block(tv).also { onReachable(tv) }
        } catch (e: FireTvApi.HttpError) {
            handleHttpError(tv, e)
            return null
        } catch (e: IOException) {
            // Not answering: idle, asleep, or a new IP address.
        }
        if (!wakeOrRelocate(tv)) return null
        val current = paired ?: return null
        if (!resendAfterWake) {
            onReachable(current)
            return null
        }
        return try {
            block(current).also { onReachable(current) }
        } catch (e: FireTvApi.HttpError) {
            handleHttpError(current, e)
            null
        } catch (e: IOException) {
            _state.value = State(Status.UNREACHABLE, current.name, unreachableMessage(current))
            null
        }
    }

    private suspend fun wakeOrRelocate(tv: PairedTv): Boolean {
        _state.value = State(Status.WAKING, tv.name, "Waking ${tv.name}…")
        if (wake(tv.ip, tv.mac, tv.token)) return true

        // Maybe the router gave the TV a new address: look for it by its uuid.
        if (tv.uuid != null) {
            val moved = runCatching { FireTvDiscovery.search(appContext, listenMs = 2500) }
                .getOrDefault(emptyList())
                .firstOrNull { it.uuid == tv.uuid && it.ip != tv.ip }
            if (moved != null) {
                val updated = tv.copy(ip = moved.ip, mac = tv.mac ?: moved.mac)
                savePaired(updated)
                if (api.isAnswering(updated.ip, updated.token) || wake(updated.ip, updated.mac, updated.token)) return true
            }
        }
        _state.value = State(Status.UNREACHABLE, tv.name, unreachableMessage(tv))
        return false
    }

    /** Wake-on-LAN + DIAL wake, repeated, until the control API answers. */
    private suspend fun wake(ip: String, mac: String?, token: String?): Boolean {
        val deadline = System.currentTimeMillis() + WAKE_BUDGET_MS
        while (System.currentTimeMillis() < deadline) {
            if (mac != null) runCatching { api.wakeOnLan(mac, ip) }
            runCatching { api.dialWake(ip) }
            val pollUntil = System.currentTimeMillis() + WAKE_ATTEMPT_MS
            while (System.currentTimeMillis() < pollUntil) {
                if (api.isAnswering(ip, token)) return true
                delay(250)
            }
        }
        return false
    }

    private fun onReachable(tv: PairedTv) {
        val wasConnected = _state.value.status == Status.CONNECTED
        _state.value = State(Status.CONNECTED, tv.name)
        if (!wasConnected) {
            scope.launch {
                if (_capabilities.value == null) runCatching { _capabilities.value = api.capabilities(tv.ip, tv.token) }
                if (_apps.value.isEmpty()) loadApps(tv)
            }
        }
    }

    private fun loadApps(tv: PairedTv) {
        runCatching { api.apps(tv.ip, tv.token) }
            .onSuccess { list -> _apps.value = list.sortedBy { it.name.lowercase(Locale.ROOT) } }
            .onFailure { Log.w(TAG, "couldn't load apps", it) }
    }

    private fun handleHttpError(tv: PairedTv, e: FireTvApi.HttpError) {
        if (e.isAuthError) {
            _state.value = State(Status.AUTH_FAILED, tv.name, "${tv.name} no longer accepts this phone. Pair again.")
        } else {
            Log.w(TAG, "TV returned HTTP ${e.code}")
        }
    }

    private fun unreachableMessage(tv: PairedTv) =
        "Can't reach ${tv.name}. Check that it's plugged in and that the phone is on the same Wi-Fi."

    // ---------------------------------------------------------------- storage

    private fun loadPaired(): PairedTv? {
        val ip = prefs.getString(KEY_IP, null) ?: return null
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        return PairedTv(
            ip = ip,
            name = prefs.getString(KEY_NAME, null) ?: "TV at $ip",
            token = token,
            mac = prefs.getString(KEY_MAC, null),
            uuid = prefs.getString(KEY_UUID, null),
        )
    }

    private fun savePaired(tv: PairedTv) {
        paired = tv
        prefs.edit()
            .putString(KEY_IP, tv.ip)
            .putString(KEY_NAME, tv.name)
            .putString(KEY_TOKEN, tv.token)
            .putString(KEY_MAC, tv.mac)
            .putString(KEY_UUID, tv.uuid)
            .apply()
    }

    private fun phoneLabel(): String {
        val model = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Phone"
        return "$model (TV Remote)"
    }

    private fun normalize(s: String): String =
        s.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    private companion object {
        const val TAG = "WifiRemote"
        const val KEY_IP = "ip"
        const val KEY_NAME = "name"
        const val KEY_TOKEN = "token"
        const val KEY_MAC = "mac"
        const val KEY_UUID = "uuid"

        const val KEY_PRESS_MS = 220L     // OK press length used by Amazon's app
        const val HOLD_DELAY_MS = 400L    // hold this long before repeating
        const val REPEAT_MS = 220L        // repeat rate while held
        const val WAKE_ATTEMPT_MS = 2500L
        const val WAKE_BUDGET_MS = 20_000L

        val REPEATING = setOf(
            RemoteButton.UP, RemoteButton.DOWN, RemoteButton.LEFT, RemoteButton.RIGHT,
            RemoteButton.VOLUME_UP, RemoteButton.VOLUME_DOWN,
        )
    }
}
