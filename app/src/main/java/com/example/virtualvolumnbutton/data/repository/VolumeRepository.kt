package com.example.virtualvolumnbutton.data.repository

import com.example.virtualvolumnbutton.data.model.AudioStreamType
import com.example.virtualvolumnbutton.data.model.VolumeStreamState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface contract defining operations for querying and adjusting Android audio stream volumes.
 *
 * WHAT IT DOES:
 * - Exposes real-time volume states via observable [StateFlow].
 * - Allows setting volume levels, toggling mute status, and listening for system hardware volume button events.
 *
 * WHY IT EXISTS:
 * - Decouples low-level system APIs ([android.media.AudioManager], Broadcast Receivers, Content Observers)
 *   from ViewModels and UI, making code easier to test and maintain.
 */
interface VolumeRepository {

    /**
     * Observable state flow mapping each [AudioStreamType] to its current [VolumeStreamState].
     */
    val volumeStates: StateFlow<Map<AudioStreamType, VolumeStreamState>>

    /**
     * Queries and returns the current volume snapshot for a specific audio stream.
     *
     * @param streamType The stream to query.
     * @return Current [VolumeStreamState].
     */
    fun getVolumeState(streamType: AudioStreamType): VolumeStreamState

    /**
     * Sets the volume level for a specific stream.
     *
     * @param streamType The target audio stream.
     * @param level The target integer volume level.
     * @param showUi If `true`, Android's native system volume dialog will pop up on screen.
     */
    fun setVolume(streamType: AudioStreamType, level: Int, showUi: Boolean = false)

    /**
     * Toggles mute state for a specific stream.
     *
     * @param streamType The target audio stream.
     * @param mute `true` to mute, `false` to unmute.
     */
    fun setMute(streamType: AudioStreamType, mute: Boolean)

    /**
     * Returns a Kotlin [Flow] emitting [AudioStreamType] whenever system hardware volume changes occur.
     */
    fun observeVolumeChanges(): Flow<AudioStreamType>

    /**
     * Forces an immediate re-query of all stream volumes from system [android.media.AudioManager].
     */
    fun refreshVolumeStates()
}
