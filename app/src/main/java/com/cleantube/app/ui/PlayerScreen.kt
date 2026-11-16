@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.cleantube.app.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.cleantube.app.data.HttpDownloader
import com.cleantube.app.data.Repo
import com.cleantube.app.data.Video
import com.cleantube.app.data.fmtViews
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import com.cleantube.app.player.PlaybackService
import org.schabi.newpipe.extractor.stream.StreamInfo

@Composable
fun PlayerScreen(vm: MainVM, video: Video) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    var info by remember(video.id) { mutableStateOf<StreamInfo?>(null) }
    var error by remember(video.id) { mutableStateOf<String?>(null) }
    var maxH by remember { mutableIntStateOf(720) }
    var full by remember { mutableStateOf(false) }
    var showQ by remember { mutableStateOf(false) }
    var loadedFor by remember { mutableStateOf<String?>(null) }

    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    val fb = remember { SourceChain() }

    // Servise MediaController (SessionToken) ile baglan. Servisi bu baglanti ayakta tutar;
    // ekran kapaninca yalnizca controller birakilir, oynatici release EDILMEZ.
    DisposableEffect(Unit) {
        var future: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
        try {
            val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
            future = MediaController.Builder(ctx, token).buildAsync()
        } catch (t: Throwable) {
            Log.w("VYRO", "MediaController baglanamadi, servis dogrudan baslatiliyor", t)
            try { ctx.startService(Intent(ctx, PlaybackService::class.java)) } catch (t2: Throwable) { Log.w("VYRO", "Servis baslatilamadi", t2) }
        }
        onDispose { future?.let { runCatching { MediaController.releaseFuture(it) } } }
    }
    LaunchedEffect(Unit) {
        repeat(100) {
            PlaybackService.sharedPlayer?.let {
                player = it
                it.playWhenReady = true
                return@LaunchedEffect
            }
            delay(50)
        }
        error = "Medya servisi başlatılamadı"
    }
    val activePlayer = player
    if (activePlayer == null) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            if (error == null) CircularProgressIndicator() else Text(error!!, Modifier.padding(16.dp))
        }
        BackHandler { vm.playing = null }
        return
    }
    // Video kapatilinca (ekran kompozisyondan cikinca) oynatmayi durdur; oynatici servisin, release ETME.
    DisposableEffect(activePlayer) {
        onDispose { runCatching { activePlayer.stop(); activePlayer.clearMediaItems() } }
    }
    DisposableEffect(activePlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(playbackError: PlaybackException) {
                Log.w("VYRO", "Oynatma hatasi: ${playbackError.errorCodeName}", playbackError)
                // Guvenilir fallback: siradaki kaynagi dene; hepsi tukenirse mesaj goster.
                if (!fb.tryNext(activePlayer)) {
                    error = playbackError.localizedMessage ?: "Video akışı oynatılamadı"
                }
            }
        }
        activePlayer.addListener(listener)
        onDispose { activePlayer.removeListener(listener) }
    }
    DisposableEffect(full) {
        val w = activity?.window
        if (w != null) {
            val c = WindowCompat.getInsetsController(w, w.decorView)
            if (full) {
                c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                c.hide(WindowInsetsCompat.Type.systemBars())
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                c.show(WindowInsetsCompat.Type.systemBars())
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
        onDispose {
            val ww = activity?.window
            if (ww != null) WindowCompat.getInsetsController(ww, ww.decorView).show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(video.id) {
        info = null; error = null; loadedFor = null
        runCatching { activePlayer.stop(); activePlayer.clearMediaItems() }
        try { info = Repo.resolve(video.id) }
        catch (e: CancellationException) { throw e }
        catch (e: Throwable) { Log.w("VYRO", "Video cozulemedi", e); error = e.message ?: "Video açılamadı" }
    }
    LaunchedEffect(info, maxH) {
        val i = info ?: return@LaunchedEffect
        try {
            val keepPos = if (loadedFor == video.id) activePlayer.currentPosition else 0L
            val factory = DefaultHttpDataSource.Factory()
                .setUserAgent(HttpDownloader.USER_AGENT)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(20_000)
                .setReadTimeoutMs(30_000)
            val list = Repo.candidates(i, maxH, factory)
            if (list.isEmpty()) {
                error = "Bu video için uyumlu bir akış bulunamadı."
                return@LaunchedEffect
            }
            error = null
            fb.reset(list, keepPos)
            fb.tryNext(activePlayer)
            loadedFor = video.id
        } catch (e: CancellationException) { throw e }
        catch (e: Throwable) {
            Log.w("VYRO", "Video hazirlanamadi", e)
            error = e.localizedMessage ?: "Video hazırlanırken hata oluştu."
        }
    }
    BackHandler { if (full) full = false else vm.playing = null }

    val playerView: @Composable (Modifier) -> Unit = { m ->
        AndroidView(
            modifier = m,
            factory = { c ->
                PlayerView(c).apply {
                    this.player = activePlayer; keepScreenOn = true
                    setFullscreenButtonClickListener { full = !full }
                }
            },
        )
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (full) {
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) { playerView(Modifier.fillMaxSize()) }
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(androidx.compose.ui.graphics.Color.Black)) {
                    playerView(Modifier.fillMaxSize())
                    if (info == null && error == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
                    error?.let { Text(it, Modifier.align(Alignment.Center).padding(16.dp)) }
                }
                val i = info
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Column(Modifier.padding(12.dp)) {
                            Text(i?.name ?: video.title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            val sub = listOfNotNull(i?.uploaderName ?: video.channel, i?.viewCount?.takeIf { it >= 0 }?.let { fmtViews(it) })
                                .filter { it.isNotBlank() }.joinToString(" • ")
                            Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { vm.playing = null }) { Icon(Icons.Filled.ArrowBack, "Kapat") }
                                if (i != null) {
                                    Box {
                                        TextButton(onClick = { showQ = true }) { Text("Kalite: ${maxH}p") }
                                        DropdownMenu(showQ, { showQ = false }) {
                                            Repo.qualities(i).forEach { q ->
                                                DropdownMenuItem(text = { Text("${q}p") }, onClick = { maxH = q; showQ = false })
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (i != null) {
                        items(Repo.related(i), key = { it.id }) { r -> VideoCard(r) { vm.playing = r } }
                    }
                }
            }
        }
    }
}

/** Siradaki oynatma kaynagini deneyen basit zincir (progressive -> birlestirme -> DASH -> HLS). */
private class SourceChain {
    private var items: List<() -> MediaSource> = emptyList()
    private var idx = 0
    private var startPos = 0L

    fun reset(list: List<() -> MediaSource>, pos: Long) { items = list; idx = 0; startPos = pos }

    /** true: bir kaynak denendi. false: aday kalmadi. */
    fun tryNext(p: ExoPlayer): Boolean {
        while (idx < items.size) {
            val make = items[idx++]
            try {
                p.setMediaSource(make())
                p.prepare()
                if (startPos > 0) p.seekTo(startPos)
                p.playWhenReady = true
                return true
            } catch (e: Throwable) {
                Log.w("VYRO", "Kaynak olusturulamadi, siradaki deneniyor", e)
            }
        }
        return false
    }
}
