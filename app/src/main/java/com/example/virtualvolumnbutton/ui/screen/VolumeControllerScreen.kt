package com.example.virtualvolumnbutton.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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

@Composable
fun VolumeControllerScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

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
        modifier = modifier
    )
}

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
    modifier: Modifier = Modifier
) {
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
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh Volume States"
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
            item {
                PermissionCard(
                    hasOverlayPermission = uiState.hasOverlayPermission,
                    hasNotificationPolicyPermission = uiState.hasNotificationPolicyPermission,
                    onRequestOverlayPermission = onRequestOverlayPermission,
                    onRequestNotificationPolicyPermission = onRequestNotificationPolicyPermission
                )
            }

            item {
                MasterLockCard(
                    isLocked = uiState.isMasterLockActive,
                    onToggleLock = onToggleMasterLock
                )
            }

            item {
                FloatingServiceCard(
                    isServiceEnabled = uiState.isFloatingServiceEnabled,
                    hasOverlayPermission = uiState.hasOverlayPermission,
                    onToggleService = onToggleFloatingService,
                    onRequestOverlayPermission = onRequestOverlayPermission
                )
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Audio Streams",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

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
        }
    }
}

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
            onLockToggle = {}
        )
    }
}
