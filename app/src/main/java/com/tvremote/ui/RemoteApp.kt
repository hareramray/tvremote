package com.tvremote.ui

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tvremote.ConnectionMode
import com.tvremote.RemoteViewModel
import com.tvremote.hid.BluetoothHidRemote
import com.tvremote.voice.VoiceController
import com.tvremote.wifi.WifiRemote

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteApp(vm: RemoteViewModel) {
    val context = LocalContext.current
    val mode by vm.mode.collectAsStateWithLifecycle()
    val btState by vm.bluetooth.state.collectAsStateWithLifecycle()
    val wifiState by vm.wifi.state.collectAsStateWithLifecycle()
    val pairing by vm.wifi.pairing.collectAsStateWithLifecycle()
    val scan by vm.wifi.scan.collectAsStateWithLifecycle()
    val apps by vm.wifi.apps.collectAsStateWithLifecycle()
    val unsupported by vm.unsupportedButtons.collectAsStateWithLifecycle()
    val voiceState by vm.voice.state.collectAsStateWithLifecycle()
    val feedback by vm.voiceFeedback.collectAsStateWithLifecycle()
    val okSendsEnter by vm.okSendsEnter.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    // ---- Bluetooth permission ("Nearby devices" on Android 12+), only needed in Bluetooth mode
    val btPermissions = remember { bluetoothPermissions() }
    var hasBtPermission by remember { mutableStateOf(context.hasPermissions(btPermissions)) }
    val btPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasBtPermission = context.hasPermissions(btPermissions)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasBtPermission = context.hasPermissions(btPermissions)
    }
    LaunchedEffect(mode, hasBtPermission) {
        if (mode == ConnectionMode.BLUETOOTH) {
            if (hasBtPermission) vm.startBluetooth() else btPermissionLauncher.launch(btPermissions)
        }
    }

    // ---- System dialogs
    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { vm.startBluetooth() }
    val discoverableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_CANCELED) {
            vm.showMessage("Phone is visible for 2 minutes. Now select it on the TV.")
        }
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.startVoice() else vm.showMessage("Microphone permission is needed for voice commands.")
    }

    val enableBluetooth: () -> Unit = {
        enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }
    val makeVisible: () -> Unit = {
        discoverableLauncher.launch(
            Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
        )
    }
    val onMic: () -> Unit = {
        when {
            voiceState is VoiceController.State.Listening -> vm.stopVoice()
            !vm.voice.isAvailable ->
                vm.showMessage("Speech recognition isn't available. Install or enable the Google app.")
            context.hasPermissions(arrayOf(Manifest.permission.RECORD_AUDIO)) -> vm.startVoice()
            else -> micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showConnect by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    // First run in Wi-Fi mode: go straight to pairing.
    LaunchedEffect(Unit) {
        if (vm.mode.value == ConnectionMode.WIFI && !vm.wifi.isPaired) showConnect = true
    }
    // Close the connect sheet once a TV connects (e.g. right after pairing).
    LaunchedEffect(btState.status, wifiState.status) {
        val connected = when (vm.mode.value) {
            ConnectionMode.WIFI -> wifiState.status == WifiRemote.Status.CONNECTED
            ConnectionMode.BLUETOOTH -> btState.status == BluetoothHidRemote.Status.CONNECTED
        }
        if (connected) showConnect = false
    }

    val connected = when (mode) {
        ConnectionMode.WIFI -> wifiState.status == WifiRemote.Status.CONNECTED
        ConnectionMode.BLUETOOTH -> btState.status == BluetoothHidRemote.Status.CONNECTED
    }
    val statusText = when (mode) {
        ConnectionMode.WIFI -> wifiTopLine(wifiState)
        ConnectionMode.BLUETOOTH -> bluetoothStatusLine(btState)
    }
    val connectIcon = when (mode) {
        ConnectionMode.WIFI -> when (wifiState.status) {
            WifiRemote.Status.NOT_PAIRED, WifiRemote.Status.UNREACHABLE, WifiRemote.Status.AUTH_FAILED -> Icons.Filled.WifiOff
            else -> Icons.Filled.Wifi
        }
        ConnectionMode.BLUETOOTH -> when (btState.status) {
            BluetoothHidRemote.Status.CONNECTED -> Icons.Filled.BluetoothConnected
            BluetoothHidRemote.Status.BLUETOOTH_OFF, BluetoothHidRemote.Status.UNSUPPORTED -> Icons.Filled.BluetoothDisabled
            else -> Icons.Filled.Bluetooth
        }
    }

    Scaffold(
        topBar = {
            RemoteTopBar(
                statusText = statusText,
                connected = connected,
                connectIcon = connectIcon,
                onConnect = { showConnect = true },
                onSettings = { showSettings = true },
            )
        },
        bottomBar = { BottomTabs(selected = tab, onSelect = { tab = it }) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (mode) {
                ConnectionMode.WIFI -> WifiBanner(
                    state = wifiState,
                    onPair = { showConnect = true },
                    onReconnect = vm.wifi::reconnect,
                )
                ConnectionMode.BLUETOOTH -> if (!hasBtPermission) {
                    PermissionBanner(
                        onAllow = { btPermissionLauncher.launch(btPermissions) },
                        onOpenSettings = { context.openAppSettings() },
                    )
                } else {
                    BluetoothBanner(
                        state = btState,
                        onConnect = { showConnect = true },
                        onEnableBluetooth = enableBluetooth,
                        onRetry = vm::retryBluetooth,
                    )
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                CompositionLocalProvider(LocalUnsupportedButtons provides unsupported) {
                    when (tab) {
                        0 -> ButtonsPanel(vm)
                        1 -> TouchpadPanel(vm)
                        2 -> KeyboardPanel(
                            actions = vm,
                            onType = vm::typeText,
                            onDelete = vm::deleteChars,
                            onClearField = vm::clearTvField,
                            canDelete = mode == ConnectionMode.BLUETOOTH,
                        )
                        else -> AppsPanel(
                            mode = mode,
                            paired = wifiState.status != WifiRemote.Status.NOT_PAIRED,
                            apps = apps,
                            onLaunch = { vm.launchApp(it.packageName) },
                            onRefresh = vm.wifi::refreshApps,
                            onUseWifi = { vm.setMode(ConnectionMode.WIFI) },
                            onPair = { showConnect = true },
                        )
                    }
                }
            }
            VoiceBar(
                state = voiceState,
                feedback = feedback,
                onMic = onMic,
                onSendText = vm::sendHeardText,
            )
        }
    }

    if (showConnect) {
        ConnectSheet(
            mode = mode,
            onModeChange = vm::setMode,
            onDismiss = {
                vm.wifi.cancelPairing()
                showConnect = false
            },
        ) {
            when (mode) {
                ConnectionMode.WIFI -> WifiConnectSection(
                    state = wifiState,
                    paired = if (wifiState.status == WifiRemote.Status.NOT_PAIRED) null else vm.wifi.pairedTv,
                    pairing = pairing,
                    scan = scan,
                    onScan = vm.wifi::scan,
                    onPick = vm.wifi::beginPairing,
                    onSubmitPin = vm.wifi::submitPin,
                    onCancelPairing = vm.wifi::cancelPairing,
                    onReconnect = vm.wifi::reconnect,
                    onForget = vm.wifi::forget,
                )
                ConnectionMode.BLUETOOTH -> if (!hasBtPermission) {
                    Text(
                        "Bluetooth mode needs the “Nearby devices” permission.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = { btPermissionLauncher.launch(btPermissions) }) { Text("Allow") }
                } else {
                    BluetoothConnectSection(
                        state = btState,
                        phoneName = vm.bluetooth.phoneName,
                        loadDevices = vm.bluetooth::pairedDevices,
                        onMakeVisible = makeVisible,
                        onConnect = vm.bluetooth::connect,
                        onDisconnect = vm.bluetooth::disconnect,
                    )
                }
            }
        }
    }
    if (showSettings) {
        SettingsDialog(
            mode = mode,
            okSendsEnter = okSendsEnter,
            onOkSendsEnterChange = vm::setOkSendsEnter,
            onDismiss = { showSettings = false },
        )
    }
}

// ---------------------------------------------------------------- top & bottom bars

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteTopBar(
    statusText: String,
    connected: Boolean,
    connectIcon: ImageVector,
    onConnect: () -> Unit,
    onSettings: () -> Unit,
) {
    TopAppBar(
        title = {
            Column {
                Text("TV Remote", style = MaterialTheme.typography.titleLarge)
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        actions = {
            IconButton(onClick = onConnect) {
                Icon(
                    connectIcon,
                    contentDescription = "Connect to TV",
                    tint = if (connected) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                )
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings and help")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

private fun wifiTopLine(state: WifiRemote.State): String = when (state.status) {
    WifiRemote.Status.CONNECTED -> "Wi-Fi • ${state.tvName ?: "TV"}"
    else -> "Wi-Fi • ${wifiStatusLine(state)}"
}

private fun bluetoothStatusLine(state: BluetoothHidRemote.State): String = when (state.status) {
    BluetoothHidRemote.Status.CONNECTED -> "Bluetooth • ${state.deviceName ?: "TV"}"
    BluetoothHidRemote.Status.CONNECTING -> "Bluetooth • Connecting…"
    BluetoothHidRemote.Status.READY -> "Bluetooth • Not connected"
    BluetoothHidRemote.Status.STARTING -> "Bluetooth • Starting…"
    BluetoothHidRemote.Status.BLUETOOTH_OFF -> "Bluetooth is off"
    BluetoothHidRemote.Status.UNSUPPORTED -> "Bluetooth • Not supported"
    BluetoothHidRemote.Status.ERROR -> "Bluetooth remote stopped"
}

@Composable
private fun BottomTabs(selected: Int, onSelect: (Int) -> Unit) {
    val tabs = listOf(
        "Remote" to Icons.Filled.Gamepad,
        "Touchpad" to Icons.Filled.TouchApp,
        "Keyboard" to Icons.Filled.Keyboard,
        "Apps" to Icons.Filled.Apps,
    )
    NavigationBar {
        tabs.forEachIndexed { index, (label, icon) ->
            NavigationBarItem(
                selected = selected == index,
                onClick = { onSelect(index) },
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(label) },
            )
        }
    }
}

// ---------------------------------------------------------------- banners

@Composable
private fun WifiBanner(state: WifiRemote.State, onPair: () -> Unit, onReconnect: () -> Unit) {
    val name = state.tvName ?: "the TV"
    val (title, detail) = when (state.status) {
        WifiRemote.Status.CONNECTED -> return
        WifiRemote.Status.NOT_PAIRED -> "Not paired" to "Pair with your TV over Wi-Fi. You'll type a PIN shown on the TV."
        WifiRemote.Status.CHECKING -> "Connecting…" to "Reaching $name"
        WifiRemote.Status.ASLEEP -> "TV asleep or off" to (state.message ?: "Press any button to wake it.")
        WifiRemote.Status.WAKING -> "Waking $name…" to "This can take a few seconds."
        WifiRemote.Status.UNREACHABLE -> "Can't reach the TV" to state.message.orEmpty()
        WifiRemote.Status.AUTH_FAILED -> "Pair again" to state.message.orEmpty()
    }
    Banner(title = title, detail = detail) {
        when (state.status) {
            WifiRemote.Status.NOT_PAIRED, WifiRemote.Status.AUTH_FAILED ->
                FilledTonalButton(onClick = onPair) { Text("Pair") }
            WifiRemote.Status.ASLEEP -> FilledTonalButton(onClick = onReconnect) { Text("Wake") }
            WifiRemote.Status.UNREACHABLE -> FilledTonalButton(onClick = onReconnect) { Text("Retry") }
            else -> Unit
        }
    }
}

@Composable
private fun PermissionBanner(onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    Banner(
        title = "Allow “Nearby devices”",
        detail = "Needed so this phone can act as a Bluetooth remote for your TV.",
    ) {
        Column(horizontalAlignment = Alignment.End) {
            FilledTonalButton(onClick = onAllow) { Text("Allow") }
            TextButton(onClick = onOpenSettings) { Text("App settings") }
        }
    }
}

@Composable
private fun BluetoothBanner(
    state: BluetoothHidRemote.State,
    onConnect: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onRetry: () -> Unit,
) {
    val (title, detail) = when (state.status) {
        BluetoothHidRemote.Status.STARTING -> "Starting…" to "Setting up the Bluetooth remote."
        BluetoothHidRemote.Status.UNSUPPORTED -> "Not supported on this phone" to state.message.orEmpty()
        BluetoothHidRemote.Status.ERROR -> "Bluetooth remote stopped" to state.message.orEmpty()
        BluetoothHidRemote.Status.BLUETOOTH_OFF -> "Bluetooth is off" to "Turn on Bluetooth to control the TV."
        BluetoothHidRemote.Status.READY -> "Not connected" to
            (state.message ?: "Pair with your TV, or connect to one you paired before.")
        BluetoothHidRemote.Status.CONNECTING -> "Connecting…" to "Reaching ${state.deviceName ?: "your TV"}"
        BluetoothHidRemote.Status.CONNECTED -> return
    }
    Banner(title = title, detail = detail) {
        when (state.status) {
            BluetoothHidRemote.Status.BLUETOOTH_OFF -> FilledTonalButton(onClick = onEnableBluetooth) { Text("Turn on") }
            BluetoothHidRemote.Status.ERROR -> FilledTonalButton(onClick = onRetry) { Text("Retry") }
            BluetoothHidRemote.Status.READY, BluetoothHidRemote.Status.CONNECTING ->
                FilledTonalButton(onClick = onConnect) { Text("Connect") }
            else -> Unit
        }
    }
}

@Composable
private fun Banner(title: String, detail: String, action: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (detail.isNotEmpty()) {
                    Text(detail, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.width(8.dp))
            action()
        }
    }
}

// ---------------------------------------------------------------- connect sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectSheet(
    mode: ConnectionMode,
    onModeChange: (ConnectionMode) -> Unit,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Connect to your TV", style = MaterialTheme.typography.headlineSmall)
            val modes = listOf(
                Triple(ConnectionMode.WIFI, "Wi-Fi", Icons.Filled.Wifi),
                Triple(ConnectionMode.BLUETOOTH, "Bluetooth", Icons.Filled.Bluetooth),
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, (value, label, icon) ->
                    SegmentedButton(
                        selected = mode == value,
                        onClick = { onModeChange(value) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    ) {
                        Text(label)
                    }
                }
            }
            Text(
                when (mode) {
                    ConnectionMode.WIFI -> "Wi-Fi: pair once with a PIN. Opens apps, works from any room, can wake the TV."
                    ConnectionMode.BLUETOOTH -> "Bluetooth: works without Wi-Fi. Pair the phone like a keyboard in the TV's settings."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun BluetoothConnectSection(
    state: BluetoothHidRemote.State,
    phoneName: String?,
    loadDevices: () -> List<BluetoothHidRemote.PairedDevice>,
    onMakeVisible: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
) {
    val devices = remember(state) { loadDevices() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.status == BluetoothHidRemote.Status.CONNECTED) {
            OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
                Text("Disconnect from ${state.deviceName ?: "TV"}")
            }
        }

        SectionTitle("Paired devices")
        if (devices.isEmpty()) {
            Text(
                "Nothing paired yet. Follow the steps below to pair your TV.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "Pick your TV. Other devices (earbuds, watches) are listed too.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            devices.forEach { device ->
                DeviceRow(
                    device = device,
                    connecting = state.status == BluetoothHidRemote.Status.CONNECTING && state.deviceName == device.name,
                    onConnect = { onConnect(device.address) },
                )
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        SectionTitle("Pair a new TV (one time)")
        PairStep(1, "Tap the button below so the TV can find this phone.")
        Button(onClick = onMakeVisible, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Bluetooth, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Make phone visible")
        }
        PairStep(
            2,
            "On the TV open Settings → Controllers & Bluetooth Devices → " +
                "Other Bluetooth Devices → Add Bluetooth Devices.",
        )
        PairStep(3, "Choose “${phoneName ?: "this phone"}” in the TV's list and confirm the code on both screens.")
        PairStep(4, "That's it. Next time the app reconnects by itself.")

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
        ) {
            Row(Modifier.padding(12.dp)) {
                Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Don't use “Amazon Fire TV Remotes → Add New Remote”. That screen only finds " +
                        "Amazon remotes. Use “Other Bluetooth Devices”.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun PairStep(number: Int, text: String) {
    Row {
        Text(
            "$number.",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(24.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DeviceRow(device: BluetoothHidRemote.PairedDevice, connecting: Boolean, onConnect: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(enabled = !device.isConnected, onClick = onConnect),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        leadingContent = { Icon(Icons.Filled.Tv, contentDescription = null) },
        headlineContent = { Text(device.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                when {
                    device.isConnected -> "Connected"
                    connecting -> "Connecting…"
                    device.isLastUsed -> "Last used"
                    else -> device.address
                }
            )
        },
        trailingContent = {
            if (device.isConnected) {
                Icon(Icons.Filled.Check, contentDescription = "Connected", tint = MaterialTheme.colorScheme.primary)
            } else {
                TextButton(onClick = onConnect) { Text("Connect") }
            }
        },
    )
}

// ---------------------------------------------------------------- settings & help

private val VOICE_HELP = listOf(
    "up / down / left / right" to "Move",
    "down 3 times" to "Move 3 steps",
    "ok, select" to "Select",
    "back, go home, menu" to "Navigate",
    "play, pause, rewind, fast forward" to "Playback",
    "volume up, volume down 5, mute" to "Volume",
    "open YouTube, open Hotstar" to "Opens the app (Wi-Fi mode)",
    "search Avengers" to "Types it into the open search box",
    "type hello" to "Types into the focused text box",
    "down 2 then ok" to "Chain commands",
    "turn on the TV" to "Wakes the TV (Wi-Fi mode)",
    "power off" to "Turns the TV off",
)

@Composable
private fun SettingsDialog(
    mode: ConnectionMode,
    okSendsEnter: Boolean,
    onOkSendsEnterChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Settings & help") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (mode == ConnectionMode.BLUETOOTH) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("OK button sends Enter", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Turn on if OK does nothing in some app (Bluetooth mode).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = okSendsEnter, onCheckedChange = onOkSendsEnterChange)
                    }
                    HorizontalDivider()
                }
                Text("Things you can say", style = MaterialTheme.typography.titleSmall)
                VOICE_HELP.forEach { (say, does) ->
                    Column {
                        Text("“$say”", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            does,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider()
                Text(
                    "Your phone's volume buttons also control the TV volume while connected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

// ---------------------------------------------------------------- permission helpers

private fun bluetoothPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
    } else {
        emptyArray()
    }

private fun Context.hasPermissions(permissions: Array<String>): Boolean = permissions.all {
    ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
}

private fun Context.openAppSettings() {
    startActivity(
        Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
