package com.example.virtualvolumnbutton.data.model

/**
 * Immutable data snapshot representing the current volume state of a single audio stream.
 *
 * WHAT IT DOES:
 * - Holds the current volume integer level, minimum volume, maximum volume, and mute status.
 *
 * WHY IT EXISTS:
 * - Allows UI components (sliders, text, icons) to observe clean, structured data states
 *   without querying Android's [android.media.AudioManager] on every frame render.
 *
 * @property streamType The target audio stream (Media, Ring, Alarm, Notification).
 * @property currentVolume Current active volume integer level.
 * @property maxVolume Maximum volume integer level supported by the device for this stream.
 * @property minVolume Minimum volume integer level (usually 0, or 1 on some devices for Ring).
 * @property isMuted True if the stream is currently muted or in Do Not Disturb mode.
 */
data class VolumeStreamState(
    val streamType: AudioStreamType,
    val currentVolume: Int,
    val maxVolume: Int,
    val minVolume: Int = 0,
    val isMuted: Boolean = false
) {
    /**
     * Calculates the current volume level as a normalized float percentage between `0.0f` and `1.0f`.
     * Useful for progress bars, linear indicators, and percentage text.
     */
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
