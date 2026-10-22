package com.example.virtualvolumnbutton.ui.viewmodel

import android.app.Application
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val volumeStates: Map<AudioStreamType, VolumeStreamState> = emptyMap(),
    val isMasterLockActive: Boolean = false,
    val lockedStreams: Set<AudioStreamType> = emptySet(),
    val isFloatingServiceEnabled: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val hasNotificationPolicyPermission: Boolean = false
)

class MainViewModel @JvmOverloads constructor(
    application: Application,
    private val volumeRepository: VolumeRepository = (application as? VirtualVolumeApp)?.volumeRepository ?: VolumeRepositoryImpl(application),
    private val volumeLockEngine: VolumeLockEngine = (application as? VirtualVolumeApp)?.volumeLockEngine ?: VolumeLockEngine(volumeRepository)
) : AndroidViewModel(application) {

    private val _hasOverlayPermission = MutableStateFlow(false)
    private val _hasNotificationPolicyPermission = MutableStateFlow(false)

    private val permissionsFlow = combine(
        _hasOverlayPermission,
        _hasNotificationPolicyPermission
    ) { overlay, notif ->
        overlay to notif
    }

    private val lockStateFlow = combine(
        volumeLockEngine.isLockActive,
        volumeLockEngine.lockedStreams
    ) { isMaster, lockedStreams ->
        isMaster to lockedStreams
    }

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
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    init {
        volumeLockEngine.start(viewModelScope)

        viewModelScope.launch {
            volumeRepository.observeVolumeChanges().collect {
                // Audio changes in system will trigger volumeStates update
            }
        }

        refreshPermissions()
    }

    fun refreshPermissions() {
        val context = getApplication<Application>()
        _hasOverlayPermission.value = PermissionHelper.hasOverlayPermission(context)
        _hasNotificationPolicyPermission.value = PermissionHelper.hasNotificationPolicyAccess(context)
    }

    fun setVolume(streamType: AudioStreamType, level: Int) {
        if (volumeLockEngine.isStreamLocked(streamType)) {
            volumeLockEngine.setLockedVolume(streamType, level)
        } else {
            volumeRepository.setVolume(streamType, level)
        }
    }

    fun toggleMute(streamType: AudioStreamType) {
        val currentState = volumeRepository.getVolumeState(streamType)
        val targetMute = !currentState.isMuted
        volumeRepository.setMute(streamType, targetMute)
    }

    fun toggleMasterLock(enabled: Boolean) {
        if (enabled) {
            volumeLockEngine.enableLock()
        } else {
            volumeLockEngine.disableLock()
        }
    }

    fun toggleStreamLock(streamType: AudioStreamType) {
        if (volumeLockEngine.isStreamLocked(streamType)) {
            volumeLockEngine.unlockStream(streamType)
        } else {
            volumeLockEngine.lockStream(streamType)
        }
    }

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

    fun refreshVolumeStates() {
        volumeRepository.refreshVolumeStates()
    }
}
