package com.example.virtualvolumnbutton.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.virtualvolumnbutton.data.model.AudioStreamType

/**
 * Storage manager for persisting volume lock configuration to disk using [SharedPreferences].
 *
 * WHAT IT DOES:
 * - Saves and retrieves whether master volume lock is active.
 * - Saves and retrieves which specific audio streams (Media, Ring, Alarm, Notification) are locked.
 * - Saves and retrieves the target locked volume integer levels for each locked stream.
 *
 * WHY IT EXISTS:
 * - When the user turns on volume lock or restarts their device, the app needs to remember
 *   what settings were active so volume protection resumes automatically.
 *
 * HOW IT WORKS:
 * - Uses Android's private [SharedPreferences] file named `"volume_lock_prefs"`.
 * - Reads values synchronously from memory-cached preferences.
 * - Writes values asynchronously using [SharedPreferences.Editor.apply] to ensure smooth performance
 *   without blocking the main UI thread.
 */
class VolumeLockPrefs(context: Context) {

    // Private handle to the app's SharedPreferences storage file
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Gets or sets whether the master volume lock is currently active.
     *
     * - `get()`: Reads boolean flag from preferences (defaults to `false` if not set).
     * - `set()`: Saves boolean flag asynchronously to preferences.
     */
    var isMasterLocked: Boolean
        get() = prefs.getBoolean(KEY_IS_MASTER_LOCKED, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_MASTER_LOCKED, value).apply()

    /**
     * Gets or sets whether the floating overlay button is currently enabled by user preference.
     *
     * - `get()`: Reads boolean flag from preferences (defaults to `false` if not set).
     * - `set()`: Saves boolean flag asynchronously to preferences.
     */
    var isOverlayEnabled: Boolean
        get() = prefs.getBoolean(KEY_IS_OVERLAY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_OVERLAY_ENABLED, value).apply()

    /**
     * Retrieves the set of currently locked audio streams.
     *
     * @return A set of [AudioStreamType] enums. If none are saved, defaults to all available streams.
     */
    fun getLockedStreams(): Set<AudioStreamType> {
        // Retrieve string names saved in preferences (e.g., ["MEDIA", "RING"])
        val streamNames = prefs.getStringSet(KEY_LOCKED_STREAMS, null)
            ?: return AudioStreamType.entries.toSet()

        // Safely map string names back to AudioStreamType enum values
        return streamNames.mapNotNull { name ->
            try {
                AudioStreamType.valueOf(name)
            } catch (e: Exception) {
                null // Ignore any invalid/unknown stream names
            }
        }.toSet()
    }

    /**
     * Saves the set of locked streams to preferences disk storage.
     *
     * @param streams Set of [AudioStreamType] enums to be saved.
     */
    fun saveLockedStreams(streams: Set<AudioStreamType>) {
        val streamNames = streams.map { it.name }.toSet()
        prefs.edit().putStringSet(KEY_LOCKED_STREAMS, streamNames).apply()
    }

    /**
     * Retrieves the map of locked volume levels for each audio stream.
     *
     * @return Map where key is [AudioStreamType] and value is the target volume level integer.
     */
    fun getLockedVolumes(): Map<AudioStreamType, Int> {
        val volumesMap = mutableMapOf<AudioStreamType, Int>()
        for (stream in AudioStreamType.entries) {
            // Read saved volume level using key like "locked_volume_MEDIA"
            val level = prefs.getInt(KEY_PREFIX_VOLUME + stream.name, -1)
            if (level != -1) {
                volumesMap[stream] = level
            }
        }
        return volumesMap
    }

    /**
     * Saves target volume levels for each stream to preferences storage.
     *
     * @param volumes Map of [AudioStreamType] to volume integer levels.
     */
    fun saveLockedVolumes(volumes: Map<AudioStreamType, Int>) {
        val editor = prefs.edit()
        for ((stream, level) in volumes) {
            editor.putInt(KEY_PREFIX_VOLUME + stream.name, level)
        }
        editor.apply()
    }

    /**
     * Atomically saves the complete lock state in a single batch editor transaction.
     *
     * @param isMasterLocked Master lock toggle state.
     * @param lockedStreams Set of locked streams.
     * @param lockedVolumes Map of target volume levels for each stream.
     */
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

    /**
     * Clears all saved volume lock preferences from disk storage.
     */
    fun clearLockState() {
        prefs.edit().clear().apply()
    }

    companion object {
        // Key constants used for SharedPreferences lookups
        private const val PREFS_NAME = "volume_lock_prefs"
        private const val KEY_IS_MASTER_LOCKED = "is_master_locked"
        private const val KEY_IS_OVERLAY_ENABLED = "is_overlay_enabled"
        private const val KEY_LOCKED_STREAMS = "locked_streams"
        private const val KEY_PREFIX_VOLUME = "locked_volume_"
    }
}
