package com.example.voiceassistant.handlers

import android.app.admin.DeviceAdminReceiver

/**
 * Required boilerplate for Android's Device Admin API. Locking the screen
 * programmatically (DevicePolicyManager.lockNow()) is NOT possible without an app
 * being registered as a device admin — there's no regular runtime permission for it,
 * this is Android's deliberate design so apps can't lock a user's phone silently.
 * The user has to explicitly approve this via a system screen (wired up in
 * MainActivity), same category of step as the battery-optimization exemption.
 */
class LockScreenAdminReceiver : DeviceAdminReceiver()
