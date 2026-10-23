package com.example.virtualvolumnbutton.data.model

import android.media.AudioManager

/**
 * Enumeration representing the core audio stream types available on Android devices.
 *
 * WHAT IT DOES:
 * - Maps human-readable stream labels ("Media", "Ring", "Alarm", "Notification")
 *   to Android's native system integer constants in [AudioManager].
 *
 * WHY IT EXISTS:
 * - Android controls volume independently for different types of sounds (e.g. music vs incoming call ringer).
 * - This enum provides type safety across the entire codebase instead of passing raw integers around.
 *
 * @property streamType The native Android system stream constant (e.g., [AudioManager.STREAM_MUSIC]).
 * @property label User-friendly UI display name for the audio stream.
 */
enum class AudioStreamType(val streamType: Int, val label: String) {

    /** Music, video, games, and web media playback sound stream. */
    MEDIA(AudioManager.STREAM_MUSIC, "Media"),

    /** Incoming phone calls and ringer sound stream. */
    RING(AudioManager.STREAM_RING, "Ring"),

    /** Clock alarms sound stream. */
    ALARM(AudioManager.STREAM_ALARM, "Alarm"),

    /** App notifications, messages, and system alerts sound stream. */
    NOTIFICATION(AudioManager.STREAM_NOTIFICATION, "Notification");

    companion object {
        /**
         * Converts a raw Android system integer stream constant back to our [AudioStreamType] enum.
         *
         * @param rawType The system integer constant (e.g., [AudioManager.STREAM_MUSIC]).
         * @return Matching [AudioStreamType] or `null` if the integer ID is unrecognized.
         */
        fun fromRawStreamType(rawType: Int): AudioStreamType? {
            return entries.find { it.streamType == rawType }
        }
    }
}
