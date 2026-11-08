package com.tvremote.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tvremote.ConnectionMode
import com.tvremote.wifi.TvApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

@Composable
fun AppsPanel(
    mode: ConnectionMode,
    paired: Boolean,
    apps: List<TvApp>,
    onLaunch: (TvApp) -> Unit,
    onRefresh: () -> Unit,
    onUseWifi: () -> Unit,
    onPair: () -> Unit,
) {
    when {
        mode != ConnectionMode.WIFI -> Message(
            title = "Open apps over Wi-Fi",
            body = "Launching TV apps from the phone works in Wi-Fi mode.",
            action = "Switch to Wi-Fi",
            onAction = onUseWifi,
        )
        !paired -> Message(
            title = "Pair with your TV",
            body = "Pair over Wi-Fi to see and open the TV's apps.",
            action = "Pair",
            onAction = onPair,
        )
        apps.isEmpty() -> Message(
            title = "Loading apps…",
            body = "Getting the app list from the TV. If it doesn't appear, make sure the TV is on.",
            action = "Try again",
            onAction = onRefresh,
        )
        else -> AppGrid(apps, onLaunch)
    }
}

@Composable
private fun AppGrid(apps: List<TvApp>, onLaunch: (TvApp) -> Unit) {
    val view = LocalView.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 96.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(apps, key = { it.packageName }) { app ->
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onLaunch(app)
                    }
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppIcon(app, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                Spacer(Modifier.height(6.dp))
                Text(
                    app.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun AppIcon(app: TvApp, modifier: Modifier) {
    val bitmap by produceState<ImageBitmap?>(initialValue = IconCache.peek(app.iconUrl), app.iconUrl) {
        if (value == null) value = IconCache.load(app.iconUrl)
    }
    val shape = RoundedCornerShape(12.dp)
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = modifier.clip(shape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                app.name.take(1).uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Message(title: String, body: String, action: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Apps,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        if (action == "Try again") {
            OutlinedButton(onClick = onAction) { Text(action) }
        } else {
            Button(onClick = onAction) { Text(action) }
        }
    }
}

/** Small in-memory cache for app icons (Amazon CDN images listed by the TV). */
private object IconCache {
    private val cache = LruCache<String, ImageBitmap>(64)

    fun peek(url: String?): ImageBitmap? = url?.let { cache.get(it) }

    suspend fun load(url: String?): ImageBitmap? {
        if (url == null) return null
        cache.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val c = URL(url).openConnection()
                c.connectTimeout = 4000
                c.readTimeout = 6000
                c.getInputStream().use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
            }.getOrNull()
        }?.also { cache.put(url, it) }
    }
}
