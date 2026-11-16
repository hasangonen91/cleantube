package com.cleantube.app.player

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * App-wide playback owner.
 *
 * The UI attaches to this player; it must not release the player when a Compose
 * destination leaves composition. MediaSessionService keeps audio playback alive
 * while the app is backgrounded and exposes system media controls.
 */
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val exoPlayer = ExoPlayer.Builder(this).build()
        sharedPlayer = exoPlayer
        mediaSession = MediaSession.Builder(this, exoPlayer).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        sharedPlayer = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var sharedPlayer: ExoPlayer? = null
            private set
    }
}
