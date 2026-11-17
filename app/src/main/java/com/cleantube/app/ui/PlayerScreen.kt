@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.cleantube.app.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
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
import com.cleantube.app.data.CommentItem
import com.cleantube.app.data.InnerTube
import com.cleantube.app.data.Prefs
import com.cleantube.app.data.Repo
import com.cleantube.app.data.Video
import com.cleantube.app.data.fmtViews
import com.cleantube.app.player.PlaybackService
import kotlinx.coroutines.CancellationException
import org.schabi.newpipe.extractor.stream.StreamInfo

@OptIn(ExperimentalMaterial3Api::class)
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
    var showComments by remember { mutableStateOf(false) }
    var comments by remember(video.id) { mutableStateOf<List<CommentItem>>(emptyList()) }
    var commentsLoading by remember(video.id) { mutableStateOf(false) }
    var commentsError by remember(video.id) { mutableStateOf<String?>(null) }
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
    // Ekran kapanip uygulama arka plana gecince servis oynatmaya devam eder.
    // Oynatma yalnizca kullanici oynaticiyi kapattiginda durdurulur.
    val closePlayer = {
        runCatching { activePlayer.pause(); activePlayer.stop(); activePlayer.clearMediaItems() }
        vm.playing = null
    }
    DisposableEffect(activePlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(playbackError: PlaybackException) {
                Log.w("VYRO", "Oynatma hatasi: ${playbackError.errorCodeName}", playbackError)
                if (!fb.tryNext(activePlayer)) {
                    error = playbackError.localizedMessage ?: "Video akışı oynatılamadı"
                }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && vm.queueIndex >= 0 && vm.queueIndex < vm.queue.lastIndex) {
                    vm.playNext()
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
    BackHandler { if (full) full = false else vm.minimizePlayer() }

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
            update = { it.player = activePlayer },
            onRelease = { it.player = null },
        )
    }
    val overlay: @Composable (Modifier) -> Unit = { m ->
        PlayerOverlay(
            player = activePlayer, full = full,
            onClose = { if (full) full = false else vm.minimizePlayer() },
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
                            TextButton(onClick = { showComments = true }) {
                                Text("☰")
                                Spacer(Modifier.width(6.dp))
                                Text("Yorumları gör")
                            }
                            TextButton(onClick = { showQ = true }) {
                                Text("Kalite: " + if (qualitySel == 0) "Otomatik (${Prefs.AUTO_HEIGHT}p)" else "${qualitySel}p")
                            }
                        }
                    }
                    if (i != null) {
                        val related = Repo.related(i)
                        items(related, key = { it.id }) { r -> VideoCard(r) { vm.play(r, related) } }
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
        if (showComments) {
            ModalBottomSheet(onDismissRequest = { showComments = false }) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text("Yorumlar", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    when {
                        commentsLoading -> Box(Modifier.fillMaxWidth().height(120.dp), Alignment.Center) { CircularProgressIndicator() }
                        commentsError != null -> Text(commentsError!!, color = MaterialTheme.colorScheme.error)
                        comments.isEmpty() -> Text("Bu video için herkese açık yorum bulunamadı.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                            items(comments, key = { it.id }) { comment ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
                                    AsyncImage(comment.avatar.ifBlank { "https://www.youtube.com/s/desktop/12f6b690/img/favicon_144x144.png" }, null,
                                        Modifier.size(36.dp).clip(CircleShape))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(comment.author, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Spacer(Modifier.height(4.dp))
                                        Text(comment.text, fontSize = 14.sp, lineHeight = 19.sp)
                                        if (comment.likes.isNotBlank()) Text("♡ ${comment.likes}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Divider()
                            }
                        }
                    }
                }
                LaunchedEffect(video.id) {
                    commentsLoading = true
                    commentsError = null
                    try { comments = InnerTube.comments(video.id) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Throwable) { commentsError = e.localizedMessage ?: "Yorumlar yüklenemedi" }
                    commentsLoading = false
                }
            }
        }
    }
}


/** Sekme değiştirildiğinde aynı servis oynatıcısını altta tutar; video kesilmez. */
@Composable
fun MiniPlayer(vm: MainVM) {
    val video = vm.playing ?: return
    val player = PlaybackService.sharedPlayer
    var isPlaying by remember(player) { mutableStateOf(player?.isPlaying == true) }
    DisposableEffect(player) {
        val p = player ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        p.addListener(listener)
        onDispose { p.removeListener(listener) }
    }
    Row(
        Modifier.fillMaxWidth().height(68.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { vm.expandPlayer() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(112.dp).height(64.dp).background(Color.Black)) {
            if (player != null) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        PlayerView(context).apply {
                            this.player = player
                            useController = false
                            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        }
                    },
                    update = { it.player = player },
                    onRelease = { it.player = null },
                )
            } else {
                AsyncImage(video.thumb, null, Modifier.fillMaxSize())
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(video.title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(video.channel, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        IconButton(onClick = {
            if (player?.isPlaying == true) player.pause() else player?.play()
        }) { Text(if (isPlaying) "Ⅱ" else "▶", fontSize = 20.sp) }
        IconButton(onClick = {
            runCatching { player?.pause(); player?.stop(); player?.clearMediaItems() }
            vm.closePlayer()
        }) { Text("✕", fontSize = 18.sp) }
    }
}
