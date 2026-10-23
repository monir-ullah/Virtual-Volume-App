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

/**
 * The core volume lock engine enforcing fixed volume levels across Android audio streams.
 *
 * WHAT IT DOES:
 * - Locks specific or all audio streams to designated integer volume levels.
 * - Monitors hardware volume buttons and external volume changes in real-time.
 * - If an external change is detected on a locked stream, the engine immediately overrides it
 *   and restores the volume back to the locked target level.
 *
 * WHY IT EXISTS:
 * - Prevents accidental volume changes (e.g., pressing volume buttons in pocket, kids changing volume,
 *   or rogue apps altering sound levels).
 *
 * HOW IT WORKS:
 * 1. Collects volume change events from [VolumeRepository.observeVolumeChanges].
 * 2. Compares the stream's new system volume level with its cached target locked level.
 * 3. If `currentVolume != lockedVolume`, calls [VolumeRepository.setVolume] to reset it.
 * 4. Uses a concurrent thread-safe set ([restoringStreams]) to avoid infinite feedback loops
 *    when resetting volume!
 */
class VolumeLockEngine(
    private val volumeRepository: VolumeRepository,
    private val volumeLockPrefs: VolumeLockPrefs? = null
) {
    // Observable state for master lock activity
    private val _isLockActive = MutableStateFlow(false)
    val isLockActive: StateFlow<Boolean> = _isLockActive.asStateFlow()

    // Observable set of streams currently locked
    private val _lockedStreams = MutableStateFlow<Set<AudioStreamType>>(emptySet())
    val lockedStreams: StateFlow<Set<AudioStreamType>> = _lockedStreams.asStateFlow()

    // Observable map storing the target locked volume level for each stream
    private val _lockedVolumes = MutableStateFlow<Map<AudioStreamType, Int>>(emptyMap())
    val lockedVolumes: StateFlow<Map<AudioStreamType, Int>> = _lockedVolumes.asStateFlow()

    // Background monitoring coroutine job
    private var monitoringJob: Job? = null

    // Set tracking streams currently undergoing restoration to prevent infinite feedback loops
    private val restoringStreams = ConcurrentHashMap.newKeySet<AudioStreamType>()

    // Low-level primitive array caches for O(1) ultra-fast lookups without heap object allocations
    private val lockedLevelsCache = IntArray(AudioStreamType.entries.size) { -1 }
    private val isStreamLockedMask = BooleanArray(AudioStreamType.entries.size) { false }

    /**
     * Starts the background coroutine job monitoring system volume changes in real-time.
     *
     * @param scope [CoroutineScope] in which to launch the monitoring job (typically applicationScope).
     * @param dispatcher Coroutine dispatcher (defaults to [Dispatchers.Default]).
     */
    fun start(scope: CoroutineScope, dispatcher: CoroutineDispatcher = Dispatchers.Default) {
        if (monitoringJob != null) return // Already running
        monitoringJob = scope.launch(dispatcher) {
            // Collect volume button presses and system volume changes
            volumeRepository.observeVolumeChanges().collect { changedStream ->
                checkAndRestoreVolume(changedStream)
            }
        }
    }

    /**
     * Stops background volume monitoring job.
     */
    fun stop() {
        monitoringJob?.cancel()
        monitoringJob = null
    }

    /**
     * Enables master volume lock across specified or all audio streams.
     *
     * @param streamsToLock Streams to protect (defaults to all audio streams).
     * @param customLevels Optional explicit volume levels map. If null, captures current device volumes.
     */
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

        // Update fast primitive array caches
        updatePrimitiveCaches(true, streamsToLock, currentVolumes)
        // Persist lock configuration to disk
        volumeLockPrefs?.saveLockState(true, streamsToLock, currentVolumes)

        Log.d("VolumeLockEngine", "Volume lock ENABLED for streams: $streamsToLock with levels: $currentVolumes")
    }

    /**
     * Disables master volume lock and unlocks all audio streams.
     */
    fun disableLock() {
        _isLockActive.value = false
        _lockedStreams.value = emptySet()
        _lockedVolumes.value = emptyMap()

        // Clear fast primitive caches
        updatePrimitiveCaches(false, emptySet(), emptyMap())
        // Update persistent disk storage
        volumeLockPrefs?.isMasterLocked = false

        Log.d("VolumeLockEngine", "Volume lock DISABLED")
    }

    /**
     * Locks a specific individual audio stream without locking all streams.
     *
     * @param streamType The target stream to lock.
     * @param level Target level to lock to. If null, captures current stream volume.
     */
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

    /**
     * Unlocks a specific individual audio stream.
     *
     * @param streamType The target stream to unlock.
     */
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

    /**
     * Adjusts the target locked volume level for a stream while lock remains active.
     *
     * @param streamType Target stream.
     * @param level New target volume level integer.
     */
    fun setLockedVolume(streamType: AudioStreamType, level: Int) {
        val maxVolume = volumeRepository.getVolumeState(streamType).maxVolume
        val minVolume = volumeRepository.getVolumeState(streamType).minVolume
        val clampedLevel = level.coerceIn(minVolume, maxVolume)

        val updatedVolumes = _lockedVolumes.value + (streamType to clampedLevel)
        _lockedVolumes.value = updatedVolumes

        updatePrimitiveCaches(_isLockActive.value, _lockedStreams.value, updatedVolumes)
        volumeLockPrefs?.saveLockedVolumes(updatedVolumes)

        // Immediately apply new target volume level to system
        if (_isLockActive.value && _lockedStreams.value.contains(streamType)) {
            restoringStreams.add(streamType)
            volumeRepository.setVolume(streamType, clampedLevel, showUi = false)
            restoringStreams.remove(streamType)
        }
    }

    /**
     * Restores in-memory lock state from saved disk preferences.
     *
     * @param streams Locked streams set.
     * @param volumes Map of locked volume levels.
     */
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

    /**
     * Checks if a specific stream is currently locked.
     *
     * @param streamType Target stream.
     * @return `true` if master lock is active and this stream is in the locked set.
     */
    fun isStreamLocked(streamType: AudioStreamType): Boolean {
        return _isLockActive.value && _lockedStreams.value.contains(streamType)
    }

    /**
     * Returns the target locked volume integer level for a stream, or `null` if not locked.
     */
    fun getLockedVolume(streamType: AudioStreamType): Int? {
        return _lockedVolumes.value[streamType]
    }

    /**
     * Updates primitive arrays used for O(1) ultra-fast lookups during high-frequency volume events.
     */
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

    /**
     * Compares device volume with locked volume. Restores target level if changed externally.
     *
     * CRITICAL FEEDBACK LOOP PREVENTION:
     * When this function calls `volumeRepository.setVolume(...)`, Android triggers a volume change event.
     * We add [streamType] to [restoringStreams] before setting volume and remove it immediately after,
     * ensuring we ignore our own programmatic volume corrections!
     */
    private fun checkAndRestoreVolume(streamType: AudioStreamType) {
        val index = streamType.ordinal
        if (!_isLockActive.value) return
        if (!isStreamLockedMask[index]) return
        if (restoringStreams.contains(streamType)) return // Ignore our own restore operation

        val targetVolume = lockedLevelsCache[index]
        if (targetVolume < 0) return

        val currentVolume = volumeRepository.getVolumeState(streamType).currentVolume

        // If system volume differs from our locked target, restore it!
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
