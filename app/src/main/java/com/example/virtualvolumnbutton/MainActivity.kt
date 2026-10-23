package com.example.virtualvolumnbutton

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.virtualvolumnbutton.ui.screen.VolumeControllerScreen
import com.example.virtualvolumnbutton.ui.theme.VirtualVolumnButtonTheme
import com.example.virtualvolumnbutton.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShortcutIntent(intent)
        setContent {
            VirtualVolumnButtonTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VolumeControllerScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcutIntent(intent)
    }

    private fun handleShortcutIntent(intent: Intent?) {
        val action = intent?.getStringExtra("action") ?: return
        when (action) {
            "toggle_lock" -> {
                val isLocked = viewModel.uiState.value.isMasterLockActive
                viewModel.toggleMasterLock(!isLocked)
            }
            "start_overlay" -> {
                viewModel.setFloatingServiceEnabled(true)
            }
        }
    }
}
