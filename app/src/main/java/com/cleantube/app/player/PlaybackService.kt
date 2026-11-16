package com.cleantube.app.player

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Uygulama genelindeki oynatma sahibi.
 *
 * Oynatici SADECE bu servis olusturur ve SADECE servis yok edilirken release eder.
 * Compose ekrani kapaninca oynatici release EDILMEZ; ekran yalnizca MediaController ile
 * servise baglanir (SessionToken) ve ayni ExoPlayer'a PlayerView baglar. Boylece uygulama
 * arka plana gecince ses devam eder ve sistem medya kontrolleri calisir.
 */
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        sharedPlayer = exoPlayer
        mediaSession = MediaSession.Builder(this, exoPlayer).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /** Uygulama son uygulamalardan kapatilirsa ve calmiyorsa servis de kapansin. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = mediaSession?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
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
