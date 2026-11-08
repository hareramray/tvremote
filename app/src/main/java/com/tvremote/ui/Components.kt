package com.tvremote.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tvremote.hid.RemoteButton

/** What a remote key does: finger down, finger up, or a complete tap. */
interface KeyActions {
    fun down(button: RemoteButton)
    fun up(button: RemoteButton)
    fun tap(button: RemoteButton)
}

/** Buttons the current connection can't send. They are drawn dimmed and do nothing. */
val LocalUnsupportedButtons = compositionLocalOf { emptySet<RemoteButton>() }

/**
 * A remote button that behaves like a real one: the key goes down when the finger
 * touches it and up when the finger lifts, so holding it repeats on the TV.
 */
@Composable
fun RemoteKey(
    button: RemoteButton,
    actions: KeyActions,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val currentActions by rememberUpdatedState(actions)
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "keyScale")
    val pressedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f).compositeOver(containerColor)
    val supported = button !in LocalUnsupportedButtons.current

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (supported) 1f else 0.3f
            }
            .semantics {
                contentDescription = button.label
                role = Role.Button
                if (supported) {
                    onClick {
                        currentActions.tap(button)
                        true
                    }
                } else {
                    disabled()
                }
            }
            .pointerInput(button, supported) {
                if (!supported) return@pointerInput
                detectTapGestures(
                    onPress = {
                        pressed = true
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        currentActions.down(button)
                        try {
                            tryAwaitRelease()
                        } finally {
                            pressed = false
                            currentActions.up(button)
                        }
                    },
                )
            },
        shape = shape,
        color = if (pressed) pressedColor else containerColor,
        contentColor = contentColor,
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
fun IconKey(
    button: RemoteButton,
    icon: ImageVector,
    actions: KeyActions,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    RemoteKey(
        button = button,
        actions = actions,
        modifier = modifier.size(size),
        containerColor = containerColor,
        contentColor = contentColor,
    ) {
        Icon(icon, contentDescription = null)
    }
}

/** Round D-pad with OK in the middle. */
@Composable
fun DPad(size: Dp, actions: KeyActions) {
    val arrowSize = size * 0.3f
    val inset = size * 0.03f
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        ArrowKey(
            RemoteButton.UP, Icons.Filled.KeyboardArrowUp, actions,
            Modifier.align(Alignment.TopCenter).padding(top = inset).size(arrowSize),
        )
        ArrowKey(
            RemoteButton.DOWN, Icons.Filled.KeyboardArrowDown, actions,
            Modifier.align(Alignment.BottomCenter).padding(bottom = inset).size(arrowSize),
        )
        ArrowKey(
            RemoteButton.LEFT, Icons.AutoMirrored.Filled.KeyboardArrowLeft, actions,
            Modifier.align(Alignment.CenterStart).padding(start = inset).size(arrowSize),
        )
        ArrowKey(
            RemoteButton.RIGHT, Icons.AutoMirrored.Filled.KeyboardArrowRight, actions,
            Modifier.align(Alignment.CenterEnd).padding(end = inset).size(arrowSize),
        )
        RemoteKey(
            button = RemoteButton.OK,
            actions = actions,
            modifier = Modifier.align(Alignment.Center).size(size * 0.4f),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Text("OK", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ArrowKey(button: RemoteButton, icon: ImageVector, actions: KeyActions, modifier: Modifier) {
    RemoteKey(button, actions, modifier, containerColor = Color.Transparent) {
        Icon(icon, contentDescription = null, modifier = Modifier.fillMaxSize(0.6f))
    }
}
