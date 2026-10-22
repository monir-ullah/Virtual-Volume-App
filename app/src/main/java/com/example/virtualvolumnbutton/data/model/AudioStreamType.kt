package com.example.virtualvolumnbutton.data.model

import android.media.AudioManager

enum class AudioStreamType(val streamType: Int, val label: String) {
    MEDIA(AudioManager.STREAM_MUSIC, "Media"),
    RING(AudioManager.STREAM_RING, "Ring"),
    ALARM(AudioManager.STREAM_ALARM, "Alarm"),
    NOTIFICATION(AudioManager.STREAM_NOTIFICATION, "Notification");

    companion object {
        fun fromRawStreamType(rawType: Int): AudioStreamType? {
            return entries.find { it.streamType == rawType }
        }
    }
}
