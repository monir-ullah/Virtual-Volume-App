package com.example.virtualvolumnbutton.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.virtualvolumnbutton.VirtualVolumeApp
import com.example.virtualvolumnbutton.data.model.AudioStreamType
import com.example.virtualvolumnbutton.data.model.VolumeStreamState
import com.example.virtualvolumnbutton.data.repository.VolumeRepository
import com.example.virtualvolumnbutton.data.repository.VolumeRepositoryImpl
import com.example.virtualvolumnbutton.engine.VolumeLockEngine
import com.example.virtualvolumnbutton.service.FloatingOverlayService
import com.example.virtualvolumnbutton.service.FloatingOverlayServiceController
import com.example.virtualvolumnbutton.util.PermissionHelper
import com.example.virtualvolumnbutton.util.findActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Immutable UI State holder data class for [com.example.virtualvolumnbutton.ui.screen.VolumeControllerScreen].
 *
 * @property volumeStates Map of audio streams to their volume level snapshots.
 * @property isMasterLockActive True if master volume lock is active.
 * @property lockedStreams Set of audio streams currently locked.
 * @property isFloatingServiceEnabled True if the floating overlay button service is running.
 * @property hasOverlayPermission True if system overlay draw permission ("Display over other apps") is granted.
 * @property hasNotificationPolicyPermission True if Notification Policy Access (Do Not Disturb permission) is granted.
 */
data class MainUiState(
    val volumeStates: Map<AudioStreamType, VolumeStreamState> = emptyMap(),
    val isMasterLockActive: Boolean = false,
    val lockedStreams: Set<AudioStreamType> = emptySet(),
    val isFloatingServiceEnabled: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val hasNotificationPolicyPermission: Boolean = false
)

/**
 * Architecture ViewModel powering the main volume control screen.
 *
 * WHAT IT DOES:
 * - Bridges the UI layout ([com.example.virtualvolumnbutton.ui.screen.VolumeControllerScreen])
 *   with the underlying data layer ([VolumeRepository]) and logic layer ([VolumeLockEngine]).
 * - Combines multiple background data streams into a single, unified [MainUiState] flow.
 * - Dispatches user UI interaction events (adjusting sliders, toggling mute, turning on lock, starting overlay service).
 *
 * WHY IT EXISTS:
 * - Follows official Android Uni-directional Data Flow (UDF) architectural guidelines.
 * - Survives screen configuration changes (such as device rotations).
 *
 * HOW IT WORKS:
 * - Uses Kotlin `combine` operators to reactively merge volume state flows, lock status flows,
 *   floating service controller flows, and permission flows into `uiState`.
 * - Uses `stateIn` with `SharingStarted.WhileSubscribed(5000)` to automatically pause processing
 *   when the UI screen goes into background to conserve battery.
 */
