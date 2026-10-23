package com.example.virtualvolumnbutton

import com.example.virtualvolumnbutton.data.model.AudioStreamType
import com.example.virtualvolumnbutton.data.model.VolumeStreamState
import com.example.virtualvolumnbutton.data.repository.VolumeRepository
import com.example.virtualvolumnbutton.engine.VolumeLockEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * In-memory fake implementation of [VolumeRepository] for unit testing [VolumeLockEngine] without Android dependencies.
 */
class FakeVolumeRepository : VolumeRepository {
    private val volumes = mutableMapOf(
        AudioStreamType.MEDIA to 10,
        AudioStreamType.RING to 7,
        AudioStreamType.ALARM to 5,
        AudioStreamType.NOTIFICATION to 7
    )

    private val _volumeStates = MutableStateFlow<Map<AudioStreamType, VolumeStreamState>>(emptyMap())
    override val volumeStates: StateFlow<Map<AudioStreamType, VolumeStreamState>> = _volumeStates.asStateFlow()

    private val volumeChangesFlow = MutableSharedFlow<AudioStreamType>()

    init {
        refreshVolumeStates()
    }

    override fun getVolumeState(streamType: AudioStreamType): VolumeStreamState {
        val current = volumes[streamType] ?: 0
        return VolumeStreamState(
            streamType = streamType,
            currentVolume = current,
            maxVolume = 15,
            minVolume = 0,
            isMuted = false
        )
    }

    override fun setVolume(streamType: AudioStreamType, level: Int, showUi: Boolean) {
        volumes[streamType] = level.coerceIn(0, 15)
        refreshVolumeStates()
    }

    override fun setMute(streamType: AudioStreamType, mute: Boolean) {
        if (mute) volumes[streamType] = 0
        refreshVolumeStates()
    }

    override fun observeVolumeChanges(): Flow<AudioStreamType> = volumeChangesFlow

    override fun refreshVolumeStates() {
        _volumeStates.value = AudioStreamType.entries.associateWith { getVolumeState(it) }
    }

    /**
     * Simulates an external hardware volume button press or system volume change in tests.
     */
    suspend fun simulateExternalVolumeChange(streamType: AudioStreamType, newLevel: Int) {
        volumes[streamType] = newLevel
        refreshVolumeStates()
        volumeChangesFlow.emit(streamType)
    }
}

/**
 * Unit tests verifying [VolumeLockEngine] logic for locking, overriding external changes, and individual stream controls.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VolumeLockEngineTest {

    private lateinit var fakeRepository: FakeVolumeRepository
    private lateinit var lockEngine: VolumeLockEngine
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        fakeRepository = FakeVolumeRepository()
        lockEngine = VolumeLockEngine(fakeRepository)
        lockEngine.start(testScope.backgroundScope, testDispatcher)
    }

    @Test
    fun testEnableLockCapturesCurrentVolumes() {
        lockEngine.enableLock()

        assertTrue(lockEngine.isLockActive.value)
        assertEquals(10, lockEngine.getLockedVolume(AudioStreamType.MEDIA))
        assertEquals(7, lockEngine.getLockedVolume(AudioStreamType.RING))
    }

    @Test
    fun testExternalVolumeChangeRestoredWhenLocked() = testScope.runTest {
        lockEngine.enableLock()

        // Simulate external volume change on Media stream to level 3
        fakeRepository.simulateExternalVolumeChange(AudioStreamType.MEDIA, 3)

        // Verify lock engine overrides change and restores back to locked level 10
        assertEquals(10, fakeRepository.getVolumeState(AudioStreamType.MEDIA).currentVolume)
    }

    @Test
    fun testExternalVolumeChangeAllowedWhenUnlocked() = testScope.runTest {
        lockEngine.enableLock()
        lockEngine.disableLock()

        assertFalse(lockEngine.isLockActive.value)

        // Simulate external volume change when unlocked
        fakeRepository.simulateExternalVolumeChange(AudioStreamType.MEDIA, 3)

        // Verify volume change is accepted
        assertEquals(3, fakeRepository.getVolumeState(AudioStreamType.MEDIA).currentVolume)
    }

    @Test
    fun testDisableLockUnlocksAllStreams() {
        lockEngine.enableLock()
        assertTrue(lockEngine.isLockActive.value)

        lockEngine.disableLock()
        assertFalse(lockEngine.isLockActive.value)
        assertTrue(lockEngine.lockedStreams.value.isEmpty())
        assertTrue(lockEngine.lockedVolumes.value.isEmpty())
    }

    @Test
    fun testLockIndividualStream() {
        lockEngine.lockStream(AudioStreamType.MEDIA, 12)

        assertTrue(lockEngine.isLockActive.value)
        assertTrue(lockEngine.isStreamLocked(AudioStreamType.MEDIA))
        assertEquals(12, lockEngine.getLockedVolume(AudioStreamType.MEDIA))
        assertFalse(lockEngine.isStreamLocked(AudioStreamType.RING))
    }

    @Test
    fun testUnlockIndividualStream() {
        lockEngine.lockStream(AudioStreamType.MEDIA, 12)
        lockEngine.lockStream(AudioStreamType.RING, 8)

        lockEngine.unlockStream(AudioStreamType.MEDIA)

        assertFalse(lockEngine.isStreamLocked(AudioStreamType.MEDIA))
        assertTrue(lockEngine.isStreamLocked(AudioStreamType.RING))
        assertTrue(lockEngine.isLockActive.value)

        lockEngine.unlockStream(AudioStreamType.RING)
        assertFalse(lockEngine.isLockActive.value)
    }
}
