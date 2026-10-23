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

class VolumeLockTileService : TileService() {

    private var stateJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        val app = (applicationContext as? VirtualVolumeApp) ?: (application as? VirtualVolumeApp) ?: return
        updateTileState()

        stateJob?.cancel()
        stateJob = CoroutineScope(Dispatchers.Main).launch {
            app.volumeLockEngine.isLockActive.collect { isLocked ->
                updateTileState(isLocked)
            }
        }
    }

    override fun onStopListening() {
        stateJob?.cancel()
        stateJob = null
        super.onStopListening()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val app = (applicationContext as? VirtualVolumeApp) ?: (application as? VirtualVolumeApp) ?: return
        val engine = app.volumeLockEngine
        val prefs = app.volumeLockPrefs

        val currentLockState = engine.isLockActive.value || prefs.isMasterLocked
        val newLockState = !currentLockState

        if (newLockState) {
            val savedStreams = prefs.getLockedStreams()
            val savedVolumes = prefs.getLockedVolumes()
            engine.restoreLockState(
                streams = if (savedStreams.isEmpty()) AudioStreamType.entries.toSet() else savedStreams,
                volumes = savedVolumes
            )
            engine.start(app.applicationScope)
        } else {
            engine.disableLock()
        }

        prefs.isMasterLocked = newLockState
        if (newLockState) {
            prefs.saveLockedStreams(engine.lockedStreams.value)
            prefs.saveLockedVolumes(engine.lockedVolumes.value)
        }

        updateTileState(newLockState)
    }

    private fun updateTileState(isLocked: Boolean? = null) {
        val tile = qsTile ?: return
        val app = (applicationContext as? VirtualVolumeApp) ?: (application as? VirtualVolumeApp)
        val locked = isLocked ?: app?.volumeLockEngine?.isLockActive?.value ?: app?.volumeLockPrefs?.isMasterLocked ?: false

        tile.state = if (locked) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (locked) "Locked" else "Unlocked"
        }
        tile.updateTile()
    }
}