class MainViewModel @JvmOverloads constructor(
    application: Application,
    private val volumeRepository: VolumeRepository = (application as? VirtualVolumeApp)?.volumeRepository ?: VolumeRepositoryImpl(application),
    private val volumeLockEngine: VolumeLockEngine = (application as? VirtualVolumeApp)?.volumeLockEngine ?: VolumeLockEngine(volumeRepository)
) : AndroidViewModel(application) {

    // Internal state flows for system permission statuses
    private val _hasOverlayPermission = MutableStateFlow(false)
    private val _hasNotificationPolicyPermission = MutableStateFlow(false)

    // Flow combining overlay and notification policy permissions into a pair
    private val permissionsFlow = combine(
        _hasOverlayPermission,
        _hasNotificationPolicyPermission
    ) { overlay, notif ->
        overlay to notif
    }

    // Flow combining master lock activity and locked streams set
    private val lockStateFlow = combine(
        volumeLockEngine.isLockActive,
        volumeLockEngine.lockedStreams
    ) { isMaster, lockedStreams ->
        isMaster to lockedStreams
    }

    /**
     * Unified, reactive StateFlow observed by [com.example.virtualvolumnbutton.ui.screen.VolumeControllerScreen].
     */
    val uiState: StateFlow<MainUiState> = combine(
        volumeRepository.volumeStates,
        lockStateFlow,
        FloatingOverlayServiceController.isServiceRunning,
        permissionsFlow
    ) { volumeStates, lockState, isServiceRunning, permissions ->
        MainUiState(
            volumeStates = volumeStates,
            isMasterLockActive = lockState.first,
            lockedStreams = lockState.second,
            isFloatingServiceEnabled = isServiceRunning,
            hasOverlayPermission = permissions.first,
            hasNotificationPolicyPermission = permissions.second
        )
    }.stateIn(
        scope = viewModelScope,
        // WhileSubscribed(5000): Keeps flow active for 5s after screen unbinds to handle brief rotations cleanly
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    init {
        // Step 1: Ensure background lock engine is started via application scope
        (getApplication<Application>() as? VirtualVolumeApp)?.let { app ->
            app.volumeLockEngine.start(app.applicationScope)
        }

        // Step 2: Observe volume change events in viewModelScope
        viewModelScope.launch {
            volumeRepository.observeVolumeChanges().collect {
                // System volume updates trigger volumeStates update automatically
            }
        }

        // Step 3: Query initial permission status
        refreshPermissions()
    }

    /**
     * Re-checks system permission status for Overlay Draw and Notification Policy Access.
     */
    fun refreshPermissions() {
        val context = getApplication<Application>()
        _hasOverlayPermission.value = PermissionHelper.hasOverlayPermission(context)
        _hasNotificationPolicyPermission.value = PermissionHelper.hasNotificationPolicyAccess(context)
    }

    /**
     * Adjusts the volume level for a specific stream.
     * If the stream is currently locked, updates its locked level target in [VolumeLockEngine].
     * Otherwise, sets the level directly in [VolumeRepository].
     *
     * @param streamType Target stream.
     * @param level Target volume level integer.
     */
    fun setVolume(streamType: AudioStreamType, level: Int) {
        if (volumeLockEngine.isStreamLocked(streamType)) {
            volumeLockEngine.setLockedVolume(streamType, level)
        } else {
            volumeRepository.setVolume(streamType, level)
        }
    }

    /**
     * Toggles mute state for a specific stream.
     *
     * @param streamType Target stream.
     */
    fun toggleMute(streamType: AudioStreamType) {
        val currentState = volumeRepository.getVolumeState(streamType)
        val targetMute = !currentState.isMuted
        volumeRepository.setMute(streamType, targetMute)
    }

    /**
     * Toggles master volume lock on or off across all audio streams.
     *
     * @param enabled `true` to lock all volumes, `false` to unlock.
     */
    fun toggleMasterLock(enabled: Boolean) {
        if (enabled) {
            volumeLockEngine.enableLock()
        } else {
            volumeLockEngine.disableLock()
        }
    }

    /**
     * Toggles lock status for a single individual audio stream.
     *
     * @param streamType Target stream.
     */
    fun toggleStreamLock(streamType: AudioStreamType) {
        if (volumeLockEngine.isStreamLocked(streamType)) {
            volumeLockEngine.unlockStream(streamType)
        } else {
            volumeLockEngine.lockStream(streamType)
        }
    }

    /**
     * Starts or stops the [FloatingOverlayService] foreground service.
     *
     * @param enabled `true` to start floating overlay button service, `false` to stop.
     */
    fun setFloatingServiceEnabled(enabled: Boolean) {
        val context = getApplication<Application>()
        if (enabled) {
            if (PermissionHelper.hasOverlayPermission(context)) {
                val intent = Intent(context, FloatingOverlayService::class.java).apply {
                    action = FloatingOverlayService.ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        } else {
            val intent = Intent(context, FloatingOverlayService::class.java).apply {
                action = FloatingOverlayService.ACTION_STOP
            }
            context.startService(intent)
        }
    }

    /**
     * Manually forces an immediate refresh of stream volume levels from Android's system [android.media.AudioManager].
     */
    fun refreshVolumeStates() {
        volumeRepository.refreshVolumeStates()
    }

    /**
     * Completely shuts down all active background services, stops the volume lock engine,
     * unlocks all streams, and terminates the application task cleanly.
     *
     * @param context Application or Activity context.
     */
    fun exitAppAndStopServices(context: Context) {
        // Stop floating overlay service
        val intent = Intent(context, FloatingOverlayService::class.java).apply {
            action = FloatingOverlayService.ACTION_STOP
        }
        context.stopService(intent)

        // Disable volume lock engine
        volumeLockEngine.disableLock()
        volumeLockEngine.stop()

        // Finish activity and remove task from Android recent apps stack
        context.findActivity()?.finishAndRemoveTask()
    }
}
