package com.tvremote.hid

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

/**
 * Makes the phone a Bluetooth HID device (keyboard + remote/media keys) that a Fire TV
 * pairs with like any Bluetooth keyboard. Uses [BluetoothHidDevice] (Android 9+).
 *
 * The caller must hold BLUETOOTH_CONNECT (Android 12+) before calling [start].
 */
@SuppressLint("MissingPermission")
class BluetoothHidRemote(context: Context) {

    enum class Status { STARTING, UNSUPPORTED, ERROR, BLUETOOTH_OFF, READY, CONNECTING, CONNECTED }

    data class State(val status: Status, val deviceName: String? = null, val message: String? = null)

    data class PairedDevice(
        val address: String,
        val name: String,
        val isLastUsed: Boolean,
        val isConnected: Boolean,
    )

    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? =
        appContext.getSystemService(BluetoothManager::class.java)?.adapter
    private val prefs = appContext.getSharedPreferences("hid_remote", Context.MODE_PRIVATE)

    private val callbackExecutor = Executors.newSingleThreadExecutor()
    /** All reports go through one thread so presses, releases and typed text stay in order. */
    private val sendExecutor = Executors.newSingleThreadExecutor()

    private val _state = MutableStateFlow(State(Status.STARTING))
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile private var hid: BluetoothHidDevice? = null
    @Volatile private var host: BluetoothDevice? = null
    @Volatile private var registered = false
    @Volatile private var registering = false
    private var proxyRequested = false
    private var receiverRegistered = false
    private var lastDownAt = 0L // only used on sendExecutor

    val isConnected: Boolean get() = hid != null && host != null

    /** The phone's Bluetooth name, which is what the TV shows in its device list. */
    val phoneName: String? get() = runCatching { adapter?.name }.getOrNull()

    // ---------------------------------------------------------------- lifecycle

    fun start() {
        registerStateReceiver()
        val a = adapter
        if (a == null) {
            update(Status.UNSUPPORTED, message = "This phone has no Bluetooth.")
            return
        }
        if (!a.isEnabled) {
            update(Status.BLUETOOTH_OFF)
            return
        }
        val h = hid
        if (h != null) {
            register(h)
            return
        }
        if (proxyRequested) return
        update(Status.STARTING)
        proxyRequested = a.getProfileProxy(appContext, serviceListener, BluetoothProfile.HID_DEVICE)
        if (!proxyRequested) {
            update(
                Status.UNSUPPORTED,
                message = "This phone can't act as a Bluetooth remote (the HID Device profile isn't available).",
            )
        }
    }

    fun restart() {
        teardown()
        start()
    }

    fun stop() {
        unregisterStateReceiver()
        teardown()
    }

