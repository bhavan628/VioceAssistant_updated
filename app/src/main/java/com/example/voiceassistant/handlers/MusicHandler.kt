package com.example.voiceassistant.handlers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import java.io.File

/**
 * Handler 4/8: music. Split into offline (local files) and online (Spotify/YT Music)
 * per the user's spec:
 *  - "play music" / "play <song>" with no mention of Spotify -> offline first, falls
 *    back to online only if nothing local matches.
 *  - "play spotify music" / "play <song> on spotify" -> online via Spotify directly.
 *
 * "Recently played" priority is approximated honestly, since neither this app nor
 * Android exposes real playback history to read:
 *  - Offline: local files sorted by lastModified() (most recently added/downloaded)
 *    as the closest available proxy for "recent".
 *  - Online, no song named: opens Spotify's own home screen, which shows Spotify's
 *    actual recently-played (data this app has no way to query directly).
 */
object MusicHandler {

    private const val SPOTIFY_PACKAGE = "com.spotify.music"
    private const val YT_MUSIC_PACKAGE = "com.google.android.apps.youtube.music"

    private val genericFillers = setOf("music", "song", "a", "some", "spotify", "on", "from", "please")

    fun handle(context: Context, remainder: String): String {
        val text = remainder.trim()
        val mentionsSpotify = text.contains("spotify")

        return if (mentionsSpotify) {
            playOnline(context, text)
        } else {
            playOffline(text) ?: playOnline(context, text)
        }
    }

    private fun playOffline(text: String): String? {
        val musicDir = File(
            android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_MUSIC
            ).path
        )
        if (!musicDir.exists()) return null

        val audioFiles = musicDir.listFiles { file ->
            file.extension.lowercase() in listOf("mp3", "m4a", "wav", "ogg")
        }?.sortedByDescending { it.lastModified() } ?: return null
        if (audioFiles.isEmpty()) return null

        val meaningfulWords = text.split(" ").filter { it.isNotBlank() && it !in genericFillers }

        val target = if (meaningfulWords.isEmpty()) {
            audioFiles.first() // most recently added, no specific song requested
        } else {
            val query = meaningfulWords.joinToString(" ").lowercase().replace(Regex("[^a-z0-9 ]"), "")
            audioFiles.firstOrNull { file ->
                val name = file.nameWithoutExtension.lowercase().replace(Regex("[^a-z0-9 ]"), "")
                name.contains(query) || query.contains(name)
            }
        } ?: return null

        return try {
            MediaPlayer().apply {
                setDataSource(target.path)
                prepare()
                start()
            }
            "Playing ${target.nameWithoutExtension} from your local files"
        } catch (e: Exception) {
            null
        }
    }

    private fun playOnline(context: Context, text: String): String {
        val query = text.replace("spotify", "").trim()

        if (isInstalled(context, SPOTIFY_PACKAGE)) {
            return if (query.isBlank()) {
                openAppOnly(context, SPOTIFY_PACKAGE, "Opened Spotify — your recently played is right there")
            } else {
                playViaSpotify(context, query)
            }
        }
        if (isInstalled(context, YT_MUSIC_PACKAGE)) {
            return playViaYouTubeMusic(context, query.ifBlank { text })
        }
        return "I couldn't find Spotify or YouTube Music installed, and no matching local file"
    }

    private fun isInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun openAppOnly(context: Context, packageName: String, message: String): String {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return "Couldn't open Spotify"
        ActivityLauncher.launch(context, intent)
        return message
    }

    private fun playViaSpotify(context: Context, query: String): String {
        val uri = Uri.parse("spotify:search:${Uri.encode(query)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage(SPOTIFY_PACKAGE) }
        return try {
            ActivityLauncher.launch(context, intent)
            "Opened Spotify search for $query — tap the top result to play it"
        } catch (e: Exception) {
            "Found Spotify but couldn't open search for $query"
        }
    }

    private fun playViaYouTubeMusic(context: Context, query: String): String {
        val uri = Uri.parse("https://music.youtube.com/search?q=${Uri.encode(query)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage(YT_MUSIC_PACKAGE) }
        return try {
            ActivityLauncher.launch(context, intent)
            "Opened YouTube Music search for $query — tap the top result to play it"
        } catch (e: Exception) {
            "Found YouTube Music but couldn't open search for $query"
        }
    }
}
