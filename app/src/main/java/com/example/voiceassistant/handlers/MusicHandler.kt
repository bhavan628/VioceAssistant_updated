package com.example.voiceassistant.handlers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.view.KeyEvent
import java.io.File

/**
 * Two completely separate paths, no fallback between them:
 *  - "play music" (no "spotify" mentioned) -> opens the device's own local Music
 *    app and asks it to start playing. NEVER touches Spotify.
 *  - "play spotify music" -> opens Spotify specifically, recently-played priority.
 *
 * Previous version had a bug: plain "play music" would silently fall back to
 * opening Spotify if no local file matched — that's removed. The two paths are now
 * fully independent, matching what was actually asked for.
 */
object MusicHandler {

    private const val SPOTIFY_PACKAGE = "com.spotify.music"
    private val genericFillers = setOf("music", "song", "a", "some", "spotify", "on", "from", "please")

    fun handle(context: Context, remainder: String): String {
        val text = remainder.trim()
        return if (text.contains("spotify")) {
            playViaSpotify(context, text)
        } else {
            playViaLocalMusicApp(context, text)
        }
    }

    // ---- "play music" -> local Music app only ----

    private fun playViaLocalMusicApp(context: Context, text: String): String {
        val pm = context.packageManager

        // CATEGORY_APP_MUSIC is how Android identifies "the" music player app on a
        // given phone — same mechanism launchers/assistants use.
        val musicAppIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
        val resolveInfo = pm.resolveActivity(musicAppIntent, PackageManager.MATCH_DEFAULT_ONLY)

        if (resolveInfo != null) {
            val launchIntent = pm.getLaunchIntentForPackage(resolveInfo.activityInfo.packageName)
            if (launchIntent != null) {
                ActivityLauncher.launch(context, launchIntent)
                // Opening the app alone doesn't start playback — most music apps
                // need an actual song tapped. A media-button PLAY event asks
                // whichever app owns the active media session to start/resume —
                // works on many music apps, not guaranteed on all of them.
                sendPlayMediaButton(context)
                return "Opened your music app"
            }
        }

        // No dedicated music app found on this phone at all — fall back to playing
        // a local file directly so at least something plays.
        return playLocalFileDirectly(text) ?: "I couldn't find a music app or any local songs"
    }

    private fun sendPlayMediaButton(context: Context) {
        try {
            val down = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
            }
            val up = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
            }
            context.sendOrderedBroadcast(down, null)
            context.sendOrderedBroadcast(up, null)
        } catch (e: Exception) {
            // Not fatal — the app is still open even if this doesn't trigger playback.
        }
    }

    private fun playLocalFileDirectly(text: String): String? {
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
            audioFiles.first()
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

    // ---- "play spotify music" -> Spotify only, recently-played priority ----

    private fun playViaSpotify(context: Context, text: String): String {
        if (!isInstalled(context, SPOTIFY_PACKAGE)) {
            return "Spotify isn't installed"
        }
        val query = text.replace("spotify", "").trim()

        return if (query.isBlank()) {
            // No specific song named — open Spotify itself, which shows Spotify's
            // own recently-played on its home screen (real playback history that
            // only Spotify has, not something this app can read directly).
            val intent = context.packageManager.getLaunchIntentForPackage(SPOTIFY_PACKAGE)
                ?: return "Couldn't open Spotify"
            ActivityLauncher.launch(context, intent)
            "Opened Spotify — your recently played is right there"
        } else {
            val uri = Uri.parse("spotify:search:${Uri.encode(query)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage(SPOTIFY_PACKAGE) }
            try {
                ActivityLauncher.launch(context, intent)
                "Opened Spotify search for $query — tap the top result to play it"
            } catch (e: Exception) {
                "Found Spotify but couldn't open search for $query"
            }
        }
    }

    private fun isInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
