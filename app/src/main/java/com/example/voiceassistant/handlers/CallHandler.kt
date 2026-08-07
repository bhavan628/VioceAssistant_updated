package com.example.voiceassistant.handlers

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat

/**
 * Handler 7/8: call.
 * Looks up the spoken contact name via ContactLookup and places a call directly using
 * Intent.ACTION_CALL (requires CALL_PHONE — a dangerous-level permission the user must
 * grant; without it this falls back to ACTION_DIAL, which opens the dialer pre-filled
 * but requires the user to tap the call button themselves).
 */
object CallHandler {

    fun handle(context: Context, remainder: String): String {
        if (!hasPermission(context, Manifest.permission.READ_CONTACTS)) {
            return "I need contacts permission to place calls"
        }

        val contactName = remainder.trim()
        if (contactName.isBlank()) return "Who would you like to call?"

        val contact = ContactLookup.find(context, contactName)
            ?: return "I couldn't find a contact named $contactName"

        val hasCallPermission = hasPermission(context, Manifest.permission.CALL_PHONE)
        val action = if (hasCallPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL

        val intent = Intent(action, Uri.parse("tel:${contact.phoneNumber}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            if (hasCallPermission) {
                "Calling ${contact.name}"
            } else {
                // Placing the call itself is out of the assistant's control here —
                // ACTION_DIAL always shows the dialer UI (another action-needs-it case).
                "Opening dialer for ${contact.name} — I don't have permission to call directly"
            }
        } catch (e: Exception) {
            "Couldn't start a call to ${contact.name}"
        }
    }

    private fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
