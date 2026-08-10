package com.example.voiceassistant.handlers

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Handler 1/8: time.
 * Reads the current date/time off the device's system clock — no internet, no
 * permissions beyond what the app already has.
 *
 * Formats:
 *  - Time: HH:MM AM/PM, e.g. "07:45 PM"
 *  - Date: DD:Month name:YYYY, e.g. "09:August:2026"
 */
object TimeHandler {

    fun handle(remainder: String): String {
        val now = Date()
        val wantsDate = remainder.contains("date")

        return if (wantsDate) {
            val fmt = SimpleDateFormat("dd:MMMM:yyyy", Locale.getDefault())
            fmt.format(now)
        } else {
            val fmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
            fmt.format(now)
        }
    }
}
