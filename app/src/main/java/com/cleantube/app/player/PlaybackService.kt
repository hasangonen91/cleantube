package com.cleantube.app.player

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.cleantube.app.MainActivity

/**
 * App-wide playback owner.
 *
 * The UI attaches to this player; it must not release the player when a Compose
 * destination leaves composition. MediaSessionService keeps audio playback alive
 * while the app is backgrounded and exposes system media controls/notification.
 */
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val audio = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audio, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        sharedPlayer = exoPlayer
        val sessionIntent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this, 0, sessionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(pi)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

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
