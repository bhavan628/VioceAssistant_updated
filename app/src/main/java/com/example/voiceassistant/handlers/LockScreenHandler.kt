package com.example.voiceassistant.handlers

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context

/**
 * Locks the screen via Android's Device Admin API. Requires the user to have
 * approved this app as a device admin ahead of time (one-time setup via
 * MainActivity) — there is no way to lock the screen without it; Android blocks this
 * deliberately so apps can't do it silently/maliciously.
 */
object LockScreenHandler {

    fun handle(context: Context): String {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, LockScreenAdminReceiver::class.java)

        if (!dpm.isAdminActive(adminComponent)) {
            return "I need device admin permission to lock the screen — please grant it in settings"
        }

        return try {
            dpm.lockNow()
            "Locking the screen"
        } catch (e: SecurityException) {
            "I don't have permission to lock the screen"
        }
    }
}
