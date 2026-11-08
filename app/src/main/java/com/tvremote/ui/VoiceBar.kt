package com.tvremote.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tvremote.RemoteViewModel
import com.tvremote.voice.VoiceController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceBar(
    state: VoiceController.State,
    feedback: RemoteViewModel.VoiceFeedback?,
    onMic: () -> Unit,
    onSendText: (text: String, submit: Boolean) -> Unit,
) {
    val listening = state is VoiceController.State.Listening
    val level = (state as? VoiceController.State.Listening)?.level ?: 0f
    val ring by animateFloatAsState(if (listening) 1.05f + level * 0.4f else 1f, label = "micLevel")

    val (title, body) = when {
        state is VoiceController.State.Listening ->
            "Listening…" to state.partial.ifEmpty { "Say a command" }
        state is VoiceController.State.Error ->
            "Voice" to state.message
        feedback != null ->
            "“${feedback.heard}”" to (feedback.action?.let { "→ $it" }
                ?: "Not a command. Type it or search it on the TV?")
        else ->
            "Voice" to "Tap the mic: “volume up”, “go home”, “search Avengers”"
    }
    val showTextChoices = state is VoiceController.State.Idle && feedback != null && feedback.action == null

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp)) {
                    if (listening) {
                        Box(
                            Modifier
                                .size(56.dp)
                                .graphicsLayer {
                                    scaleX = ring
                                    scaleY = ring
                                }
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.25f))
                        )
                    }
                    FilledIconButton(
                        onClick = onMic,
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            contentColor = if (listening) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(
                            if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = if (listening) "Stop listening" else "Speak a command",
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (showTextChoices) {
                Row(
                    modifier = Modifier.padding(start = 68.dp, top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(
                        onClick = { onSendText(feedback.heard, false) },
                        label = { Text("Type it") },
                        leadingIcon = { Icon(Icons.Filled.Keyboard, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                    AssistChip(
                        onClick = { onSendText(feedback.heard, true) },
                        label = { Text("Search it") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }
        }
    }
}
