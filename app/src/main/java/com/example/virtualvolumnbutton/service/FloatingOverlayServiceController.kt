package com.example.virtualvolumnbutton.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton state controller tracking whether [FloatingOverlayService] is currently running.
 *
 * WHAT IT DOES:
 * - Exposes a reactive [StateFlow] (`isServiceRunning`) to UI components.
 *
 * WHY IT EXISTS:
 * - Allows UI elements (like the toggle switch on FloatingServiceCard) to reactively update
 *   their visual state whenever the service starts, stops, or is destroyed by the system.
 */
object FloatingOverlayServiceController {

    private val _isServiceRunning = MutableStateFlow(false)

    /**
     * Observable state flow indicating whether the floating overlay service is currently running on screen.
     */
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    /**
     * Updates the running status flag. Called inside service lifecycle callbacks.
     *
     * @param running `true` if service is running, `false` otherwise.
     */
    fun setRunning(running: Boolean) {
        _isServiceRunning.value = running
    }
}
