package com.example.virtualvolumnbutton.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.virtualvolumnbutton.MainActivity
import com.example.virtualvolumnbutton.VirtualVolumeApp
import com.example.virtualvolumnbutton.data.repository.VolumeRepositoryImpl
import com.example.virtualvolumnbutton.engine.VolumeLockEngine
import com.example.virtualvolumnbutton.ui.components.FloatingOverlayContent
import com.example.virtualvolumnbutton.ui.theme.VirtualVolumnButtonTheme
import com.example.virtualvolumnbutton.util.PermissionHelper

/**
 * Android [Service] that draws a moveable floating virtual volume button overlay on top of all other apps.
 *
 * WHAT IT DOES:
 * - Runs as a Foreground Service showing a persistent status bar notification ("Virtual Volume Overlay").
 * - Inflates a floating window view using Android's [WindowManager] system service.
 * - Embeds modern Jetpack Compose UI ([FloatingOverlayContent]) inside the window overlay.
 * - Allows dragging the floating button across the screen and adjusting volume levels without switching apps.
 *
 * WHY IT EXISTS:
 * - Provides an accessible on-screen virtual volume control for users with broken hardware volume buttons
 *   or users who prefer touching on-screen controls while gaming, watching videos, or reading.
 *
 * HOW IT WORKS:
 * - Obtains [WindowManager] and sets layout parameters (`TYPE_APPLICATION_OVERLAY`, `FLAG_NOT_FOCUSABLE`).
 * - Implements [LifecycleOwner] and [SavedStateRegistryOwner] to properly attach Jetpack Compose views
 *   to a Service lifecycle without throwing runtime crashes.
 * - Updates window parameters (`params.x`, `params.y`) dynamically on drag gestures.
 */
class FloatingOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    // Lifecycle owner implementation for Compose integration in a Service
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    // Saved state registry controller implementation for Compose integration
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private lateinit var params: WindowManager.LayoutParams

    override fun onCreate() {
        super.onCreate()
        // Step 1: Initialize lifecycle & state registry
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        // Step 2: Mark floating service as active in global controller
        FloatingOverlayServiceController.setRunning(true)

        // Step 3: Setup notification channel & start foreground service
        createNotificationChannel()
        startForegroundServiceWithNotification()

        // Step 4: Ensure background volume lock engine is active
        ensureVolumeLockEngineStarted()

        // Step 5: Draw floating overlay on screen
        setupOverlayView()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf() // Stop service when explicit stop action received
                return START_NOT_STICKY
            }
            else -> {
                ensureVolumeLockEngineStarted()
                if (overlayView == null) {
                    setupOverlayView()
                }
            }
        }
        return START_STICKY // Restart service if system kills it unexpectedly
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // Step 1: Remove overlay view from screen
        removeOverlayView()

        // Step 2: Teardown lifecycle
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

        // Step 3: Update global running flag
        FloatingOverlayServiceController.setRunning(false)
        super.onDestroy()
    }

    /**
     * Creates a notification channel required for foreground services on Android 8.0 (API 26) and above.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating Volume Service",
                NotificationManager.IMPORTANCE_LOW // Low priority avoids annoying sound alerts
            ).apply {
                description = "Notification for virtual volume button floating overlay service"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    /**
     * Promotes this service to a Foreground Service with an ongoing notification.
     */
    private fun startForegroundServiceWithNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Virtual Volume Overlay")
            .setContentText("Touch volume controls active on screen")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // Android 14+ requires foreground service types
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Configures WindowManager parameters and attaches ComposeView overlay to screen.
     */
    private fun setupOverlayView() {
        if (overlayView != null) return
        if (!PermissionHelper.hasOverlayPermission(this)) {
            Log.w("FloatingOverlayService", "Cannot show overlay: Missing overlay permission")
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Select correct overlay window type based on Android OS version
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Configure WindowManager LayoutParams for floating touch overlay
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            // FLAG_NOT_FOCUSABLE: Allows touches outside the floating widget to reach underlying apps
            // FLAG_LAYOUT_IN_SCREEN: Allows placing overlay anywhere on screen
            // FLAG_NOT_TOUCH_MODAL: Prevents overlay from capturing touches outside its bounds
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100 // Default initial X position on screen
            y = 300 // Default initial Y position on screen
        }

        // Retrieve shared application instances
        val app = application as? VirtualVolumeApp
        val volumeRepository = app?.volumeRepository ?: VolumeRepositoryImpl(this)
        val volumeLockEngine = app?.volumeLockEngine ?: VolumeLockEngine(volumeRepository)
        app?.let {
            it.volumeLockEngine.start(it.applicationScope)
        }

        // Create ComposeView and bind Service lifecycle
        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)

            setContent {
                VirtualVolumnButtonTheme {
                    FloatingOverlayContent(
                        volumeRepository = volumeRepository,
                        volumeLockEngine = volumeLockEngine,
                        onDrag = { dx, dy ->
                            updateOverlayPosition(dx, dy) // Dragging gesture callback
                        },
                        onCloseService = {
                            stopSelf() // Close floating service button
                        }
                    )
                }
            }
        }

        // Add overlay ComposeView to system WindowManager
        try {
            windowManager.addView(overlayView, params)
        } catch (e: Exception) {
            Log.e("FloatingOverlayService", "Error adding overlay view: ${e.message}")
        }
    }

    /**
     * Updates the (X, Y) coordinates of the floating button view on drag gestures.
     * Keeps the overlay bounded safely within display screen dimensions.
     *
     * @param dx Delta drag distance on X axis.
     * @param dy Delta drag distance on Y axis.
     */
    private fun updateOverlayPosition(dx: Float, dy: Float) {
        val view = overlayView ?: return
        params.x += dx.toInt()
        params.y += dy.toInt()

        // Bound overlay position within physical display metrics
        val metrics = resources.displayMetrics
        val maxX = (metrics.widthPixels - view.width).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - view.height).coerceAtLeast(0)

        params.x = params.x.coerceIn(0, maxX)
        params.y = params.y.coerceIn(0, maxY)

        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.e("FloatingOverlayService", "Error updating overlay layout: ${e.message}")
        }
    }

    /**
     * Removes the overlay view from the WindowManager when service stops.
     */
    private fun removeOverlayView() {
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                Log.e("FloatingOverlayService", "Error removing overlay view: ${e.message}")
            }
        }
        overlayView = null
    }

    /**
     * Ensures background volume lock engine is active.
     */
    private fun ensureVolumeLockEngineStarted() {
        val app = application as? VirtualVolumeApp
        app?.let {
            it.volumeLockEngine.start(it.applicationScope)
        }
    }

    companion object {
        const val ACTION_START = "com.example.virtualvolumnbutton.action.START_OVERLAY"
        const val ACTION_STOP = "com.example.virtualvolumnbutton.action.STOP_OVERLAY"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "floating_overlay_channel"
    }
}
