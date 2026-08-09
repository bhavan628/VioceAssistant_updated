package com.example.voiceassistant.handlers

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/**
 * Handler 2/8: open_app.
 * Looks up installed apps via PackageManager, fuzzy-matches the spoken app name
 * against their display labels (STT text won't exactly match "WhatsApp" or "YouTube"
 * every time), and launches the closest match via its launch Intent.
 *
 * Needs android.permission.QUERY_ALL_PACKAGES (already in the manifest) to see the
 * full list of installed apps on Android 11+ — without it, PackageManager only
 * returns a small "visible by default" subset.
 */
object OpenAppHandler {

    fun handle(context: Context, remainder: String): String {
        val spoken = remainder.trim()
        if (spoken.isBlank()) return "Which app do you want to open?"

        val pm = context.packageManager
        val launchableApps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            0
        )

        var bestLabel: String? = null
        var bestPackage: String? = null
        var bestScore = Int.MAX_VALUE

        for (resolveInfo in launchableApps) {
            val label = resolveInfo.loadLabel(pm).toString()
            val score = fuzzyDistance(spoken.lowercase(), label.lowercase())
            if (score < bestScore) {
                bestScore = score
                bestLabel = label
                bestPackage = resolveInfo.activityInfo.packageName
            }
        }

        // Cutoff: if even the closest match is too different from what was spoken,
        // treat it as no match rather than opening a random app. Loosened from 0.5x
        // to 0.65x since STT mishearing (e.g. "spotifyy", "you tube") needs more slack.
        val threshold = (spoken.length * 0.65).toInt().coerceAtLeast(3)
        if (bestPackage == null || bestScore > threshold) {
            return "I couldn't find an app called $spoken"
        }

        val launchIntent = pm.getLaunchIntentForPackage(bestPackage)
        return if (launchIntent != null) {
            ActivityLauncher.launch(context, launchIntent)
            "Opening $bestLabel"
            // Note: this is the one case in the whole app where UI intentionally
            // appears — the requirement was "no UI unless the action itself needs it,
            // e.g. opening another app" (Spotify was the example given).
        } else {
            "Found $bestLabel but couldn't launch it"
        }
    }

    /** Simple Levenshtein edit distance — good enough for short app-name matching
     *  without pulling in a fuzzy-matching library. */
    private fun fuzzyDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        return dp[a.length][b.length]
    }
}
