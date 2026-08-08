package com.example.voiceassistant.handlers

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Handler 1/8: time.
 * Reads the current date/time off the device's system clock — no internet, no
 * permissions beyond what the app already has.
 */
object TimeHandler {

    fun handle(remainder: String): String {
        val now = Date()
        val wantsDate = remainder.contains("date")

        return if (wantsDate) {
            val fmt = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
            "Today is ${fmt.format(now)}"
        } else {
            val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
            "It's ${fmt.format(now)}"
        }
    }
}
