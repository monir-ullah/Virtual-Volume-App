package com.example.virtualvolumnbutton.data.repository

import com.example.virtualvolumnbutton.data.model.AudioStreamType
import com.example.virtualvolumnbutton.data.model.VolumeStreamState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface VolumeRepository {
    val volumeStates: StateFlow<Map<AudioStreamType, VolumeStreamState>>

    fun getVolumeState(streamType: AudioStreamType): VolumeStreamState
    fun setVolume(streamType: AudioStreamType, level: Int, showUi: Boolean = false)
    fun setMute(streamType: AudioStreamType, mute: Boolean)
    fun observeVolumeChanges(): Flow<AudioStreamType>
    fun refreshVolumeStates()
}
