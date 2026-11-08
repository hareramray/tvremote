package com.tvremote.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tvremote.hid.RemoteButton
import kotlin.math.abs

// ---------------------------------------------------------------- Remote buttons

@Composable
fun ButtonsPanel(actions: KeyActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconKey(
                RemoteButton.POWER, Icons.Filled.PowerSettingsNew, actions,
                size = 48.dp, contentColor = MaterialTheme.colorScheme.error,
            )
            IconKey(RemoteButton.MUTE, Icons.AutoMirrored.Filled.VolumeOff, actions, size = 48.dp)
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            DPad(size = minOf(maxWidth, maxHeight, 300.dp), actions = actions)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IconKey(RemoteButton.BACK, Icons.AutoMirrored.Filled.ArrowBack, actions)
            IconKey(RemoteButton.HOME, Icons.Filled.Home, actions)
            IconKey(RemoteButton.MENU, Icons.Filled.Menu, actions)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IconKey(RemoteButton.REWIND, Icons.Filled.FastRewind, actions)
            RemoteKey(RemoteButton.PLAY_PAUSE, actions, Modifier.size(56.dp)) {
                Row {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            IconKey(RemoteButton.FAST_FORWARD, Icons.Filled.FastForward, actions)
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconKey(RemoteButton.PREVIOUS, Icons.Filled.SkipPrevious, actions, size = 48.dp)
            VolumeRocker(actions)
            IconKey(RemoteButton.NEXT, Icons.Filled.SkipNext, actions, size = 48.dp)
        }
    }
}

@Composable
private fun VolumeRocker(actions: KeyActions) {
    Row(
        modifier = Modifier
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteKey(
            RemoteButton.VOLUME_DOWN, actions,
            Modifier.width(76.dp).fillMaxHeight(), shape = RectangleShape,
        ) { Icon(Icons.AutoMirrored.Filled.VolumeDown, contentDescription = null) }
        Box(
            Modifier
                .width(1.dp)
                .height(24.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        RemoteKey(
            RemoteButton.VOLUME_UP, actions,
            Modifier.width(76.dp).fillMaxHeight(), shape = RectangleShape,
        ) { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null) }
    }
}

// ---------------------------------------------------------------- Touchpad

@Composable
fun TouchpadPanel(actions: KeyActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TouchSurface(
            actions,
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IconKey(RemoteButton.BACK, Icons.AutoMirrored.Filled.ArrowBack, actions, size = 52.dp)
            IconKey(RemoteButton.HOME, Icons.Filled.Home, actions, size = 52.dp)
            RemoteKey(RemoteButton.PLAY_PAUSE, actions, Modifier.size(52.dp)) {
                Row {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            IconKey(RemoteButton.VOLUME_DOWN, Icons.AutoMirrored.Filled.VolumeDown, actions, size = 52.dp)
            IconKey(RemoteButton.VOLUME_UP, Icons.AutoMirrored.Filled.VolumeUp, actions, size = 52.dp)
        }
    }
}

/** Swipe = arrow keys (one step per ~48dp of finger travel), tap = OK. */
@Composable
private fun TouchSurface(actions: KeyActions, modifier: Modifier) {
    val view = LocalView.current
    val currentActions by rememberUpdatedState(actions)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) {
                val step = 48.dp.toPx()
                var acc = Offset.Zero
                detectDragGestures(onDragStart = { acc = Offset.Zero }) { change, drag ->
                    change.consume()
                    acc += drag
                    while (true) {
                        val ax = abs(acc.x)
                        val ay = abs(acc.y)
                        if (ax < step && ay < step) break
                        if (ax >= ay) {
                            val right = acc.x > 0
                            currentActions.tap(if (right) RemoteButton.RIGHT else RemoteButton.LEFT)
                            val dx = if (right) step else -step
                            acc = Offset(acc.x - dx, 0f)
                        } else {
                            val down = acc.y > 0
                            currentActions.tap(if (down) RemoteButton.DOWN else RemoteButton.UP)
                            val dy = if (down) step else -step
                            acc = Offset(0f, acc.y - dy)
                        }
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    currentActions.tap(RemoteButton.OK)
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.TouchApp,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Swipe to move  •  Tap to select",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ---------------------------------------------------------------- Keyboard

@Composable
fun KeyboardPanel(
    actions: KeyActions,
    onType: (String) -> Unit,
    onDelete: (Int) -> Unit,
    onClearField: () -> Unit,
    canDelete: Boolean,
) {
    var liveSetting by rememberSaveable { mutableStateOf(true) }
    // Live typing mirrors deletions too, so it needs a connection that can delete (Bluetooth).
    val live = liveSetting && canDelete
    var text by rememberSaveable { mutableStateOf("") }

    val submit = {
        if (!live) onType(text)
        if (canDelete) actions.tap(RemoteButton.ENTER) // over Wi-Fi the TV searches as you type
        text = ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Select a search box or text field on the TV, then type here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { new ->
                if (live) {
                    // Mirror the edit on the TV: delete what changed, type what's new.
                    val common = text.commonPrefixWith(new).length
                    onDelete(text.length - common)
                    onType(new.substring(common))
                }
                text = new
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (live) "Types on the TV as you write" else "Text to send") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submit() }),
        )
        if (canDelete) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = liveSetting, onCheckedChange = { liveSetting = it })
                Spacer(Modifier.width(12.dp))
                Text("Live typing", style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (!live) {
            Button(
                onClick = {
                    onType(text)
                    text = ""
                },
                enabled = text.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Send text")
            }
        }
        if (!canDelete) {
            Text(
                "Over Wi-Fi the app can type but can't delete. To fix a mistake, use the TV's " +
                    "on-screen keyboard or switch to Bluetooth mode.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            BluetoothTextTools(actions, onClear = {
                onClearField()
                text = ""
            })
        }
    }
}

/** Enter / Delete / Clear, which need Bluetooth (a real keyboard). */
@Composable
private fun BluetoothTextTools(actions: KeyActions, onClear: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(
                onClick = { actions.tap(RemoteButton.ENTER) },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardReturn, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Enter")
            }
            RemoteKey(
                button = RemoteButton.BACKSPACE,
                actions = actions,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete on TV")
                }
            }
        }
        OutlinedButton(
            onClick = onClear,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Clear, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Clear TV text field")
        }
        Text(
            "Hold “Delete on TV” to erase quickly. English letters, numbers and symbols only.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
