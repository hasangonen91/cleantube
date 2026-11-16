@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.cleantube.app.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
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
import androidx.media3.ui.PlayerView
import com.cleantube.app.data.HttpDownloader
import com.cleantube.app.data.Repo
import com.cleantube.app.data.Video
import com.cleantube.app.data.fmtViews
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
    LaunchedEffect(Unit) {
        ContextCompat.startForegroundService(ctx, Intent(ctx, PlaybackService::class.java))
        repeat(60) {
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
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        return
    }
    DisposableEffect(activePlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(playbackError: PlaybackException) {
                error = playbackError.localizedMessage ?: "Video akışı oynatılamadı"
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
        info = null; error = null
        activePlayer.stop(); activePlayer.clearMediaItems()
        try { info = Repo.resolve(video.id) } catch (e: Exception) { error = e.message ?: "Video açılamadı" }
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
            val src = Repo.buildSource(i, maxH, factory)
            if (src == null) {
                error = "Bu video için uyumlu bir akış bulunamadı."
                return@LaunchedEffect
            }
            activePlayer.setMediaSource(src)
            activePlayer.prepare()
            if (keepPos > 0) activePlayer.seekTo(keepPos)
            activePlayer.playWhenReady = true
            loadedFor = video.id
        } catch (e: Exception) {
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
