package com.example.virtualvolumnbutton.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.virtualvolumnbutton.VirtualVolumeApp
import com.example.virtualvolumnbutton.data.model.AudioStreamType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Android [TileService] exposing a Quick Settings system tile in the device notification shade.
 *
 * WHAT IT DOES:
 * - Adds a "Volume Lock" quick toggle tile in Android's status bar notification shade.
 * - Shows visual tile state (`STATE_ACTIVE` when volume lock is ON, `STATE_INACTIVE` when OFF).
 * - Allows toggling the master volume lock on/off with a single tap from anywhere in Android.
 *
 * WHY IT EXISTS:
 * - Provides immediate convenience to lock or unlock volume without having to open the main app.
 *
 * HOW IT WORKS:
 * - Extends Android's [TileService].
 * - In [onStartListening], subscribes to [com.example.virtualvolumnbutton.engine.VolumeLockEngine.isLockActive]
 *   to update the tile UI dynamically whenever lock status changes.
 * - In [onClick], toggles master lock state, updates preferences, refreshes tile UI, and syncs foreground service state.
 */
class VolumeLockTileService : TileService() {

    // Background job for listening to lock state changes while Quick Settings panel is open
    private var stateJob: Job? = null

    /**
     * Called when the Quick Settings tile becomes visible to the user in the notification shade.
     */
    override fun onStartListening() {
        super.onStartListening()
        val app = (applicationContext as? VirtualVolumeApp) ?: (application as? VirtualVolumeApp) ?: return
        
        // Update tile visual appearance immediately
        updateTileState()

        // Cancel previous collector job if active
        stateJob?.cancel()
        // Collect real-time volume lock state changes to keep tile UI perfectly synced
        stateJob = CoroutineScope(Dispatchers.Main).launch {
            app.volumeLockEngine.isLockActive.collect { isLocked ->
                updateTileState(isLocked)
            }
        }
    }

    /**
     * Called when the Quick Settings tile goes out of view / user closes notification shade.
     */
    override fun onStopListening() {
        stateJob?.cancel() // Stop listening to save battery
        stateJob = null
        super.onStopListening()
    }

    /**
     * Called when the user adds this tile to their active Quick Settings grid.
     */
    override fun onTileAdded() {
        super.onTileAdded()
        updateTileState()
    }

    /**
     * Called when the user taps the Quick Settings tile.
     */
    override fun onClick() {
        super.onClick()
        val app = (applicationContext as? VirtualVolumeApp) ?: (application as? VirtualVolumeApp) ?: return
        val engine = app.volumeLockEngine
        val prefs = app.volumeLockPrefs

        // Determine current lock state and flip it
        val currentLockState = engine.isLockActive.value || prefs.isMasterLocked
        val newLockState = !currentLockState

        if (newLockState) {
            // Restore saved streams and start volume protection engine
            val savedStreams = prefs.getLockedStreams()
            val savedVolumes = prefs.getLockedVolumes()
            engine.restoreLockState(
                streams = if (savedStreams.isEmpty()) AudioStreamType.entries.toSet() else savedStreams,
                volumes = savedVolumes
            )
            engine.start(app.applicationScope)
        } else {
            // Disable volume protection engine
            engine.disableLock()
        }

        // Save new toggle state to preferences disk storage
        prefs.isMasterLocked = newLockState
        if (newLockState) {
            prefs.saveLockedStreams(engine.lockedStreams.value)
            prefs.saveLockedVolumes(engine.lockedVolumes.value)
        }

        // Refresh tile UI state instantly
        updateTileState(newLockState)

        // Synchronize unified foreground service state
        FloatingOverlayService.syncServiceState(applicationContext)
    }

    /**
     * Updates the Quick Settings Tile icon, active state, and subtitle text.
     *
     * @param isLocked Explicit boolean lock state, or `null` to query current state.
     */
    private fun updateTileState(isLocked: Boolean? = null) {
        val tile = qsTile ?: return
        val app = (applicationContext as? VirtualVolumeApp) ?: (application as? VirtualVolumeApp)
        val locked = isLocked ?: app?.volumeLockEngine?.isLockActive?.value ?: app?.volumeLockPrefs?.isMasterLocked ?: false

        // Tile.STATE_ACTIVE highlights the tile in the system theme color
        tile.state = if (locked) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        // Set subtitle string on Android 10 (API 29) and higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (locked) "Locked" else "Unlocked"
        }
        // Publish tile changes to system UI
        tile.updateTile()
    }
}
