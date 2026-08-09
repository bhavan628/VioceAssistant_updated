package com.example.voiceassistant.handlers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

/**
 * Handler 6/8: message.
 * Parses "message <contact> <that|saying> <text>" (or just "<contact> <text>" if no
 * separator word is spoken), looks the contact up via ContactLookup, and sends the
 * text via SMS by default, or via a WhatsApp intent if the command mentioned WhatsApp.
 *
 * Needs READ_CONTACTS + SEND_SMS (already in the manifest / requested in
 * MainActivity) — both are dangerous-level runtime permissions, so this handler
 * double-checks they're actually granted before doing anything, since a user can
 * revoke a permission after granting it once.
 */
object MessageHandler {

    private val separators = listOf(" that ", " saying ", " telling them ", " tell them ")

    fun handle(context: Context, remainder: String): String {
        if (!hasPermission(context, Manifest.permission.READ_CONTACTS) ||
            !hasPermission(context, Manifest.permission.SEND_SMS)
        ) {
            return "I need contacts and SMS permission to send messages"
        }

        val (contactName, messageText) = splitNameAndMessage(remainder)
            ?: return "Who should I message, and what should it say?"

        val contact = ContactLookup.find(context, contactName)
            ?: return "I couldn't find a contact named $contactName"

        val useWhatsApp = remainder.contains("whatsapp")

        return if (useWhatsApp) {
            sendViaWhatsApp(context, contact, messageText)
        } else {
            sendViaSms(context, contact, messageText)
        }
    }

    private fun splitNameAndMessage(remainder: String): Pair<String, String>? {
        for (sep in separators) {
            if (remainder.contains(sep)) {
                val parts = remainder.split(sep, limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    return parts[0].trim() to parts[1].trim()
                }
            }
        }
        // Fallback: first word is the contact name, everything after is the message.
        val words = remainder.trim().split(" ")
        if (words.size < 2) return null
        return words.first() to words.drop(1).joinToString(" ")
    }

    private fun sendViaSms(context: Context, contact: ContactLookup.Contact, text: String): String {
        return try {
            // getDefault() works on every Android version; the newer
            // context.getSystemService(SmsManager::class.java) lookup only exists on
            // API 31+ and was silently failing on older phones.
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(contact.phoneNumber, null, text, null, null)
            "Message sent to ${contact.name}"
        } catch (e: Exception) {
            "Couldn't send the message to ${contact.name}"
        }
    }

    private fun sendViaWhatsApp(context: Context, contact: ContactLookup.Contact, text: String): String {
        // WhatsApp's documented scheme for pre-filling a chat. Still requires the user
        // to tap send inside WhatsApp — WhatsApp doesn't expose a send-without-opening
        // API, so this necessarily brings up its UI (another "action needs it" case).
        val phone = contact.phoneNumber.filter { it.isDigit() }
        val uri = Uri.parse("https://wa.me/$phone?text=${Uri.encode(text)}")
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
        return if (intent.resolveActivity(context.packageManager) != null) {
            ActivityLauncher.launch(context, intent)
            "Opening WhatsApp to message ${contact.name}"
        } else {
            "WhatsApp isn't installed"
        }
    }

    private fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