    private fun teardown() {
        val h = hid
        hid = null
        host = null
        registered = false
        registering = false
        proxyRequested = false
        if (h != null) {
            runCatching { h.unregisterApp() }
            runCatching { adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, h) }
        }
    }

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            val h = proxy as? BluetoothHidDevice ?: return
            hid = h
            register(h)
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            hid = null
            host = null
            registered = false
            registering = false
            proxyRequested = false
            update(if (adapter?.isEnabled == true) Status.STARTING else Status.BLUETOOTH_OFF)
        }
    }

    private fun register(h: BluetoothHidDevice) {
        if (registered || registering) return
        registering = true
        val sdp = BluetoothHidDeviceAppSdpSettings(
            SDP_NAME,
            SDP_DESCRIPTION,
            SDP_PROVIDER,
            BluetoothHidDevice.SUBCLASS1_KEYBOARD,
            HidDescriptor.REPORT_MAP,
        )
        val ok = runCatching { h.registerApp(sdp, null, null, callbackExecutor, hidCallback) }
            .onFailure { Log.w(TAG, "registerApp failed", it) }
            .getOrDefault(false)
        if (!ok) {
            registering = false
            update(
                Status.ERROR,
                message = "Couldn't start the Bluetooth remote. Close other keyboard or remote apps, then tap Retry.",
            )
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            registering = false
            this@BluetoothHidRemote.registered = registered
            if (registered) {
                if (host == null) update(Status.READY)
                // Reconnect to the TV used last time (or the one the stack says is plugged in).
                (pluggedDevice ?: lastUsedDevice())?.let { connectDevice(it) }
            } else {
                host = null
                if (hid == null) return // we stopped on purpose
                if (adapter?.isEnabled == true) {
                    update(
                        Status.ERROR,
                        message = "The Bluetooth remote was stopped, maybe by another app. Tap Retry.",
                    )
                } else {
                    update(Status.BLUETOOTH_OFF)
                }
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            device ?: return
            when (state) {
                BluetoothProfile.STATE_CONNECTING ->
                    if (host == null) update(Status.CONNECTING, deviceName = nameOf(device))

                BluetoothProfile.STATE_CONNECTED -> {
                    host = device
                    prefs.edit().putString(KEY_LAST_DEVICE, device.address).apply()
                    update(Status.CONNECTED, deviceName = nameOf(device))
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    val current = host
                    if (current == null || current.address == device.address) {
                        val wasConnecting = _state.value.status == Status.CONNECTING
                        host = null
                        update(
                            if (registered) Status.READY else Status.STARTING,
                            message = if (wasConnecting) {
                                "Couldn't reach ${nameOf(device)}. Make sure the TV is on and nearby."
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            val h = hid ?: return
            device ?: return
            val size = when (id.toInt()) {
                HidDescriptor.ID_KEYBOARD -> HidDescriptor.KEYBOARD_REPORT_SIZE
                HidDescriptor.ID_CONSUMER -> HidDescriptor.CONSUMER_REPORT_SIZE
                else -> 0
            }
            runCatching {
                if (type == BluetoothHidDevice.REPORT_TYPE_INPUT && size > 0) {
                    h.replyReport(device, type, id, ByteArray(size))
                } else {
                    h.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID)
                }
            }
        }

        override fun onSetReport(device: BluetoothDevice?, type: Byte, id: Byte, data: ByteArray?) {
            // The TV may send keyboard LED state (Caps Lock etc). Accept and ignore it.
            device ?: return
            runCatching { hid?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS) }
        }
    }

    // ---------------------------------------------------------------- connections

    fun connect(address: String) {
        val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull() ?: return
        connectDevice(device)
    }

    private fun connectDevice(device: BluetoothDevice) {
        val h = hid ?: return
        val current = host
        if (current != null && current.address != device.address) {
            runCatching { h.disconnect(current) }
        }
        val ok = runCatching { h.connect(device) }.getOrDefault(false)
        if (ok && host == null) update(Status.CONNECTING, deviceName = nameOf(device))
    }

    fun disconnect() {
        val h = hid ?: return
        val d = host ?: return
        runCatching { h.disconnect(d) }
    }

    /** Devices already paired with the phone, the connected / last-used TV first. */
    fun pairedDevices(): List<PairedDevice> {
        val bonded = runCatching { adapter?.bondedDevices?.toList() }.getOrNull().orEmpty()
        val last = prefs.getString(KEY_LAST_DEVICE, null)
        val connected = host?.address
        return bonded
            .map { PairedDevice(it.address, nameOf(it), it.address == last, it.address == connected) }
            .sortedWith(
                compareByDescending<PairedDevice> { it.isConnected }
                    .thenByDescending { it.isLastUsed }
                    .thenBy { it.name.lowercase() }
            )
    }

    private fun lastUsedDevice(): BluetoothDevice? {
        val address = prefs.getString(KEY_LAST_DEVICE, null) ?: return null
        return runCatching { adapter?.bondedDevices?.firstOrNull { it.address == address } }.getOrNull()
    }

    private fun nameOf(device: BluetoothDevice): String =
        runCatching { device.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: device.address

    // ---------------------------------------------------------------- sending keys

    /** Finger down on a button. The TV auto-repeats while it's held (e.g. volume, D-pad). */
    fun press(button: RemoteButton) = enqueue { down(button) }

    /** Finger up. */
    fun release(button: RemoteButton) = enqueue { up(button) }

    fun tap(button: RemoteButton, times: Int = 1, gapMs: Long = DEFAULT_GAP_MS) = enqueue {
        repeat(times.coerceIn(1, MAX_TAPS)) { i ->
            if (i > 0) Thread.sleep(gapMs)
            down(button)
            up(button)
        }
    }

    /** Types text into whatever field has focus on the TV. */
    fun typeText(text: String) = enqueue {
        for (c in HidKeys.toTypeable(text)) {
            val key = HidKeys.forChar(c) ?: continue
            sendKeyboard(key.modifier, key.usage)
            Thread.sleep(TYPE_DELAY_MS)
            sendKeyboard(0, 0)
            Thread.sleep(TYPE_DELAY_MS)
        }
    }

    /** A key with modifiers, e.g. Ctrl+A. */
    fun combo(modifier: Int, usage: Int) = enqueue {
        sendKeyboard(modifier, usage)
        Thread.sleep(HOLD_MS)
        sendKeyboard(0, 0)
        Thread.sleep(TYPE_DELAY_MS)
    }

    private fun down(button: RemoteButton) {
        when (button.kind) {
            RemoteButton.Kind.KEYBOARD -> sendKeyboard(0, button.usage)
            RemoteButton.Kind.CONSUMER -> sendConsumer(button.usage)
        }
        lastDownAt = SystemClock.uptimeMillis()
    }

    private fun up(button: RemoteButton) {
        // A press shorter than a few ms can be missed, so hold for at least HOLD_MS.
        val wait = HOLD_MS - (SystemClock.uptimeMillis() - lastDownAt)
        if (wait > 0) Thread.sleep(wait)
        when (button.kind) {
            RemoteButton.Kind.KEYBOARD -> sendKeyboard(0, 0)
            RemoteButton.Kind.CONSUMER -> sendConsumer(0)
        }
    }

    private fun sendKeyboard(modifier: Int, usage: Int) {
        val report = ByteArray(HidDescriptor.KEYBOARD_REPORT_SIZE)
        report[0] = modifier.toByte()
        report[2] = usage.toByte()
        send(HidDescriptor.ID_KEYBOARD, report)
    }

    private fun sendConsumer(usage: Int) {
        val report = byteArrayOf((usage and 0xFF).toByte(), ((usage shr 8) and 0xFF).toByte())
        send(HidDescriptor.ID_CONSUMER, report)
    }

    private fun send(reportId: Int, data: ByteArray) {
        val h = hid ?: return
        val d = host ?: return
        runCatching { h.sendReport(d, reportId, data) }
            .onFailure { Log.w(TAG, "sendReport failed", it) }
    }

    private fun enqueue(block: () -> Unit) {
        sendExecutor.execute {
            runCatching(block).onFailure { Log.w(TAG, "send task failed", it) }
        }
    }

    // ---------------------------------------------------------------- Bluetooth on/off

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_ON -> start()
                BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> {
                    teardown()
                    update(Status.BLUETOOTH_OFF)
                }
            }
        }
    }

    private fun registerStateReceiver() {
        if (receiverRegistered) return
        // ACTION_STATE_CHANGED is a protected system broadcast. EXPORTED is needed so it
        // reaches us on all Android versions; no other app can send it.
        ContextCompat.registerReceiver(
            appContext,
            stateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
        receiverRegistered = true
    }

    private fun unregisterStateReceiver() {
        if (!receiverRegistered) return
        runCatching { appContext.unregisterReceiver(stateReceiver) }
        receiverRegistered = false
    }

    private fun update(status: Status, deviceName: String? = null, message: String? = null) {
        _state.value = State(status, deviceName, message)
    }

    private companion object {
        const val TAG = "BluetoothHidRemote"
        const val SDP_NAME = "TV Remote"
        const val SDP_DESCRIPTION = "Phone remote control"
        const val SDP_PROVIDER = "TvRemote"
        const val KEY_LAST_DEVICE = "last_device"

        const val HOLD_MS = 40L
        const val TYPE_DELAY_MS = 15L
        const val DEFAULT_GAP_MS = 120L
        const val MAX_TAPS = 50
    }
}
