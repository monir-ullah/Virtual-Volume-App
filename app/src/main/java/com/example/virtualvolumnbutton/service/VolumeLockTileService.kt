package com.example.virtualvolumnbutton.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.virtualvolumnbutton.VirtualVolumeApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class VolumeLockTileService : TileService() {

    private var stateJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
        val app = (application as? VirtualVolumeApp) ?: return
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
        val app = (application as? VirtualVolumeApp) ?: return
        val currentLockState = app.volumeLockEngine.isLockActive.value
        if (currentLockState) {
            app.volumeLockEngine.disableLock()
        } else {
            app.volumeLockEngine.enableLock()
        }
        updateTileState(!currentLockState)
    }

    private fun updateTileState(isLocked: Boolean? = null) {
        val tile = qsTile ?: return
        val app = application as? VirtualVolumeApp
        val locked = isLocked ?: app?.volumeLockEngine?.isLockActive?.value ?: false

        tile.state = if (locked) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (locked) "Locked" else "Unlocked"
        }
        tile.updateTile()
    }
}
