package com.tvremote

import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.tvremote.hid.RemoteButton
import com.tvremote.ui.RemoteApp
import com.tvremote.ui.TvRemoteTheme

class MainActivity : ComponentActivity() {

    private val vm: RemoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            TvRemoteTheme {
                RemoteApp(vm)
            }
        }
    }

    // While connected, the phone's own volume buttons control the TV volume.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val button = volumeButton(keyCode)
        if (button != null && vm.isReady) {
            if (event.repeatCount == 0) vm.down(button) // the TV repeats while held
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val button = volumeButton(keyCode)
        if (button != null && vm.isReady) {
            vm.up(button)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun volumeButton(keyCode: Int): RemoteButton? = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> RemoteButton.VOLUME_UP
        KeyEvent.KEYCODE_VOLUME_DOWN -> RemoteButton.VOLUME_DOWN
        else -> null
    }
}
