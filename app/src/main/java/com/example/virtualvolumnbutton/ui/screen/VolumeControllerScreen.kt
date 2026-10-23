package com.example.virtualvolumnbutton.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.virtualvolumnbutton.data.model.AudioStreamType
import com.example.virtualvolumnbutton.data.model.VolumeStreamState
import com.example.virtualvolumnbutton.ui.components.FloatingServiceCard
import com.example.virtualvolumnbutton.ui.components.MasterLockCard
import com.example.virtualvolumnbutton.ui.components.PermissionCard
import com.example.virtualvolumnbutton.ui.components.VolumeStreamCard
import com.example.virtualvolumnbutton.ui.theme.VirtualVolumnButtonTheme
import com.example.virtualvolumnbutton.ui.viewmodel.MainUiState
import com.example.virtualvolumnbutton.ui.viewmodel.MainViewModel
import com.example.virtualvolumnbutton.util.PermissionHelper

/**
 * State-aware Composable container screen for the Virtual Volume app.
 *
 * WHAT IT DOES:
 * - Collects the [MainUiState] reactive flow from [MainViewModel].
 * - Automatically re-checks permissions and volume states whenever the app screen returns to focus (`ON_RESUME`).
 * - Displays an Exit Confirmation [AlertDialog] when the user requests to shut down all background services.
 * - Passes UI state and event callbacks down to [VolumeControllerContent].
 *
 * WHY IT EXISTS:
 * - Separates state-handling logic from purely visual rendering, enabling clean previews and unit testing.
 */
