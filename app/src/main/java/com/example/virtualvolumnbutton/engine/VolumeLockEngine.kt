package com.example.virtualvolumnbutton.engine

import android.util.Log
import com.example.virtualvolumnbutton.data.local.VolumeLockPrefs
import com.example.virtualvolumnbutton.data.model.AudioStreamType
import com.example.virtualvolumnbutton.data.repository.VolumeRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class VolumeLockEngine(
    private val volumeRepository: VolumeRepository,
    private val volumeLockPrefs: VolumeLockPrefs? = null
) {
    private val _isLockActive = MutableStateFlow(false)
    val isLockActive: StateFlow<Boolean> = _isLockActive.asStateFlow()

    private val _lockedStreams = MutableStateFlow<Set<AudioStreamType>>(emptySet())
    val lockedStreams: StateFlow<Set<AudioStreamType>> = _lockedStreams.asStateFlow()

    private val _lockedVolumes = MutableStateFlow<Map<AudioStreamType, Int>>(emptyMap())
    val lockedVolumes: StateFlow<Map<AudioStreamType, Int>> = _lockedVolumes.asStateFlow()

    private var monitoringJob: Job? = null
    private val restoringStreams = ConcurrentHashMap.newKeySet<AudioStreamType>()

    // Primitive array caching for O(1) checks without extra object allocations
    private val lockedLevelsCache = IntArray(AudioStreamType.entries.size) { -1 }
    private val isStreamLockedMask = BooleanArray(AudioStreamType.entries.size) { false }

    fun start(scope: CoroutineScope, dispatcher: CoroutineDispatcher = Dispatchers.Default) {
        if (monitoringJob != null) return
        monitoringJob = scope.launch(dispatcher) {
            volumeRepository.observeVolumeChanges().collect { changedStream ->
                checkAndRestoreVolume(changedStream)
            }
        }
    }

    fun stop() {
        monitoringJob?.cancel()
        monitoringJob = null
    }

    fun enableLock(
        streamsToLock: Set<AudioStreamType> = AudioStreamType.entries.toSet(),
        customLevels: Map<AudioStreamType, Int>? = null
    ) {
        val currentVolumes = mutableMapOf<AudioStreamType, Int>()
        streamsToLock.forEach { stream ->
            val level = customLevels?.get(stream)
                ?: volumeRepository.getVolumeState(stream).currentVolume
            currentVolumes[stream] = level
        }

        _lockedVolumes.value = currentVolumes
        _lockedStreams.value = streamsToLock
        _isLockActive.value = true

        updatePrimitiveCaches(true, streamsToLock, currentVolumes)
        volumeLockPrefs?.saveLockState(true, streamsToLock, currentVolumes)

        Log.d("VolumeLockEngine", "Volume lock ENABLED for streams: $streamsToLock with levels: $currentVolumes")
    }

    fun disableLock() {
        _isLockActive.value = false
        _lockedStreams.value = emptySet()
        _lockedVolumes.value = emptyMap()

        updatePrimitiveCaches(false, emptySet(), emptyMap())
        volumeLockPrefs?.isMasterLocked = false

        Log.d("VolumeLockEngine", "Volume lock DISABLED")
    }

    fun lockStream(streamType: AudioStreamType, level: Int? = null) {
        val targetLevel = level ?: volumeRepository.getVolumeState(streamType).currentVolume
        val updatedStreams = _lockedStreams.value + streamType
        val updatedVolumes = _lockedVolumes.value + (streamType to targetLevel)

        _lockedStreams.value = updatedStreams
        _lockedVolumes.value = updatedVolumes
        _isLockActive.value = true

        updatePrimitiveCaches(true, updatedStreams, updatedVolumes)
        volumeLockPrefs?.saveLockState(true, updatedStreams, updatedVolumes)

        checkAndRestoreVolume(streamType)
    }

    fun unlockStream(streamType: AudioStreamType) {
        val updatedStreams = _lockedStreams.value - streamType
        val updatedVolumes = _lockedVolumes.value - streamType

        _lockedStreams.value = updatedStreams
        _lockedVolumes.value = updatedVolumes
        val active = updatedStreams.isNotEmpty()
        _isLockActive.value = active

        updatePrimitiveCaches(active, updatedStreams, updatedVolumes)
        volumeLockPrefs?.saveLockState(active, updatedStreams, updatedVolumes)
    }

    fun setLockedVolume(streamType: AudioStreamType, level: Int) {
        val maxVolume = volumeRepository.getVolumeState(streamType).maxVolume
        val minVolume = volumeRepository.getVolumeState(streamType).minVolume
        val clampedLevel = level.coerceIn(minVolume, maxVolume)

        val updatedVolumes = _lockedVolumes.value + (streamType to clampedLevel)
        _lockedVolumes.value = updatedVolumes

        updatePrimitiveCaches(_isLockActive.value, _lockedStreams.value, updatedVolumes)
        volumeLockPrefs?.saveLockedVolumes(updatedVolumes)

        if (_isLockActive.value && _lockedStreams.value.contains(streamType)) {
            restoringStreams.add(streamType)
            volumeRepository.setVolume(streamType, clampedLevel, showUi = false)
            restoringStreams.remove(streamType)
        }
    }

    fun restoreLockState(
        streams: Set<AudioStreamType>,
        volumes: Map<AudioStreamType, Int>
    ) {
        val currentVolumes = mutableMapOf<AudioStreamType, Int>()
        streams.forEach { stream ->
            val level = volumes[stream]
                ?: volumeRepository.getVolumeState(stream).currentVolume
            currentVolumes[stream] = level
        }

        _lockedVolumes.value = currentVolumes
        _lockedStreams.value = streams
        _isLockActive.value = streams.isNotEmpty()

        updatePrimitiveCaches(streams.isNotEmpty(), streams, currentVolumes)
        volumeLockPrefs?.saveLockState(streams.isNotEmpty(), streams, currentVolumes)
    }

    fun isStreamLocked(streamType: AudioStreamType): Boolean {
        return _isLockActive.value && _lockedStreams.value.contains(streamType)
    }

    fun getLockedVolume(streamType: AudioStreamType): Int? {
        return _lockedVolumes.value[streamType]
    }

    private fun updatePrimitiveCaches(
        isMasterLocked: Boolean,
        lockedStreams: Set<AudioStreamType>,
        lockedVolumes: Map<AudioStreamType, Int>
    ) {
        for (stream in AudioStreamType.entries) {
            val index = stream.ordinal
            val locked = isMasterLocked && lockedStreams.contains(stream)
            isStreamLockedMask[index] = locked
            if (locked) {
                lockedLevelsCache[index] = lockedVolumes[stream]
                    ?: volumeRepository.getVolumeState(stream).currentVolume
            } else {
                lockedLevelsCache[index] = -1
            }
        }
    }

    private fun checkAndRestoreVolume(streamType: AudioStreamType) {
        val index = streamType.ordinal
        if (!_isLockActive.value) return
        if (!isStreamLockedMask[index]) return
        if (restoringStreams.contains(streamType)) return

        val targetVolume = lockedLevelsCache[index]
        if (targetVolume < 0) return

        val currentVolume = volumeRepository.getVolumeState(streamType).currentVolume

        if (currentVolume != targetVolume) {
            Log.d(
                "VolumeLockEngine",
                "External volume change detected on ${streamType.name}: $currentVolume != locked $targetVolume. Restoring!"
            )
            restoringStreams.add(streamType)
            try {
                volumeRepository.setVolume(streamType, targetVolume, showUi = false)
            } finally {
                restoringStreams.remove(streamType)
            }
        }
    }
}
