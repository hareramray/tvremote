package com.tvremote.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvremote.wifi.FoundTv
import com.tvremote.wifi.WifiRemote

private val IPV4 = Regex("^(25[0-5]|2[0-4]\\d|1?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}$")

@Composable
fun WifiConnectSection(
    state: WifiRemote.State,
    paired: WifiRemote.PairedTv?,
    pairing: WifiRemote.Pairing,
    scan: WifiRemote.Scan,
    onScan: () -> Unit,
    onPick: (FoundTv) -> Unit,
    onSubmitPin: (String) -> Unit,
    onCancelPairing: () -> Unit,
    onReconnect: () -> Unit,
    onForget: () -> Unit,
) {
    LaunchedEffect(Unit) { onScan() }

    if (pairing != WifiRemote.Pairing.Idle) {
        PairingCard(pairing, onSubmitPin, onCancelPairing, onRetry = onPick)
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (paired != null) {
            PairedTvCard(state, paired, onReconnect, onForget)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "TVs on this Wi-Fi",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            if (scan.scanning) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onScan) { Icon(Icons.Filled.Refresh, contentDescription = "Search again") }
            }
        }

        val others = scan.results
        when {
            others.isEmpty() && scan.scanning -> Text(
                "Looking for TVs…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            others.isEmpty() && scan.done -> Text(
                "No TVs found. Make sure the TV is on and that this phone is on the same Wi-Fi (not mobile data or a guest network).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> others.forEach { tv ->
                val isPaired = paired != null && (tv.uuid != null && tv.uuid == paired.uuid || tv.ip == paired.ip)
                FoundTvRow(tv, isPaired, onPick = { onPick(tv) })
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        ManualIpEntry(onPick)

        Text(
            "Pairing is one-time: the TV shows a 4-digit PIN, you type it here. " +
                "Phone and TV must be on the same Wi-Fi.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PairedTvCard(
    state: WifiRemote.State,
    tv: WifiRemote.PairedTv,
    onReconnect: () -> Unit,
    onForget: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Tv, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(tv.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${wifiStatusLine(state)} • ${tv.ip}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onReconnect, enabled = state.status != WifiRemote.Status.WAKING && state.status != WifiRemote.Status.CHECKING) {
                    Text(if (state.status == WifiRemote.Status.CONNECTED) "Check connection" else "Wake & reconnect")
                }
                OutlinedButton(onClick = onForget) { Text("Forget") }
            }
        }
    }
}

@Composable
private fun FoundTvRow(tv: FoundTv, isPaired: Boolean, onPick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onPick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        leadingContent = { Icon(Icons.Filled.Tv, contentDescription = null) },
        headlineContent = { Text(tv.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(listOfNotNull(tv.model, tv.ip).joinToString(" • ")) },
        trailingContent = {
            TextButton(onClick = onPick) { Text(if (isPaired) "Pair again" else "Pair") }
        },
    )
}

@Composable
private fun ManualIpEntry(onPick: (FoundTv) -> Unit) {
    var ip by rememberSaveable { mutableStateOf("") }
    val valid = IPV4.matches(ip.trim())
    val submit = { if (valid) onPick(FoundTv(ip = ip.trim(), name = "TV at ${ip.trim()}")) }
    Text("Or enter the TV's IP address", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = ip,
            onValueChange = { ip = it.filter { c -> c.isDigit() || c == '.' }.take(15) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("192.168.1.50") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() }),
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = submit, enabled = valid) { Text("Pair") }
    }
    Text(
        "On the TV: Settings → My Fire TV → About → Network shows the IP address.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PairingCard(
    pairing: WifiRemote.Pairing,
    onSubmitPin: (String) -> Unit,
    onCancel: () -> Unit,
    onRetry: (FoundTv) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (pairing) {
                is WifiRemote.Pairing.Requesting -> {
                    Text("Asking ${pairing.tv.name} to show a PIN…", textAlign = TextAlign.Center)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
                is WifiRemote.Pairing.EnterPin -> PinEntry(pairing, onSubmitPin, onCancel)
                is WifiRemote.Pairing.Verifying -> {
                    Text("Checking the PIN…", textAlign = TextAlign.Center)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                is WifiRemote.Pairing.Failed -> {
                    Text(pairing.message, textAlign = TextAlign.Center)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onRetry(pairing.tv) }) { Text("Try again") }
                        OutlinedButton(onClick = onCancel) { Text("Cancel") }
                    }
                }
                WifiRemote.Pairing.Idle -> Unit
            }
        }
    }
}

@Composable
private fun PinEntry(pairing: WifiRemote.Pairing.EnterPin, onSubmitPin: (String) -> Unit, onCancel: () -> Unit) {
    var pin by remember(pairing) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(pairing) { runCatching { focus.requestFocus() } }

    Text("Enter the PIN shown on", style = MaterialTheme.typography.bodyMedium)
    Text(pairing.tv.name, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    OutlinedTextField(
        value = pin,
        onValueChange = { value ->
            pin = value.filter { it.isDigit() }.take(4)
            if (pin.length == 4) onSubmitPin(pin)
        },
        modifier = Modifier
            .width(180.dp)
            .focusRequester(focus),
        singleLine = true,
        textStyle = TextStyle(fontSize = 32.sp, letterSpacing = 12.sp, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (pin.length == 4) onSubmitPin(pin) }),
        isError = pairing.error != null,
    )
    pairing.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
    Text(
        "The PIN is valid for 5 minutes.",
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
    )
    TextButton(onClick = onCancel) { Text("Cancel") }
}

fun wifiStatusLine(state: WifiRemote.State): String = when (state.status) {
    WifiRemote.Status.NOT_PAIRED -> "Not paired"
    WifiRemote.Status.CHECKING -> "Connecting…"
    WifiRemote.Status.CONNECTED -> "Connected"
    WifiRemote.Status.ASLEEP -> "TV asleep"
    WifiRemote.Status.WAKING -> "Waking TV…"
    WifiRemote.Status.UNREACHABLE -> "Not reachable"
    WifiRemote.Status.AUTH_FAILED -> "Needs pairing again"
}
