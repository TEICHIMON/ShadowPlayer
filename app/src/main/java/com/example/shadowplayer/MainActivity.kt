package com.example.shadowplayer

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.shadowplayer.ui.navigation.AppNavigation
import com.example.shadowplayer.ui.theme.ShadowPlayerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // 音量键控制回调，返回值表示是否拦截该按键事件
    var onVolumeUp: (() -> Boolean)? = null
    var onVolumeDown: (() -> Boolean)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShadowPlayerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                val intercepted = onVolumeUp?.invoke() ?: false
                if (intercepted) true else super.onKeyDown(keyCode, event)
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                val intercepted = onVolumeDown?.invoke() ?: false
                if (intercepted) true else super.onKeyDown(keyCode, event)
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}