@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.cleantube.app.ui

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.cleantube.app.data.*
import com.cleantube.app.player.PlaybackService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.File

@Composable
fun MiniPlayer(vm: MainVM, modifier: Modifier = Modifier) {
    val video = vm.playing ?: return
    val player = PlaybackService.sharedPlayer
    var isPlaying by remember { mutableStateOf(player?.isPlaying == true) }
    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose {}
        val l = object : Player.Listener { override fun onIsPlayingChanged(p: Boolean) { isPlaying = p } }
        player.addListener(l); isPlaying = player.isPlaying
        onDispose { player.removeListener(l) }
    }
    Surface(
        modifier = modifier.fillMaxWidth().height(64.dp).clickable { vm.expandPlayer() },
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(video.thumb, null, Modifier.width(112.dp).fillMaxHeight(), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(video.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(video.channel, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { if (isPlaying) player?.pause() else player?.play() }) {
                Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (isPlaying) "Duraklat" else "Oynat")
            }
            IconButton(onClick = { vm.closePlayer(); player?.stop() }) { Icon(Icons.Filled.Close, contentDescription = "Kapat") }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PlayerSheet(vm: MainVM) {
    val video = vm.playing ?: return
    if (!vm.playerExpanded) return
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    val cfg = LocalConfiguration.current
    var info by remember(video.id) { mutableStateOf<StreamInfo?>(null) }
    var error by remember(video.id) { mutableStateOf<String?>(null) }
    var maxH by remember(video.id) { mutableIntStateOf(vm.defaultQuality) }
    var full by remember { mutableStateOf(false) }
    var showQ by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var descExpanded by remember(video.id) { mutableStateOf(false) }
    var loadedFor by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var liked by remember(video.id) { mutableStateOf(false) }
    var subbed by remember(video.id) { mutableStateOf(false) }
    var savedWL by remember(video.id) { mutableStateOf(false) }
    var showComments by remember(video.id) { mutableStateOf(false) }
    var showSubs by remember(video.id) { mutableStateOf(false) }
    var showDownload by remember(video.id) { mutableStateOf(false) }

    LaunchedEffect(video.id) {
        liked = LocalStore.isLiked(video.id)
        subbed = video.channelId.isNotBlank() && LocalStore.isSubscribed(video.channelId)
        savedWL = LocalStore.isWatchLater(video.id)
    }

    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    LaunchedEffect(Unit) {
        ContextCompat.startForegroundService(ctx, Intent(ctx, PlaybackService::class.java))
        repeat(80) { PlaybackService.sharedPlayer?.let { player = it; return@LaunchedEffect }; delay(50) }
        error = "Medya servisi başlatılamadı"
    }
    val activePlayer = player ?: run {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }; return
    }

    DisposableEffect(activePlayer, video.id) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) { error = e.localizedMessage ?: "Video akışı oynatılamadı" }
            override fun onIsPlayingChanged(p: Boolean) { isPlaying = p }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && (vm.autoplay || vm.repeatMode > 0)) vm.playNext(auto = true)
            }
        }
        activePlayer.addListener(listener)
        isPlaying = activePlayer.isPlaying
        onDispose { activePlayer.removeListener(listener) }
    }

    DisposableEffect(full) {
        val w = activity?.window
        if (w != null) {
            val c = WindowCompat.getInsetsController(w, w.decorView)
            if (full) {
                c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                c.hide(WindowInsetsCompat.Type.systemBars())
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                c.show(WindowInsetsCompat.Type.systemBars())
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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
        try { info = Repo.resolve(video.id); vm.currentStreamInfo = info } catch (e: Exception) { error = e.message ?: "Video açılamadı" }
    }
    LaunchedEffect(info, maxH) {
        val i = info ?: return@LaunchedEffect
        try {
            val keepPos = if (loadedFor == video.id) activePlayer.currentPosition else LocalStore.getProgress(video.id)
            // Offline playback: prefer completed local download
            val localFile = runCatching {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.listFiles()?.firstOrNull { it.name.contains(video.id) && it.exists() && it.length() > 0 }
            }.getOrNull()
            if (localFile != null) {
                activePlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(Uri.fromFile(localFile)))
                activePlayer.prepare()
                if (keepPos > 0) activePlayer.seekTo(keepPos)
                activePlayer.setPlaybackSpeed(vm.playbackSpeed)
                activePlayer.playWhenReady = true
                loadedFor = video.id
                return@LaunchedEffect
            }
            val factory = DefaultHttpDataSource.Factory()
                .setUserAgent(HttpDownloader.USER_AGENT).setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(20_000).setReadTimeoutMs(30_000)
            val src = Repo.buildSource(i, maxH, factory) ?: run { error = "Uyumlu akış bulunamadı"; return@LaunchedEffect }
            activePlayer.setMediaSource(src); activePlayer.prepare()
            if (keepPos > 0) activePlayer.seekTo(keepPos)
            activePlayer.setPlaybackSpeed(vm.playbackSpeed)
            activePlayer.playWhenReady = true
            loadedFor = video.id
        } catch (e: Exception) { error = e.localizedMessage ?: "Video hazırlanırken hata oluştu" }
    }

    LaunchedEffect(video.id, loadedFor) {
        if (loadedFor != video.id) return@LaunchedEffect
        while (true) {
            val p = activePlayer.currentPosition
            val d = activePlayer.duration.coerceAtLeast(0L)
            if (p > 5_000) LocalStore.saveProgress(video.id, p, d)
            delay(5000)
        }
    }
    DisposableEffect(video.id) {
        onDispose {
            val p = activePlayer.currentPosition
            val d = activePlayer.duration.coerceAtLeast(0L)
            if (p > 0) kotlinx.coroutines.runBlocking { LocalStore.saveProgress(video.id, p, d) }
        }
    }

    val landscape = cfg.orientation == Configuration.ORIENTATION_LANDSCAPE
    LaunchedEffect(landscape) { if (landscape) full = true }

    BackHandler {
        when {
            full -> full = false
            showComments -> showComments = false
            else -> vm.minimizePlayer()
        }
    }

    val share = {
        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "https://youtu.be/${video.id}") }
        ctx.startActivity(Intent.createChooser(intent, "Paylaş"))
    }

    val inPip = activity?.isInPictureInPictureMode == true
    Surface(Modifier.fillMaxSize(), color = if (inPip) Color.Black else MaterialTheme.colorScheme.background) {
        if (inPip) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c -> PlayerView(c).apply { this.player = activePlayer; useController = false } },
                update = { it.player = activePlayer }
            )
            return@Surface
        }
        if (full) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { c -> PlayerView(c).apply { this.player = activePlayer; keepScreenOn = true; setFullscreenButtonClickListener { full = !full } } },
                    update = { it.player = activePlayer }
                )
            }
            return@Surface
        }
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { c -> PlayerView(c).apply { this.player = activePlayer; keepScreenOn = true; setFullscreenButtonClickListener { full = !full } } },
                    update = { it.player = activePlayer }
                )
                if (info == null && error == null) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                error?.let {
                    Column(Modifier.align(Alignment.Center).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(it, color = Color.White); Spacer(Modifier.height(8.dp))
                        Button(onClick = { error = null; loadedFor = null; info?.let { i -> info = i } ?: run { /* retry resolve */ } }) { Text("Tekrar dene") }
                    }
                }
                Row(Modifier.align(Alignment.TopStart).padding(4.dp)) {
                    IconButton(onClick = { vm.minimizePlayer() }) { Icon(Icons.Filled.ExpandMore, "Küçült", tint = Color.White) }
                }
            }

            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(info?.name ?: video.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp)
                        val stats = listOfNotNull(info?.viewCount?.takeIf { it >= 0 }?.let { fmtViews(it) }, info?.uploadDate?.toString() ?: video.published)
                            .filter { it.isNotBlank() }.joinToString(" • ")
                        Text(stats, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))

                        // Actions row
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(onClick = { vm.toggleLike(video.id); liked = !liked }, label = { Text(if (liked) "Beğenildi" else "Beğen") },
                                leadingIcon = { Icon(if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, null) })
                            AssistChip(onClick = share, label = { Text("Paylaş") }, leadingIcon = { Icon(Icons.Filled.Share, null) })
                            AssistChip(onClick = { vm.toggleWatchLater(video); savedWL = !savedWL }, label = { Text(if (savedWL) "Kaydedildi" else "Daha sonra") },
                                leadingIcon = { Icon(if (savedWL) Icons.Filled.WatchLater else Icons.Outlined.WatchLater, null) })
                            AssistChip(onClick = { vm.shuffle = !vm.shuffle }, label = { Text(if (vm.shuffle) "Karışık açık" else "Karıştır") }, leadingIcon = { Icon(Icons.Filled.Shuffle, null) })
                            AssistChip(onClick = { vm.repeatMode = (vm.repeatMode + 1) % 3 }, label = { Text(when (vm.repeatMode) { 1 -> "Tekrar: tümü"; 2 -> "Tekrar: biri"; else -> "Tekrar" }) },
                                leadingIcon = { Icon(if (vm.repeatMode == 2) Icons.Filled.RepeatOne else Icons.Filled.Repeat, null) })
                            AssistChip(onClick = { showSpeed = true }, label = { Text("${vm.playbackSpeed}x") }, leadingIcon = { Icon(Icons.Filled.Speed, null) })
                            AssistChip(onClick = { showQ = true }, label = { Text("Kalite ${maxH}p") }, leadingIcon = { Icon(Icons.Filled.Fullscreen, null) })
                            AssistChip(onClick = { showSubs = true }, label = { Text("Altyazı") }, leadingIcon = { Icon(Icons.Filled.Subtitles, null) })
                            AssistChip(onClick = { showDownload = true }, label = { Text("İndir") }, leadingIcon = { Icon(Icons.Filled.Download, null) })
                        }
                        if (showDownload) {
                            val streams = info?.videoStreams?.filter { it.isUrl }?.distinctBy { it.height }?.sortedByDescending { it.height }.orEmpty()
                            val scope = rememberCoroutineScope()
                            AlertDialog(onDismissRequest = { showDownload = false }, confirmButton = { TextButton(onClick = { showDownload = false }) { Text("Kapat") } },
                                title = { Text("İndirme kalitesi") }, text = {
                                    if (streams.isEmpty()) Text("İndirilebilir akış bulunamadı.")
                                    else Column {
                                        streams.take(8).forEach { s ->
                                            TextButton(onClick = {
                                                try {
                                                    val fileName = "cleantube_${video.id}_${s.height}p.mp4"
                                                    val req = DownloadManager.Request(Uri.parse(s.content))
                                                        .setTitle(info?.name ?: video.title)
                                                        .setDescription("CleanTube indiriyor (${s.height}p)")
                                                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                                                        .setAllowedOverMetered(true).setAllowedOverRoaming(false)
                                                    val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                                    val did = dm.enqueue(req)
                                                    scope.launch {
                                                        LocalStore.addDownload(DownloadItem(video.id, info?.name ?: video.title, fileName, "${s.height}p", did, status = "queued"))
                                                        LocalStore.addNotification("İndirme başlatıldı", "${info?.name ?: video.title} (${s.height}p)")
                                                    }
                                                    Toast.makeText(ctx, "İndirme kuyruğa eklendi", Toast.LENGTH_SHORT).show()
                                                } catch (_: Exception) { Toast.makeText(ctx, "İndirme başarısız (izin/depolama kontrol edin)", Toast.LENGTH_SHORT).show() }
                                                showDownload = false
                                            }) { Text("${s.height}p ${s.format?.name ?: ""}".trim()) }
                                        }
                                    }
                                })
                        }
                        if (showSubs) {
                            val subs = info?.subtitles.orEmpty()
                            AlertDialog(onDismissRequest = { showSubs = false }, confirmButton = { TextButton(onClick = { showSubs = false }) { Text("Kapat") } },
                                title = { Text("Altyazılar (CC)") }, text = {
                                    Column {
                                        TextButton(onClick = {
                                            activePlayer.trackSelectionParameters = activePlayer.trackSelectionParameters.buildUpon()
                                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                                            showSubs = false
                                        }) { Text("Kapalı") }
                                        if (subs.isEmpty()) Text("Bu video için altyazı bulunamadı.", modifier = Modifier.padding(top = 8.dp))
                                        else subs.take(30).forEach { s ->
                                            val lang = runCatching { s.languageTag }.getOrNull().orEmpty().ifBlank { "und" }
                                            TextButton(onClick = {
                                                activePlayer.trackSelectionParameters = activePlayer.trackSelectionParameters.buildUpon()
                                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                    .setPreferredTextLanguage(lang).build()
                                                showSubs = false
                                            }) { Text(lang) }
                                        }
                                        Text("\nNot: Oynatıcı kontrollerindeki CC düğmesi de kullanılabilir.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                })
                        }
                        if (showSpeed) {
                            AlertDialog(onDismissRequest = { showSpeed = false }, confirmButton = { TextButton(onClick = { showSpeed = false }) { Text("Tamam") } },
                                title = { Text("Oynatma hızı") }, text = {
                                    Column { listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                                        Row(Modifier.fillMaxWidth().clickable { vm.setSpeedValue(s); activePlayer.setPlaybackSpeed(s); showSpeed = false }.padding(12.dp)) {
                                            Text("${s}x", fontWeight = if (s == vm.playbackSpeed) FontWeight.Bold else FontWeight.Normal)
                                        }
                                    } }
                                })
                        }
                        if (showQ && info != null) {
                            DropdownMenu(showQ, { showQ = false }) {
                                Repo.qualities(info!!).forEach { q ->
                                    DropdownMenuItem(text = { Text("${q}p") }, onClick = { maxH = q; vm.setQuality(q); showQ = false })
                                }
                            }
                        }

                        // Channel row
                        Divider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .3f))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            val chName = info?.uploaderName ?: video.channel
                            val chUrl = info?.uploaderUrl ?: ""
                            Row(Modifier.weight(1f).clickable(enabled = chUrl.isNotBlank()) { if (chUrl.isNotBlank()) vm.openChannel(chUrl) }, verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), Alignment.Center) {
                                    Text(chName.take(1).ifBlank { "C" }.uppercase(), fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(chName, fontWeight = FontWeight.SemiBold)
                                    Text(if (subbed) "Abone olundu" else "Kanal", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Button(
                                onClick = { if (chUrl.isNotBlank()) { vm.toggleSub(chUrl.substringAfterLast("/")); subbed = !subbed } },
                                shape = RoundedCornerShape(20.dp),
                                colors = if (subbed) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)
                                else ButtonDefaults.buttonColors()
                            ) { Text(if (subbed) "Abone" else "Abone ol") }
                        }

                        // Description
                        val desc = info?.description?.content?.take(1000).orEmpty()
                        if (desc.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Surface(Modifier.fillMaxWidth().clickable { descExpanded = !descExpanded }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Text(desc, Modifier.padding(12.dp), maxLines = if (descExpanded) 50 else 3, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, lineHeight = 18.sp)
                            }
                        }

                        // Comments preview
                        Spacer(Modifier.height(12.dp))
                        Surface(Modifier.fillMaxWidth().clickable { showComments = true }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Yorumlar ${if (vm.comments.isNotEmpty()) "• ${vm.comments.size}" else ""}", fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(6.dp))
                                val top = vm.comments.firstOrNull()
                                if (vm.commentsLoading) Text("Yorumlar yükleniyor...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                else if (top != null) Text("${top.author}: ${top.text}", maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                                else Text("Yorumları görmek için dokun", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        Text("İlgili videolar", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
                if (info != null) {
                    items(Repo.related(info!!), key = { it.id }) { r ->
                        VideoRow(r, onClick = { vm.play(r, Repo.related(info!!)) }, onChannel = { if (r.channelId.isNotBlank()) vm.openChannel(r.channelId) })
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }

        if (showComments) {
            var commentText by remember(video.id) { mutableStateOf("") }
            var posting by remember(video.id) { mutableStateOf(false) }
            androidx.compose.material3.ModalBottomSheet(onDismissRequest = { showComments = false }) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Yorumlar", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    if (vm.loggedIn) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = commentText, onValueChange = { commentText = it }, modifier = Modifier.weight(1f), placeholder = { Text("Yorum ekle...") }, singleLine = true)
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = {
                                posting = true
                                vm.postComment(video.id, commentText) { ok ->
                                    posting = false
                                    if (ok) commentText = "" else Toast.makeText(ctx, "Yorum gönderilemedi", Toast.LENGTH_SHORT).show()
                                }
                            }, enabled = commentText.isNotBlank() && !posting) { Text(if (posting) "..." else "Gönder") }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (vm.commentsLoading) Loading()
                    else if (vm.comments.isEmpty()) Text("Yorum bulunamadı veya kapalı.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else LazyColumn(Modifier.fillMaxHeight(0.7f)) {
                        items(vm.comments, key = { it.id + it.author }) { c ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                                Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), Alignment.Center) {
                                    Text(c.author.take(1).ifBlank { "?" }.uppercase(), fontSize = 12.sp)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("${c.author}  ${c.published}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(c.text, fontSize = 14.sp)
                                    if (c.likes.isNotBlank()) Text("👍 ${c.likes}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                        }
                        if (vm.commentsNextPage != null) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                    if (vm.commentsLoadingMore) CircularProgressIndicator(Modifier.size(24.dp))
                                    else TextButton(onClick = { vm.commentsLoadMore() }) { Text("Daha fazla yorum yükle") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
