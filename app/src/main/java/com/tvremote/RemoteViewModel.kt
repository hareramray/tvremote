package com.tvremote

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tvremote.hid.BluetoothHidRemote
import com.tvremote.hid.HidKeys
import com.tvremote.hid.RemoteButton
import com.tvremote.ui.KeyActions
import com.tvremote.voice.VoiceCommandParser
import com.tvremote.voice.VoiceController
import com.tvremote.voice.VoiceStep
import com.tvremote.wifi.WifiRemote
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class ConnectionMode { WIFI, BLUETOOTH }

class RemoteViewModel(app: Application) : AndroidViewModel(app), KeyActions {

    /** What the voice bar shows after a phrase: what was heard and what was done (null = not a command). */
    data class VoiceFeedback(val heard: String, val action: String?)

    val bluetooth = BluetoothHidRemote(app)
    val wifi = WifiRemote(app)
    val voice = VoiceController(app)

    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(
        runCatching { ConnectionMode.valueOf(prefs.getString(KEY_MODE, null) ?: "") }.getOrDefault(ConnectionMode.WIFI)
    )
    val mode: StateFlow<ConnectionMode> = _mode.asStateFlow()

    private val _okSendsEnter = MutableStateFlow(prefs.getBoolean(KEY_OK_SENDS_ENTER, false))
    val okSendsEnter: StateFlow<Boolean> = _okSendsEnter.asStateFlow()