@Composable
fun VolumeControllerScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    // Collect UI state safely with lifecycle awareness (pauses collection when screen stopped)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Local state controlling the exit confirmation dialog visibility
    var showExitConfirmationDialog by remember { mutableStateOf(false) }

    // Re-check permissions & volume levels whenever the app screen comes into foreground
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
                viewModel.refreshVolumeStates()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Exit Confirmation Dialog
    if (showExitConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmationDialog = false },
            title = {
                Text(text = "Exit Virtual Volume?")
            },
            text = {
                Text(text = "Are you sure you want to stop all volume lock services, remove the floating button, and close the application?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirmationDialog = false
                        viewModel.exitAppAndStopServices(context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Exit App")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showExitConfirmationDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Render screen UI layout
    VolumeControllerContent(
        uiState = uiState,
        onRefresh = { viewModel.refreshVolumeStates() },
        onToggleMasterLock = { enabled -> viewModel.toggleMasterLock(enabled) },
        onToggleFloatingService = { enabled -> viewModel.setFloatingServiceEnabled(enabled) },
        onRequestOverlayPermission = {
            context.startActivity(PermissionHelper.getOverlayPermissionIntent(context))
        },
        onRequestNotificationPolicyPermission = {
            context.startActivity(PermissionHelper.getNotificationPolicyIntent())
        },
        onVolumeChange = { streamType, level -> viewModel.setVolume(streamType, level) },
        onMuteToggle = { streamType -> viewModel.toggleMute(streamType) },
        onLockToggle = { streamType -> viewModel.toggleStreamLock(streamType) },
        onExitApp = { showExitConfirmationDialog = true },
        modifier = modifier
    )
}

/**
 * Pure visual content Composable for [VolumeControllerScreen].
 *
 * WHAT IT DOES:
 * - Renders a Material Design 3 screen layout with a collapsible top app bar.
 * - Displays Permission alerts, Master Lock card, Floating Overlay Service card,
 *   individual audio stream sliders, and an Exit App action card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeControllerContent(
    uiState: MainUiState,
    onRefresh: () -> Unit,
    onToggleMasterLock: (Boolean) -> Unit,
    onToggleFloatingService: (Boolean) -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPolicyPermission: () -> Unit,
    onVolumeChange: (AudioStreamType, Int) -> Unit,
    onMuteToggle: (AudioStreamType) -> Unit,
    onLockToggle: (AudioStreamType) -> Unit,
    modifier: Modifier = Modifier,
    onExitApp: () -> Unit = {}
) {
    // Nested scroll behavior for collapsing top app bar on scroll
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Virtual Volume",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Control & Lock Engine",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    // Manual Refresh Button
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh Volume States"
                        )
                    }
                    // Exit App Button
                    IconButton(onClick = onExitApp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ExitToApp,
                            contentDescription = "Exit App & Stop All Services",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card 1: System Permissions Warning Card (shown if permissions missing)
            item {
                PermissionCard(
                    hasOverlayPermission = uiState.hasOverlayPermission,
                    hasNotificationPolicyPermission = uiState.hasNotificationPolicyPermission,
                    onRequestOverlayPermission = onRequestOverlayPermission,
                    onRequestNotificationPolicyPermission = onRequestNotificationPolicyPermission
                )
            }

            // Card 2: Master Volume Lock Toggle Card
            item {
                MasterLockCard(
                    isLocked = uiState.isMasterLockActive,
                    onToggleLock = onToggleMasterLock
                )
            }

            // Card 3: Floating Overlay Service Toggle Card
            item {
                FloatingServiceCard(
                    isServiceEnabled = uiState.isFloatingServiceEnabled,
                    hasOverlayPermission = uiState.hasOverlayPermission,
                    onToggleService = onToggleFloatingService,
                    onRequestOverlayPermission = onRequestOverlayPermission
                )
            }

            // Section Header: Audio Streams
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Audio Streams",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Cards 4+: Individual Stream Cards (Media, Ring, Alarm, Notification)
            items(
                count = AudioStreamType.entries.size,
                key = { index -> AudioStreamType.entries[index].name }
            ) { index ->
                val streamType = AudioStreamType.entries[index]
                val volumeState = uiState.volumeStates[streamType]
                val isLocked = uiState.lockedStreams.contains(streamType) || uiState.isMasterLockActive

                VolumeStreamCard(
                    streamType = streamType,
                    volumeState = volumeState,
                    isLocked = isLocked,
                    onVolumeChange = { level -> onVolumeChange(streamType, level) },
                    onMuteToggle = { onMuteToggle(streamType) },
                    onLockToggle = { onLockToggle(streamType) }
                )
            }

            // Card Last: Exit App & Stop All Services Card
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ExitToApp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Column {
                                Text(
                                    text = "Exit App & Stop Services",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "Stops floating overlay, unlocks all audio streams, and closes app completely.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                        Button(
                            onClick = onExitApp,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ExitToApp,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Exit App & Stop All Services",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Android Studio UI Design Preview for [VolumeControllerContent].
 */
@Preview(showBackground = true)
@Composable
fun VolumeControllerScreenPreview() {
    val sampleVolumeStates = mapOf(
        AudioStreamType.MEDIA to VolumeStreamState(AudioStreamType.MEDIA, 10, 15),
        AudioStreamType.RING to VolumeStreamState(AudioStreamType.RING, 8, 15),
        AudioStreamType.ALARM to VolumeStreamState(AudioStreamType.ALARM, 12, 15),
        AudioStreamType.NOTIFICATION to VolumeStreamState(AudioStreamType.NOTIFICATION, 5, 15)
    )

    VirtualVolumnButtonTheme {
        VolumeControllerContent(
            uiState = MainUiState(
                volumeStates = sampleVolumeStates,
                isMasterLockActive = false,
                lockedStreams = setOf(AudioStreamType.RING),
                isFloatingServiceEnabled = true,
                hasOverlayPermission = false,
                hasNotificationPolicyPermission = true
            ),
            onRefresh = {},
            onToggleMasterLock = {},
            onToggleFloatingService = {},
            onRequestOverlayPermission = {},
            onRequestNotificationPolicyPermission = {},
            onVolumeChange = { _, _ -> },
            onMuteToggle = {},
            onLockToggle = {},
            onExitApp = {}
        )
    }
}
