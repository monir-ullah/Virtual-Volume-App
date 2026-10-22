package com.example.virtualvolumnbutton.data.model

data class VolumeStreamState(
    val streamType: AudioStreamType,
    val currentVolume: Int,
    val maxVolume: Int,
    val minVolume: Int = 0,
    val isMuted: Boolean = false
) {
    val volumePercentage: Float
        get() {
            val range = maxVolume - minVolume
            return if (range > 0) {
                ((currentVolume - minVolume).toFloat() / range.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
        }
}
