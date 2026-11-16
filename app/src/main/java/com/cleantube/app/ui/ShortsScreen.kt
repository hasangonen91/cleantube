@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.cleantube.app.ui

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.cleantube.app.R
import com.cleantube.app.data.HttpDownloader
import com.cleantube.app.data.Prefs
import com.cleantube.app.data.Repo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Dikey Shorts akisi: kaydirinca bir sonraki video DIREKT oynar (dongu), dokununca duraklar. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortsScreen(vm: MainVM) {
    if (vm.shorts.isEmpty()) {
        if (vm.shortsLoading) Loading() else ErrorView("Shorts şu an yüklenemedi.", { vm.loadShorts() })
        return
    }
    val ctx = LocalContext.current
    val pager = rememberPagerState(
        initialPage = vm.shortsIndex.coerceIn(0, vm.shorts.size - 1),
        pageCount = { vm.shorts.size },
    )
    val page = pager.currentPage
    val player = rememberServicePlayer { }
    val active = vm.tab == 1 && vm.playing == null && !vm.showLogin
    var quality by remember { mutableIntStateOf(Prefs.quality) }
    var showQ by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var userPaused by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var ready by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val chain = remember { SourceChain() }

    LaunchedEffect(page) {
        vm.shortsIndex = page
        if (page >= vm.shorts.size - 3) vm.moreShorts()
    }

    // Servis oynaticisini dinle (hata -> siradaki kaynak; ilk kare -> kucuk resmi gizle).
    DisposableEffect(player) {
        val p = player
        val l = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                Log.w("VYRO", "Shorts oynatma hatasi: ${e.errorCodeName}", e)
                if (p != null && !chain.tryNext(p)) error = "Bu Shorts oynatılamadı. Kaydırarak devam et."
            }
            override fun onRenderedFirstFrame() { ready = true }
        }
        p?.addListener(l)
        onDispose { p?.removeListener(l) }
    }
    // Sekmeden ayrilinca (ve baska video acik degilse) oynatmayi durdur.
    DisposableEffect(player) {
        val p = player
        onDispose {
            if (p != null && vm.playing == null) runCatching {
                p.stop(); p.clearMediaItems(); p.repeatMode = Player.REPEAT_MODE_OFF; p.volume = 1f
            }
        }
    }
    // Sayfa degisince / kalite degisince yeni Shorts'u direkt oynat; sonrakini onceden coz.
    LaunchedEffect(player, active, page, quality) {
        val p = player ?: return@LaunchedEffect
        if (!active) { p.pause(); return@LaunchedEffect }
        val video = vm.shorts.getOrNull(page) ?: return@LaunchedEffect
        error = null; ready = false; userPaused = false; progress = 0f
        runCatching {
            p.stop(); p.clearMediaItems()
            p.repeatMode = Player.REPEAT_MODE_ONE
            p.volume = if (muted) 0f else 1f
        }
        try {
            val info = vm.streamInfo(video.id)
            val factory = DefaultHttpDataSource.Factory()
                .setUserAgent(HttpDownloader.USER_AGENT)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(20_000)
                .setReadTimeoutMs(30_000)
            val list = Repo.candidates(info, Prefs.heightFor(quality), factory)
            if (list.isEmpty()) { error = "Bu Shorts için akış bulunamadı."; return@LaunchedEffect }
            chain.reset(list, 0L)
            chain.tryNext(p)
            vm.shorts.getOrNull(page + 1)?.let { n -> launch { runCatching { vm.streamInfo(n.id) } } }
        } catch (e: CancellationException) { throw e }
        catch (t: Throwable) {
            Log.w("VYRO", "Shorts cozulemedi", t)
            error = "Bu Shorts açılamadı. Kaydırarak devam et."
        }
    }
    LaunchedEffect(player) {
        val p = player ?: return@LaunchedEffect
        while (true) {
            val d = p.duration
            progress = if (d > 0) (p.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
            delay(250)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondBoundsPageCount = 0) { i ->
            val video = vm.shorts.getOrNull(i) ?: return@VerticalPager
            val current = i == page
            Box(
                Modifier.fillMaxSize().pointerInput(current) {
                    detectTapGestures(onTap = {
                        val p = player
                        if (current && p != null) {
                            p.playWhenReady = !p.playWhenReady
                            userPaused = !p.playWhenReady
                        }
                    })
                }
            ) {
                if (!(current && ready)) {
                    AsyncImage(video.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.55f)
                }
                val exo = player
                if (current && exo != null && active) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { c ->
                            PlayerView(c).apply {
                                this.player = exo
                                useController = false
                                keepScreenOn = true
                                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                            }
                        },
                        update = { it.player = exo },
                        onRelease = { it.player = null },
                    )
                }
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(260.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                )
                if (current && userPaused) {
                    Icon(Icons.Filled.PlayArrow, "Duraklatıldı", tint = Color.White,
                        modifier = Modifier.align(Alignment.Center).size(84.dp).background(Color(0x66000000), CircleShape))
                }
                if (current && error != null) {
                    Text(error!!, color = Color.White, fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp).background(Color(0xCC000000)).padding(10.dp))
                }
                // Alt-sol: kanal + baslik
                Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 84.dp, bottom = 22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), Alignment.Center) {
                            Text(video.channel.take(1).ifBlank { "C" }.uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("@" + video.channel.replace(" ", ""), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(video.title, color = Color.White, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                // Sag: paylas + ses
                Column(Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ShortAction("Paylaş", { Icon(Icons.Filled.Share, "Paylaş", tint = Color.White) }) {
                        try {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, "https://www.youtube.com/shorts/${video.id}")
                            ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: Throwable) {}
                    }
                    Spacer(Modifier.height(10.dp))
                    ShortAction(if (muted) "Ses kapalı" else "Ses", {
                        Icon(painterResource(if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume_up), "Ses", tint = Color.White)
                    }) {
                        muted = !muted
                        player?.volume = if (muted) 0f else 1f
                    }
                }
                // Alt ilerleme cubugu
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.dp).background(Color(0x44FFFFFF))) {
                    if (current) Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Color.White))
                }
            }
        }
        // Ust baslik: arama + ayarlar (YouTube Shorts gibi)
        Row(Modifier.align(Alignment.TopEnd).padding(horizontal = 4.dp, vertical = 4.dp)) {
            IconButton(onClick = { vm.tab = 2 }) { Icon(Icons.Filled.Search, "Ara", tint = Color.White) }
            IconButton(onClick = { showQ = true }) { Icon(Icons.Filled.MoreVert, "Ayarlar", tint = Color.White) }
        }
    }
    if (showQ) {
        QualitySheet(
            options = Prefs.DEFAULT_QUALITIES, selected = quality,
            onSelect = { Prefs.quality = it; quality = it; showQ = false },
            onDismiss = { showQ = false },
        )
    }
}

@Composable
private fun ShortAction(label: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) { icon() }
        Text(label, color = Color.White, fontSize = 11.sp)
    }
}
