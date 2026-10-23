package com.example.virtualvolumnbutton.data.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.example.virtualvolumnbutton.data.model.AudioStreamType
import com.example.virtualvolumnbutton.data.model.VolumeStreamState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import java.util.EnumMap

/**
 * Concrete implementation of [VolumeRepository] interacting directly with Android system services.
 *
 * WHAT IT DOES:
 * - Interacts with Android's system [AudioManager] service to read/write volume levels and mute states.
 * - Registers system listeners ([BroadcastReceiver] and [ContentObserver]) to catch when physical hardware
 *   volume buttons are pressed or when other apps change sound levels.
 * - Publishes updated volume states as observable reactive Kotlin [StateFlow] and [Flow] streams.
 *
 * WHY IT EXISTS:
 * - Provides a unified, safe abstraction over Android's audio framework APIs across different API levels
 *   (handling Android 6.0 Marshmallow, Android 9.0 Pie, and modern Android versions seamlessly).
 *
 * HOW IT WORKS:
 * - Queries [AudioManager.getStreamVolume], [AudioManager.getStreamMaxVolume], and [AudioManager.getStreamMinVolume].
 * - Listens for system intent `android.media.VOLUME_CHANGED_ACTION` via a registered BroadcastReceiver.
 * - Listens for system settings database changes via a ContentObserver on [Settings.System.CONTENT_URI].
 * - When changes occur, updates internal state flows so subscribers (UI and VolumeLockEngine) react immediately.
 */
