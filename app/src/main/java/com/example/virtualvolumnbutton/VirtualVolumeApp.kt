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

/**
 * Custom Application class for the Virtual Volume Button app.
 *
 * WHAT IT DOES:
 * - Serves as the global entry point for the app process when Android launches it.
 * - Instantiates and holds shared singleton instances of core application components:
 *   [VolumeRepository], [VolumeLockPrefs], and [VolumeLockEngine].
 * - Automatically restores any previously active volume lock settings from disk when the app starts.
 *
 * WHY IT EXISTS:
 * - By creating singletons here at the Application level, all UI screens, background services
 *   (like floating overlays), and Quick Settings tiles share the exact same volume engine
 *   and lock state without duplicating logic or creating inconsistent states.
 *
 * HOW IT WORKS:
 * - Android creates an instance of this class before any Activity or Service starts.
 * - In [onCreate], it initializes repositories and preference managers.
 * - It checks if the master volume lock was enabled in previous sessions; if yes, it restores
 *   the locked stream levels and starts background volume monitoring immediately.
 */
class VirtualVolumeApp : Application() {

    /**
     * Long-lived [CoroutineScope] tied directly to the lifetime of the application process.
     *
     * - Uses [SupervisorJob] so that if one background task fails or throws an exception,
     *   it does NOT cancel other unrelated application background jobs.
     * - Uses [Dispatchers.Default] for CPU-optimized background execution.
     */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Repository responsible for interacting with Android's system [android.media.AudioManager].
     */
    lateinit var volumeRepository: VolumeRepository
        private set

    /**
     * Core engine that monitors volume changes and restores locked volume levels when altered.
     */
    lateinit var volumeLockEngine: VolumeLockEngine
        private set

    /**
     * Storage helper for persisting volume lock preferences to [android.content.SharedPreferences].
     */
    lateinit var volumeLockPrefs: VolumeLockPrefs
        private set

    /**
     * Called when the application process is starting up.
     * This initializes central services and restores background lock monitoring if enabled.
     */
    override fun onCreate() {
        super.onCreate()

        // Step 1: Initialize singletons with the application context
        volumeRepository = VolumeRepositoryImpl(this)
        volumeLockPrefs = VolumeLockPrefs(this)
        volumeLockEngine = VolumeLockEngine(volumeRepository, volumeLockPrefs)

        // Step 2: Check if volume lock was active before the app was closed or rebooted
        if (volumeLockPrefs.isMasterLocked) {
            val savedStreams = volumeLockPrefs.getLockedStreams()
            val savedVolumes = volumeLockPrefs.getLockedVolumes()

            // Step 3: Restore locked streams & levels into the engine memory
            volumeLockEngine.restoreLockState(
                streams = if (savedStreams.isEmpty()) AudioStreamType.entries.toSet() else savedStreams,
                volumes = savedVolumes
            )

            // Step 4: Begin real-time background volume monitoring
            volumeLockEngine.start(applicationScope)
        }
    }
}
