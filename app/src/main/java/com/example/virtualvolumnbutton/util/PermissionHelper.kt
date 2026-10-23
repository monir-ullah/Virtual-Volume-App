package com.example.virtualvolumnbutton.util

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Extension function unwrapping a [Context] hierarchy to locate the host [Activity] instance if present.
 *
 * @return Host [Activity] or `null` if the context does not wrap an Activity.
 */
fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Utility helper object checking and creating intents for special Android permissions.
 *
 * WHAT IT DOES:
 * - Checks if the app has "Display over other apps" overlay draw permission (`SYSTEM_ALERT_WINDOW`).
 * - Checks if the app has Notification Policy Access (Do Not Disturb access to alter ringer streams).
 * - Creates system settings intents directing the user straight to permission grant screens.
 *
 * WHY IT EXISTS:
 * - Android considers drawing overlay windows and altering Do Not Disturb status as high-privilege operations.
 * - These permissions cannot be granted via simple dialogs; the user must manually toggle them in System Settings.
 */
object PermissionHelper {

    /**
     * Checks if the app is authorized to draw floating overlay windows over other applications.
     *
     * @param context Application or Activity context.
     * @return `true` if overlay permission is granted (or if running on API < 23 where permission was implicitly granted).
     */
    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Creates an [Intent] launching Android's system settings screen for "Display over other apps".
     *
     * @param context Context used to retrieve package name.
     * @return Intent targeting [Settings.ACTION_MANAGE_OVERLAY_PERMISSION].
     */
    fun getOverlayPermissionIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Checks if the app has Notification Policy Access to modify Ringer and Notification volumes during Do Not Disturb.
     *
     * @param context Application context.
     * @return `true` if policy access is granted.
     */
    fun hasNotificationPolicyAccess(context: Context): Boolean {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        return notificationManager?.isNotificationPolicyAccessGranted ?: true
    }

    /**
     * Creates an [Intent] launching Android's system settings screen for Notification Policy Access.
     *
     * @return Intent targeting [Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS].
     */
    fun getNotificationPolicyIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
