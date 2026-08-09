package com.example.voiceassistant.handlers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import java.util.Calendar

/**
 * Handler 5/8: alarm / reminder.
 *
 * Uses `AlarmClock.ACTION_SET_ALARM` rather than `AlarmManager` directly — it's the
 * standard "ask the user's Clock app to set an alarm" intent, doesn't need
 * SCHEDULE_EXACT_ALARM's special-access screen, and shows the alarm in the Clock app
 * like any other alarm the user set themselves. This DOES briefly bring the Clock
 * app's UI up on some OEMs (a few skip UI and confirm silently) — another instance of
 * the "unless the action itself needs it" exception, same as open_app/music.
 *
 * For a pure background reminder with NO app switch at all, this file also includes
 * `scheduleSilentReminder()` using AlarmManager directly — that path stays fully
 * headless but requires the user to grant the SCHEDULE_EXACT_ALARM special access
 * first (Settings > Alarms & reminders, Android 12+). Swap which one MainActivity's
 * flow uses based on which behavior you want for your client.
 */
object AlarmHandler {

    fun handle(context: Context, remainder: String): String {
        val parsed = parseTime(remainder)
            ?: return "I couldn't figure out what time to set that for"

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, parsed.first)
            putExtra(AlarmClock.EXTRA_MINUTES, parsed.second)
            putExtra(AlarmClock.EXTRA_MESSAGE, "Voice Assistant reminder")
        }

        return if (intent.resolveActivity(context.packageManager) != null) {
            ActivityLauncher.launch(context, intent)
            val displayHour = if (parsed.first % 12 == 0) 12 else parsed.first % 12
            val ampm = if (parsed.first < 12) "AM" else "PM"
            "Alarm set for %d:%02d %s".format(displayHour, parsed.second, ampm)
        } else {
            "No clock app found to set the alarm"
        }
    }

    /** Fully headless alternative — no Clock app UI at all, but needs the user to have
     *  granted "Alarms & reminders" special access first (Android 12+). */
    fun scheduleSilentReminder(context: Context, remainder: String): String {
        val parsed = parseTime(remainder)
            ?: return "I couldn't figure out what time to set that for"

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, parsed.first)
            set(Calendar.MINUTE, parsed.second)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DAY_OF_YEAR, 1)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return try {
            if (!alarmManager.canScheduleExactAlarms()) {
                return "I need permission to schedule exact alarms first — please grant " +
                    "\"Alarms & reminders\" access in system settings"
            }
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerTime.timeInMillis, pendingIntent
            )
            "Reminder set, no app will open"
        } catch (e: SecurityException) {
            "I don't have permission to schedule exact alarms"
        }
    }

    /** Handles "7 30", "7:30", "seven thirty", "half past seven" style inputs loosely.
     *  Returns 24-hour (hour, minute) or null if nothing recognizable was found. */
    private fun parseTime(text: String): Pair<Int, Int>? {
        val normalized = NumberWords.normalize(text)
        val digitMatch = Regex("""(\d{1,2})[:\s.](\d{2})""").find(normalized)
        if (digitMatch != null) {
            val h = digitMatch.groupValues[1].toInt()
            val m = digitMatch.groupValues[2].toInt()
            return normalizeAmPm(h, m, normalized)
        }

        val hourOnly = Regex("""\b(\d{1,2})\s*(am|pm)?\b""").find(normalized)
        if (hourOnly != null) {
            val h = hourOnly.groupValues[1].toIntOrNull() ?: return null
            return normalizeAmPm(h, 0, normalized)
        }
        return null
    }

    private fun normalizeAmPm(hour: Int, minute: Int, text: String): Pair<Int, Int>? {
        if (hour !in 0..23 || minute !in 0..59) return null
        var h = hour
        if (text.contains("pm") && h < 12) h += 12
        if (text.contains("am") && h == 12) h = 0
        return h to minute
    }
}
