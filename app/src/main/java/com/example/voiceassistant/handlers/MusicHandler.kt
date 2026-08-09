package com.example.voiceassistant.handlers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import java.io.File

/**
 * Handler 4/8: music.
 *
 * Tries, in order:
 *  1. Spotify, via its search-and-play deep link (works even without a Spotify API
 *     integration — Spotify handles the search itself).
 *  2. YouTube Music, if Spotify isn't installed.
 *  3. A local file under Music/ whose filename loosely matches the spoken title, via
 *     MediaPlayer, if neither streaming app is installed.
 *  4. A clear spoken failure message if none of the above work.
 *
 * Like OpenAppHandler, playing via Spotify/YouTube Music necessarily brings that app's
 * UI to the foreground — that's the "except when the action itself requires opening
 * another app" exception from the original requirement.
 */
object MusicHandler {

    private const val SPOTIFY_PACKAGE = "com.spotify.music"
    private const val YT_MUSIC_PACKAGE = "com.google.android.apps.youtube.music"

    fun handle(context: Context, remainder: String): String {
        val query = remainder.trim()
        if (query.isBlank()) return "What would you like me to play?"

        if (isInstalled(context, SPOTIFY_PACKAGE)) {
            return playViaSpotify(context, query)
        }
        if (isInstalled(context, YT_MUSIC_PACKAGE)) {
            return playViaYouTubeMusic(context, query)
        }

        val localMatch = findLocalTrack(query)
        if (localMatch != null) {
            return playLocal(context, localMatch, query)
        }

        return "I couldn't find Spotify, YouTube Music, or a local file for $query"
    }

    private fun isInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun playViaSpotify(context: Context, query: String): String {
        // Honest limitation: Spotify's public URI scheme opens its search screen
        // pre-filled with the query — it does NOT auto-play the top result without
        // Spotify's paid Web API (OAuth + a backend). This used to claim "Playing X",
        // which wasn't true; wording now matches what actually happens.
        val uri = Uri.parse("spotify:search:${Uri.encode(query)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(SPOTIFY_PACKAGE)
        }
        return try {
            ActivityLauncher.launch(context, intent)
            "Opened Spotify search for $query — tap the top result to play it"
        } catch (e: Exception) {
            "Found Spotify but couldn't open search for $query"
        }
    }

    private fun playViaYouTubeMusic(context: Context, query: String): String {
        val uri = Uri.parse("https://music.youtube.com/search?q=${Uri.encode(query)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(YT_MUSIC_PACKAGE)
        }
        return try {
            ActivityLauncher.launch(context, intent)
            "Opened YouTube Music search for $query — tap the top result to play it"
        } catch (e: Exception) {
            "Found YouTube Music but couldn't open search for $query"
        }
    }

    /** Loose filename match against the device's Music folder — no MediaStore query
     *  permission dance needed for this simple case, just a direct file scan. */
    private fun findLocalTrack(query: String): File? {
        val musicDir = File(android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_MUSIC
        ).path)
        if (!musicDir.exists()) return null

        val normalizedQuery = query.lowercase().replace(Regex("[^a-z0-9]"), "")
        return musicDir.listFiles { file ->
            file.extension.lowercase() in listOf("mp3", "m4a", "wav", "ogg")
        }?.firstOrNull { file ->
            val normalizedName = file.nameWithoutExtension.lowercase().replace(Regex("[^a-z0-9]"), "")
            normalizedName.contains(normalizedQuery) || normalizedQuery.contains(normalizedName)
        }
    }

    private fun playLocal(context: Context, file: File, query: String): String {
        return try {
            MediaPlayer().apply {
                setDataSource(file.path)
                prepare()
                start()
            }
            "Playing $query from your local files"
        } catch (e: Exception) {
            "Found a local file for $query but couldn't play it"
        }
    }
}
