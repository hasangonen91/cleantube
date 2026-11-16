@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.cleantube.app.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.ui.PlayerView
import com.cleantube.app.data.HttpDownloader
import com.cleantube.app.data.Prefs
import com.cleantube.app.data.Repo
import com.cleantube.app.data.Video
import com.cleantube.app.data.fmtViews
import kotlinx.coroutines.CancellationException
import org.schabi.newpipe.extractor.stream.StreamInfo

@Composable
fun PlayerScreen(vm: MainVM, video: Video) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    var info by remember(video.id) { mutableStateOf<StreamInfo?>(null) }
    var error by remember(video.id) { mutableStateOf<String?>(null) }
    var qualitySel by remember { mutableIntStateOf(Prefs.quality) }
    val maxH = Prefs.heightFor(qualitySel)
    var full by remember { mutableStateOf(false) }
    var showQ by remember { mutableStateOf(false) }
    var loadedFor by remember { mutableStateOf<String?>(null) }
    val fb = remember { SourceChain() }

    val activePlayer = rememberServicePlayer { error = it }
    if (activePlayer == null) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            if (error == null) CircularProgressIndicator() else Text(error!!, Modifier.padding(16.dp))
        }
        BackHandler { vm.playing = null }
        return
    }
    // Video kapatilinca oynatmayi durdur; oynatici servisin, release ETME.
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
        runCatching {
            activePlayer.stop(); activePlayer.clearMediaItems()
            activePlayer.repeatMode = Player.REPEAT_MODE_OFF
            activePlayer.volume = 1f
        }
        try { info = vm.streamInfo(video.id) }
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
                    this.player = activePlayer
                    useController = false
                    keepScreenOn = true
                }
            },
        )
    }
    val overlay: @Composable (Modifier) -> Unit = { m ->
        PlayerOverlay(
            player = activePlayer, full = full,
            onClose = { if (full) full = false else vm.playing = null },
            onFullscreen = { full = !full },
            onSettings = { showQ = true },
            onNext = { info?.let { Repo.related(it).firstOrNull() }?.let { vm.playing = it } },
            modifier = m,
        )
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (full) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                playerView(Modifier.fillMaxSize())
                overlay(Modifier.fillMaxSize())
            }
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
                    playerView(Modifier.fillMaxSize())
                    overlay(Modifier.fillMaxSize())
                    if (info == null && error == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
                    error?.let {
                        Text(
                            it, color = Color.White, fontSize = 13.sp,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp, start = 16.dp, end = 16.dp)
                                .background(Color(0xCC000000)).padding(8.dp),
                        )
                    }
                }
                val i = info
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Column(Modifier.padding(12.dp)) {
                            Text(i?.name ?: video.title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            val sub = listOfNotNull(i?.uploaderName ?: video.channel, i?.viewCount?.takeIf { it >= 0 }?.let { fmtViews(it) })
                                .filter { it.isNotBlank() }.joinToString(" • ")
                            Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { showQ = true }) {
                                Text("Kalite: " + if (qualitySel == 0) "Otomatik (${Prefs.AUTO_HEIGHT}p)" else "${qualitySel}p")
                            }
                        }
                    }
                    if (i != null) {
                        items(Repo.related(i), key = { it.id }) { r -> VideoCard(r) { vm.playing = r } }
                    }
                }
            }
        }
        if (showQ) {
            QualitySheet(
                options = info?.let { Repo.qualities(it) }?.takeIf { it.isNotEmpty() } ?: Prefs.DEFAULT_QUALITIES,
                selected = qualitySel,
                onSelect = { Prefs.quality = it; qualitySel = it; showQ = false },
                onDismiss = { showQ = false },
            )
        }
    }
}
