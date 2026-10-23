package com.example.virtualvolumnbutton

import android.app.Application
import com.example.virtualvolumnbutton.data.local.VolumeLockPrefs
import com.example.virtualvolumnbutton.data.model.AudioStreamType
import com.example.virtualvolumnbutton.data.repository.VolumeRepository
import com.example.virtualvolumnbutton.data.repository.VolumeRepositoryImpl
import com.example.virtualvolumnbutton.engine.VolumeLockEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class VirtualVolumeApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var volumeRepository: VolumeRepository
        private set

    lateinit var volumeLockEngine: VolumeLockEngine
        private set

    lateinit var volumeLockPrefs: VolumeLockPrefs
        private set

    override fun onCreate() {
        super.onCreate()
        volumeRepository = VolumeRepositoryImpl(this)
        volumeLockPrefs = VolumeLockPrefs(this)
        volumeLockEngine = VolumeLockEngine(volumeRepository, volumeLockPrefs)

        if (volumeLockPrefs.isMasterLocked) {
            val savedStreams = volumeLockPrefs.getLockedStreams()
            val savedVolumes = volumeLockPrefs.getLockedVolumes()
            volumeLockEngine.restoreLockState(
                streams = if (savedStreams.isEmpty()) AudioStreamType.entries.toSet() else savedStreams,
                volumes = savedVolumes
            )
            volumeLockEngine.start(applicationScope)
        }
    }
}