class VolumeRepositoryImpl(
    context: Context
) : VolumeRepository {

    // Always use applicationContext to avoid leaking Activity contexts
    private val appContext: Context = context.applicationContext

    // Obtain Android's system audio manager service
    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Low-level primitive array and map for fast volume state lookups
    private val cachedVolumeLevels = IntArray(AudioStreamType.entries.size) { -1 }
    private val cachedVolumeStatesMap = EnumMap<AudioStreamType, VolumeStreamState>(AudioStreamType::class.java)

    // Reactive StateFlow exposing the current state map for all audio streams
    private val _volumeStates = MutableStateFlow<Map<AudioStreamType, VolumeStreamState>>(emptyMap())
    override val volumeStates: StateFlow<Map<AudioStreamType, VolumeStreamState>> =
        _volumeStates.asStateFlow()

    init {
        // Fetch initial volume levels upon repository creation
        refreshVolumeStates()
    }

    /**
     * Reads the current volume snapshot for a given stream from Android's [AudioManager].
     *
     * HOW IT WORKS:
     * - Reads current volume level and maximum supported volume level.
     * - Handles min volume safely (min volume API was introduced in Android 9 / API 28).
     * - Handles mute check safely (isStreamMute API was introduced in Android 6 / API 23).
     *
     * @param streamType Target stream (Media, Ring, Alarm, Notification).
     * @return [VolumeStreamState] object populated with device system values.
     */
    override fun getVolumeState(streamType: AudioStreamType): VolumeStreamState {
        // Read current volume level with fallback safety
        val current = try {
            audioManager.getStreamVolume(streamType.streamType)
        } catch (e: Exception) {
            0
        }

        // Read maximum volume level with fallback safety
        val max = try {
            audioManager.getStreamMaxVolume(streamType.streamType)
        } catch (e: Exception) {
            15
        }

        // Read minimum volume level (Android 9.0+ / API 28+)
        val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                audioManager.getStreamMinVolume(streamType.streamType)
            } catch (e: Exception) {
                0
            }
        } else {
            0
        }

        // Read mute status (Android 6.0+ / API 23+)
        val isMuted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                audioManager.isStreamMute(streamType.streamType)
            } catch (e: Exception) {
                current == 0
            }
        } else {
            current == 0
        }

        // Update local array cache
        cachedVolumeLevels[streamType.ordinal] = current

        val state = VolumeStreamState(
            streamType = streamType,
            currentVolume = current,
            maxVolume = max,
            minVolume = min,
            isMuted = isMuted
        )
        cachedVolumeStatesMap[streamType] = state
        return state
    }

    /**
     * Sets a new volume level for the specified stream on the device.
     *
     * @param streamType Target stream.
     * @param level New volume level to set.
     * @param showUi If `true`, Android will display its floating native system volume slider UI.
     */
    override fun setVolume(streamType: AudioStreamType, level: Int, showUi: Boolean) {
        val currentState = getVolumeState(streamType)
        // Clamp volume level within valid min..max bounds for this stream
        val clampedLevel = level.coerceIn(currentState.minVolume, currentState.maxVolume)
        val flags = if (showUi) AudioManager.FLAG_SHOW_UI else 0

        try {
            audioManager.setStreamVolume(streamType.streamType, clampedLevel, flags)
            cachedVolumeLevels[streamType.ordinal] = clampedLevel
        } catch (e: SecurityException) {
            Log.e("VolumeRepository", "SecurityException setting volume for ${streamType.name}: ${e.message}")
        } catch (e: Exception) {
            Log.e("VolumeRepository", "Error setting volume for ${streamType.name}: ${e.message}")
        }
        // Refresh local flows immediately after setting volume
        refreshVolumeStates()
    }

    /**
     * Toggles mute state for a specified stream.
     *
     * @param streamType Target stream.
     * @param mute `true` to mute, `false` to unmute.
     */
    override fun setMute(streamType: AudioStreamType, mute: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val direction = if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
            try {
                audioManager.adjustStreamVolume(streamType.streamType, direction, 0)
            } catch (e: Exception) {
                Log.e("VolumeRepository", "Error toggling mute for ${streamType.name}: ${e.message}")
            }
        } else {
            // Fallback for older Android versions
            if (mute) {
                setVolume(streamType, 0, false)
            }
        }
        refreshVolumeStates()
    }

    /**
     * Re-queries all stream volumes from system [AudioManager] and updates the state flow.
     */
    override fun refreshVolumeStates() {
        val updatedMap = EnumMap<AudioStreamType, VolumeStreamState>(AudioStreamType::class.java)
        for (stream in AudioStreamType.entries) {
            val state = getVolumeState(stream)
            updatedMap[stream] = state
            cachedVolumeLevels[stream.ordinal] = state.currentVolume
        }
        _volumeStates.value = updatedMap
    }

    /**
     * Creates a cold [Flow] using [callbackFlow] to stream real-time system hardware volume button events.
     *
     * HOW IT WORKS:
     * 1. Registers a [BroadcastReceiver] listening for `android.media.VOLUME_CHANGED_ACTION`.
     *    This receiver fires whenever physical volume buttons are pressed on the phone.
     * 2. Registers a [ContentObserver] listening on [Settings.System.CONTENT_URI].
     *    This observer fires whenever volume settings in the system database are updated.
     * 3. Sends updated [AudioStreamType] events down the channel using `trySend()`.
     * 4. Cleans up and unregisters both listeners in `awaitClose` when coroutine collection stops.
     */
    override fun observeVolumeChanges(): Flow<AudioStreamType> = callbackFlow {
        refreshVolumeStates()

        // Listener 1: BroadcastReceiver for hardware volume button events
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == VOLUME_CHANGED_ACTION) {
                    val streamTypeRaw = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1)
                    val audioStreamType = AudioStreamType.fromRawStreamType(streamTypeRaw)
                    refreshVolumeStates()
                    if (audioStreamType != null) {
                        trySend(audioStreamType)
                    } else {
                        // If specific stream unknown, notify for all streams
                        AudioStreamType.entries.forEach { trySend(it) }
                    }
                }
            }
        }

        // Register the BroadcastReceiver with Android
        val intentFilter = IntentFilter(VOLUME_CHANGED_ACTION)
        appContext.registerReceiver(receiver, intentFilter)

        // Listener 2: ContentObserver for system settings database changes
        val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                refreshVolumeStates()
                AudioStreamType.entries.forEach { trySend(it) }
            }
        }

        // Register the ContentObserver with ContentResolver
        appContext.contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            contentObserver
        )

        // Clean up resources when the Flow collector stops listening
        awaitClose {
            try {
                appContext.unregisterReceiver(receiver)
            } catch (e: Exception) {
                Log.w("VolumeRepository", "Error unregistering receiver: ${e.message}")
            }
            try {
                appContext.contentResolver.unregisterContentObserver(contentObserver)
            } catch (e: Exception) {
                Log.w("VolumeRepository", "Error unregistering contentObserver: ${e.message}")
            }
        }
    }

    companion object {
        // System intent constants for listening to volume button key events
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
    }
}
