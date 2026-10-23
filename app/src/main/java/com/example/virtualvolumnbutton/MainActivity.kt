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

/**
 * Main Activity and primary user entry point for the Virtual Volume app.
 *
 * WHAT IT DOES:
 * - Hosts the Jetpack Compose user interface ([VolumeControllerScreen]).
 * - Configures full edge-to-edge window layout rendering under status and navigation bars.
 * - Handles incoming launcher app shortcut intents (e.g., toggling volume lock or starting floating overlay directly).
 *
 * WHY IT EXISTS:
 * - Acts as the single activity container in modern Android Compose single-activity architecture.
 *
 * HOW IT WORKS:
 * - Extends [ComponentActivity].
 * - Uses [viewModels] delegate to obtain and retain [MainViewModel] across configuration changes.
 * - Sets Compose content inside [setContent] wrapped with [VirtualVolumnButtonTheme].
 */
class MainActivity : ComponentActivity() {

    // ViewModel instance retained across screen configuration changes (e.g. rotations)
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Step 1: Enable modern edge-to-edge window rendering
        enableEdgeToEdge()

        // Step 2: Handle any shortcut intent actions passed upon Activity creation
        handleShortcutIntent(intent)

        // Step 3: Inflate Jetpack Compose UI
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

    /**
     * Called when the Activity is re-launched while already running in the background.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcutIntent(intent)
    }

    /**
     * Inspects incoming intents for launcher shortcut extra actions.
     *
     * Actions supported:
     * - `"toggle_lock"`: Quick-toggles the master volume lock state.
     * - `"start_overlay"`: Immediately starts the floating volume button overlay service.
     *
     * @param intent Incoming intent passed to the Activity.
     */
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
