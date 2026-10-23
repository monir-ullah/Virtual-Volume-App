package com.example.virtualvolumnbutton

import android.app.Application
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

    override fun onCreate() {
        super.onCreate()
        volumeRepository = VolumeRepositoryImpl(this)
        volumeLockEngine = VolumeLockEngine(volumeRepository)
        volumeLockEngine.start(applicationScope)
    }
}
