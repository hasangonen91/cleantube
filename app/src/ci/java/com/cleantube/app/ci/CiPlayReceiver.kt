@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.cleantube.app.ci

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.cleantube.app.player.PlaybackService

/**
 * CI-only: gercek PlaybackService + MediaController + ExoPlayer hattini YouTube'dan bagimsiz sinar.
 * adb shell am broadcast -n com.cleantube.app/.ci.CiPlayReceiver --es url <URL> --es kind progressive|hls|dash
 */
class CiPlayReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val url = intent.getStringExtra("url") ?: return
        val kind = intent.getStringExtra("kind") ?: "progressive"
        val app = ctx.applicationContext
        val main = Handler(Looper.getMainLooper())
        controller?.let { runCatching { MediaController.releaseFuture(it) } }
        controller = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()

        var tries = 0
        fun start() {
            val p = PlaybackService.sharedPlayer
            if (p == null) {
                if (++tries > 100) { Log.e(TAG, "$kind: sharedPlayer YOK (servis baslamadi)"); return }
                main.postDelayed({ start() }, 100); return
            }
            Log.i(TAG, "$kind: servis oynaticisi hazir, kaynak: $url")
            val f = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
            val item = MediaItem.fromUri(url)
            p.stop(); p.clearMediaItems()
            p.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) { Log.i(TAG, "$kind: state=$state") }
                override fun onPlayerError(e: PlaybackException) { Log.e(TAG, "$kind: HATA ${e.errorCodeName}", e) }
            })
            p.setMediaSource(when (kind) {
                "hls" -> HlsMediaSource.Factory(f).createMediaSource(item)
                "dash" -> DashMediaSource.Factory(f).createMediaSource(item)
                else -> ProgressiveMediaSource.Factory(f).createMediaSource(item)
            })
            p.prepare(); p.playWhenReady = true
            main.postDelayed({
                Log.i(TAG, "$kind: SONUC isPlaying=${p.isPlaying} state=${p.playbackState} pos=${p.currentPosition}ms video=${p.videoSize.width}x${p.videoSize.height}")
            }, 20_000)
        }
        main.post { start() }
    }

    companion object {
        private const val TAG = "VYROCI"
        private var controller: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    }
}
