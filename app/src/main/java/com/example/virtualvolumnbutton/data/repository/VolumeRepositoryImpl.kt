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

class VolumeRepositoryImpl(
    context: Context
) : VolumeRepository {

    private val appContext: Context = context.applicationContext

    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val cachedVolumeLevels = IntArray(AudioStreamType.entries.size) { -1 }
    private val cachedVolumeStatesMap = EnumMap<AudioStreamType, VolumeStreamState>(AudioStreamType::class.java)

    private val _volumeStates = MutableStateFlow<Map<AudioStreamType, VolumeStreamState>>(emptyMap())
    override val volumeStates: StateFlow<Map<AudioStreamType, VolumeStreamState>> =
        _volumeStates.asStateFlow()

    init {
        refreshVolumeStates()
    }

    override fun getVolumeState(streamType: AudioStreamType): VolumeStreamState {
        val current = try {
            audioManager.getStreamVolume(streamType.streamType)
        } catch (e: Exception) {
            0
        }
        val max = try {
            audioManager.getStreamMaxVolume(streamType.streamType)
        } catch (e: Exception) {
            15
        }
        val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                audioManager.getStreamMinVolume(streamType.streamType)
            } catch (e: Exception) {
                0
            }
        } else {
            0
        }
        val isMuted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                audioManager.isStreamMute(streamType.streamType)
            } catch (e: Exception) {
                current == 0
            }
        } else {
            current == 0
        }

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

    override fun setVolume(streamType: AudioStreamType, level: Int, showUi: Boolean) {
        val currentState = getVolumeState(streamType)
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
        refreshVolumeStates()
    }

    override fun setMute(streamType: AudioStreamType, mute: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val direction = if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
            try {
                audioManager.adjustStreamVolume(streamType.streamType, direction, 0)
            } catch (e: Exception) {
                Log.e("VolumeRepository", "Error toggling mute for ${streamType.name}: ${e.message}")
            }
        } else {
            if (mute) {
                setVolume(streamType, 0, false)
            }
        }
        refreshVolumeStates()
    }

    override fun refreshVolumeStates() {
        val updatedMap = EnumMap<AudioStreamType, VolumeStreamState>(AudioStreamType::class.java)
        for (stream in AudioStreamType.entries) {
            val state = getVolumeState(stream)
            updatedMap[stream] = state
            cachedVolumeLevels[stream.ordinal] = state.currentVolume
        }
        _volumeStates.value = updatedMap
    }

    override fun observeVolumeChanges(): Flow<AudioStreamType> = callbackFlow {
        refreshVolumeStates()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == VOLUME_CHANGED_ACTION) {
                    val streamTypeRaw = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1)
                    val audioStreamType = AudioStreamType.fromRawStreamType(streamTypeRaw)
                    refreshVolumeStates()
                    if (audioStreamType != null) {
                        trySend(audioStreamType)
                    } else {
                        AudioStreamType.entries.forEach { trySend(it) }
                    }
                }
            }
        }

        val intentFilter = IntentFilter(VOLUME_CHANGED_ACTION)
        appContext.registerReceiver(receiver, intentFilter)

        val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                refreshVolumeStates()
                AudioStreamType.entries.forEach { trySend(it) }
            }
        }

        appContext.contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            contentObserver
        )

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
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
    }
}
