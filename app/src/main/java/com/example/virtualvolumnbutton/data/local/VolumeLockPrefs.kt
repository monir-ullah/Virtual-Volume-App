package com.example.virtualvolumnbutton.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.virtualvolumnbutton.data.model.AudioStreamType

class VolumeLockPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isMasterLocked: Boolean
        get() = prefs.getBoolean(KEY_IS_MASTER_LOCKED, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_MASTER_LOCKED, value).apply()

    fun getLockedStreams(): Set<AudioStreamType> {
        val streamNames = prefs.getStringSet(KEY_LOCKED_STREAMS, null)
            ?: return AudioStreamType.entries.toSet()
        return streamNames.mapNotNull { name ->
            try {
                AudioStreamType.valueOf(name)
            } catch (e: Exception) {
                null
            }
        }.toSet()
    }

    fun saveLockedStreams(streams: Set<AudioStreamType>) {
        val streamNames = streams.map { it.name }.toSet()
        prefs.edit().putStringSet(KEY_LOCKED_STREAMS, streamNames).apply()
    }

    fun getLockedVolumes(): Map<AudioStreamType, Int> {
        val volumesMap = mutableMapOf<AudioStreamType, Int>()
        for (stream in AudioStreamType.entries) {
            val level = prefs.getInt(KEY_PREFIX_VOLUME + stream.name, -1)
            if (level != -1) {
                volumesMap[stream] = level
            }
        }
        return volumesMap
    }

    fun saveLockedVolumes(volumes: Map<AudioStreamType, Int>) {
        val editor = prefs.edit()
        for ((stream, level) in volumes) {
            editor.putInt(KEY_PREFIX_VOLUME + stream.name, level)
        }
        editor.apply()
    }

    fun saveLockState(
        isMasterLocked: Boolean,
        lockedStreams: Set<AudioStreamType>,
        lockedVolumes: Map<AudioStreamType, Int>
    ) {
        val editor = prefs.edit()
        editor.putBoolean(KEY_IS_MASTER_LOCKED, isMasterLocked)
        editor.putStringSet(KEY_LOCKED_STREAMS, lockedStreams.map { it.name }.toSet())
        for ((stream, level) in lockedVolumes) {
            editor.putInt(KEY_PREFIX_VOLUME + stream.name, level)
        }
        editor.apply()
    }

    fun clearLockState() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "volume_lock_prefs"
        private const val KEY_IS_MASTER_LOCKED = "is_master_locked"
        private const val KEY_LOCKED_STREAMS = "locked_streams"
        private const val KEY_PREFIX_VOLUME = "locked_volume_"
    }
}
