package com.example.virtualvolumnbutton.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
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
import androidx.core.content.ContextCompat
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
import com.example.virtualvolumnbutton.data.local.VolumeLockPrefs
import com.example.virtualvolumnbutton.data.repository.VolumeRepositoryImpl
import com.example.virtualvolumnbutton.engine.VolumeLockEngine
import com.example.virtualvolumnbutton.ui.components.FloatingOverlayContent
import com.example.virtualvolumnbutton.ui.theme.VirtualVolumnButtonTheme
import com.example.virtualvolumnbutton.util.PermissionHelper

/**
 * Android [Service] that maintains a Foreground Service process for background volume protection
 * and draws a moveable floating virtual volume button overlay on top of all other apps.
 *
 * WHAT IT DOES:
 * - Runs as a Foreground Service showing a persistent status bar notification.
 * - Dynamically attaches or removes the floating window view using Android's [WindowManager].
 * - When overlay is disabled but volume lock is active, keeps the Foreground Service process alive
 *   to guarantee CPU priority and wakefulness for [VolumeLockEngine].
 *
 * WHY IT EXISTS:
 * - Provides an accessible on-screen virtual volume control and prevents Android OS from killing
 *   background volume lock monitoring when the app is minimized.
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

    private var isOverlayWindowAttached: Boolean = false
    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private lateinit var params: WindowManager.LayoutParams

    private val app: VirtualVolumeApp?
        get() = application as? VirtualVolumeApp

    private val volumeLockPrefs: VolumeLockPrefs
        get() = app?.volumeLockPrefs ?: VolumeLockPrefs(this)

    private val volumeLockEngine: VolumeLockEngine
        get() = app?.volumeLockEngine ?: VolumeLockEngine(app?.volumeRepository ?: VolumeRepositoryImpl(this), volumeLockPrefs)

    override fun onCreate() {
        super.onCreate()
        // Step 1: Initialize lifecycle & state registry
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        // Step 2: Mark floating service as active in global controller
        FloatingOverlayServiceController.setRunning(true)

        // Step 3: Initialize window manager
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Step 4: Setup notification channel
        createNotificationChannel()

        // Step 5: Ensure background volume lock engine is active
        ensureVolumeLockEngineStarted()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf() // Stop service when explicit stop action received
                return START_NOT_STICKY
            }
            else -> {
                ensureVolumeLockEngineStarted()
                updateServiceState()
            }
        }
        return START_STICKY // Restart service if system kills it unexpectedly
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // Step 1: Remove overlay view from screen if attached
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
     * Updates notification content text dynamically based on active features
     * and manages overlay window attachment to WindowManager.
     */
    private fun updateServiceState() {
        val isLocked = volumeLockEngine.isLockActive.value || volumeLockPrefs.isMasterLocked
        val isOverlayEnabled = volumeLockPrefs.isOverlayEnabled
        val hasPermission = PermissionHelper.hasOverlayPermission(this)
        val isOverlayActive = isOverlayEnabled && hasPermission

        // Update notification content text based on active features
        val notificationText = when {
            isLocked && isOverlayActive -> "Volume Lock & Floating Overlay active"
            isLocked -> "Volume Lock active"
            isOverlayActive -> "Floating Overlay active"
            else -> "Volume Service active"
        }

        startForegroundServiceWithNotification(notificationText)

        // Attach overlay window if enabled and permitted; remove if disabled
        if (isOverlayActive) {
            if (!isOverlayWindowAttached || overlayView == null) {
                setupOverlayView()
            }
        } else {
            if (isOverlayWindowAttached || overlayView != null) {
                removeOverlayView()
            }
        }
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
                description = "Notification for volume lock and floating button overlay service"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    /**
     * Promotes this service to a Foreground Service with an ongoing notification.
     *
     * @param contentText Dynamic status notification message.
     */
    private fun startForegroundServiceWithNotification(contentText: String) {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Virtual Volume")
            .setContentText(contentText)
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100 // Default initial X position on screen
            y = 300 // Default initial Y position on screen
        }

        val repository = app?.volumeRepository ?: VolumeRepositoryImpl(this)
        val engine = app?.volumeLockEngine ?: VolumeLockEngine(repository, volumeLockPrefs)

        // Create ComposeView and bind Service lifecycle
        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)

            setContent {
                VirtualVolumnButtonTheme {
                    FloatingOverlayContent(
                        volumeRepository = repository,
                        volumeLockEngine = engine,
                        onDrag = { dx, dy ->
                            updateOverlayPosition(dx, dy) // Dragging gesture callback
                        },
                        onCloseService = {
                            volumeLockPrefs.isOverlayEnabled = false
                            syncServiceState(this@FloatingOverlayService)
                        }
                    )
                }
            }
        }

        // Add overlay ComposeView to system WindowManager
        try {
            windowManager.addView(overlayView, params)
            isOverlayWindowAttached = true
        } catch (e: Exception) {
            Log.e("FloatingOverlayService", "Error adding overlay view: ${e.message}")
            isOverlayWindowAttached = false
            overlayView = null
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
     * Removes the overlay view from the WindowManager if attached.
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
        isOverlayWindowAttached = false
    }

    /**
     * Ensures background volume lock engine is active.
     */
    private fun ensureVolumeLockEngineStarted() {
        val appInstance = application as? VirtualVolumeApp
        appInstance?.let {
            it.volumeLockEngine.start(it.applicationScope)
        }
    }

    companion object {
        const val ACTION_START = "com.example.virtualvolumnbutton.action.START_OVERLAY"
        const val ACTION_STOP = "com.example.virtualvolumnbutton.action.STOP_OVERLAY"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "floating_overlay_channel"

        /**
         * Synchronizes foreground service lifecycle based on active features.
         *
         * Starts the foreground service if volume lock is active OR floating overlay is enabled.
         * Stops the foreground service if neither feature is active.
         */
        fun syncServiceState(context: Context) {
            val app = context.applicationContext as? VirtualVolumeApp
            val prefs = app?.volumeLockPrefs ?: VolumeLockPrefs(context)
            val engine = app?.volumeLockEngine
            val isLocked = prefs.isMasterLocked || (engine?.isLockActive?.value == true)
            val isOverlayEnabled = prefs.isOverlayEnabled
            val shouldRun = isLocked || isOverlayEnabled

            val intent = Intent(context, FloatingOverlayService::class.java)
            if (shouldRun) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.stopService(intent)
            }
        }
    }
}