    /** Buttons the current connection can't do; the UI dims them. */
    val unsupportedButtons: StateFlow<Set<RemoteButton>> =
        combine(_mode, wifi.capabilities) { mode, _ ->
            if (mode == ConnectionMode.WIFI) RemoteButton.entries.filterNot { wifi.isSupported(it) }.toSet() else emptySet()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _voiceFeedback = MutableStateFlow<VoiceFeedback?>(null)
    val voiceFeedback: StateFlow<VoiceFeedback?> = _voiceFeedback.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    private var lastHintAt = 0L

    init {
        voice.onResults = ::handleVoiceResults
        if (_mode.value == ConnectionMode.WIFI) wifi.start()
    }

    /** True when button presses can be sent (Wi-Fi: paired, and the TV is woken on demand). */
    val isReady: Boolean
        get() = when (_mode.value) {
            ConnectionMode.WIFI -> wifi.isPaired
            ConnectionMode.BLUETOOTH -> bluetooth.isConnected
        }

    fun setMode(newMode: ConnectionMode) {
        if (newMode == _mode.value) return
        _mode.value = newMode
        prefs.edit().putString(KEY_MODE, newMode.name).apply()
        when (newMode) {
            ConnectionMode.WIFI -> {
                bluetooth.stop()
                wifi.start()
            }
            // Bluetooth is started by the UI once the "Nearby devices" permission is granted.
            ConnectionMode.BLUETOOTH -> wifi.stop()
        }
    }

    fun startBluetooth() {
        if (_mode.value == ConnectionMode.BLUETOOTH) bluetooth.start()
    }

    fun retryBluetooth() = bluetooth.restart()

    fun setOkSendsEnter(value: Boolean) {
        _okSendsEnter.value = value
        prefs.edit().putBoolean(KEY_OK_SENDS_ENTER, value).apply()
    }

    fun showMessage(text: String) {
        _messages.tryEmit(text)
    }

    // ------------------------------------------------------------ buttons

    override fun down(button: RemoteButton) {
        if (!ensureReady()) return
        when (_mode.value) {
            ConnectionMode.WIFI -> wifi.press(button)
            ConnectionMode.BLUETOOTH -> bluetooth.press(resolve(button))
        }
    }

    override fun up(button: RemoteButton) {
        when (_mode.value) {
            ConnectionMode.WIFI -> wifi.release(button)
            ConnectionMode.BLUETOOTH -> bluetooth.release(resolve(button))
        }
    }

    override fun tap(button: RemoteButton) = tap(button, 1, 150)

    private fun tap(button: RemoteButton, times: Int, gapMs: Long) {
        if (!ensureReady()) return
        when (_mode.value) {
            ConnectionMode.WIFI -> wifi.tap(button, times, gapMs)
            ConnectionMode.BLUETOOTH -> bluetooth.tap(resolve(button), times, gapMs)
        }
    }

    // ------------------------------------------------------------ keyboard

    /** Wi-Fi can type but has no known way to delete, so the keyboard screen adapts. */
    val canDeleteText: Boolean get() = _mode.value == ConnectionMode.BLUETOOTH

    fun typeText(text: String) {
        if (text.isEmpty() || !ensureReady()) return
        when (_mode.value) {
            ConnectionMode.WIFI -> wifi.typeText(text)
            ConnectionMode.BLUETOOTH -> bluetooth.typeText(text)
        }
    }

    fun deleteChars(count: Int) {
        if (count <= 0 || !canDeleteText || !ensureReady()) return
        bluetooth.tap(RemoteButton.BACKSPACE, count, gapMs = 15)
    }

    /** Select all (Ctrl+A) then delete, which clears most Android text fields. Bluetooth only. */
    fun clearTvField() {
        if (!canDeleteText || !ensureReady()) return
        bluetooth.combo(HidKeys.MOD_LCTRL, HidKeys.KEY_A)
        bluetooth.tap(RemoteButton.BACKSPACE)
    }

    // ------------------------------------------------------------ apps (Wi-Fi)

    fun launchApp(packageName: String) {
        if (_mode.value != ConnectionMode.WIFI || !ensureReady()) return
        wifi.launchApp(packageName)
    }

    // ------------------------------------------------------------ voice

    fun startVoice() {
        _voiceFeedback.value = null
        voice.start()
    }

    fun stopVoice() = voice.stop()

    /** Used by the "Type it" / "Search it" chips when a phrase wasn't a command. */
    fun sendHeardText(text: String, submit: Boolean) {
        val step = VoiceStep.Type(text, submit)
        _voiceFeedback.value = VoiceFeedback(text, step.description)
        executeSteps(listOf(step))
    }

    private fun handleVoiceResults(results: List<String>) {
        // Use the first guess that is a command; otherwise show the best guess as "not a command".
        val (heard, steps) = results.firstNotNullOfOrNull { guess ->
            VoiceCommandParser.parse(guess)?.let { guess to it }
        } ?: (results.first() to null)

        if (steps == null) {
            _voiceFeedback.value = VoiceFeedback(heard, null)
            return
        }
        val described = steps.joinToString(", then ") { step ->
            if (step is VoiceStep.OpenApp && _mode.value == ConnectionMode.WIFI) {
                wifi.findApp(step.appName)?.let { "Open ${it.name}" } ?: step.description
            } else {
                step.description
            }
        }
        _voiceFeedback.value = VoiceFeedback(heard, described)
        executeSteps(steps)
    }

    private fun executeSteps(steps: List<VoiceStep>) {
        if (!ensureReady()) return
        val wifiMode = _mode.value == ConnectionMode.WIFI
        for (step in steps) {
            when (step) {
                is VoiceStep.Press -> tap(step.button, step.times, if (step.button.isVolume) 90 else 180)
                is VoiceStep.Type -> {
                    typeText(step.text)
                    // Over Wi-Fi the TV shows search results as you type; "Enter" has no equivalent.
                    if (step.submit && !wifiMode) bluetooth.tap(RemoteButton.ENTER)
                }
                is VoiceStep.OpenApp -> {
                    if (!wifiMode) {
                        showMessage("Opening apps by name works in Wi-Fi mode.")
                    } else {
                        val app = wifi.findApp(step.appName)
                        if (app != null) wifi.launchApp(app.packageName)
                        else showMessage("No app called “${step.appName}” on the TV.")
                    }
                }
                VoiceStep.PowerOn -> {
                    if (wifiMode) wifi.powerOn()
                    else showMessage("Turning the TV on by voice works in Wi-Fi mode.")
                }
            }
        }
    }

    // ------------------------------------------------------------ helpers

    private fun resolve(button: RemoteButton): RemoteButton =
        if (button == RemoteButton.OK && _okSendsEnter.value) RemoteButton.ENTER else button

    private fun ensureReady(): Boolean {
        if (isReady) return true
        val now = SystemClock.uptimeMillis()
        if (now - lastHintAt > 2500) {
            lastHintAt = now
            showMessage(
                when (_mode.value) {
                    ConnectionMode.WIFI -> "Not paired with a TV yet. Tap the Wi-Fi icon to pair."
                    ConnectionMode.BLUETOOTH -> "Not connected to the TV. Tap the Bluetooth icon to connect."
                }
            )
        }
        return false
    }

    override fun onCleared() {
        voice.release()
        bluetooth.stop()
        wifi.close()
    }

    private companion object {
        const val KEY_OK_SENDS_ENTER = "ok_sends_enter"
        const val KEY_MODE = "connection_mode"
    }
}
