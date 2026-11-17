@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.cleantube.app.ui

import android.content.ComponentName
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.cleantube.app.R
import com.cleantube.app.data.Prefs
import com.cleantube.app.player.PlaybackService
import kotlinx.coroutines.delay

fun fmtTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** Siradaki oynatma kaynagini deneyen basit zincir (progressive -> birlestirme -> DASH -> HLS). */
internal class SourceChain {
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

/**
 * Servise MediaController (SessionToken) ile baglanir ve servisin ExoPlayer'ini verir.
 * Ekran kapaninca yalnizca controller birakilir; oynatici release EDILMEZ.
 */
@Composable
fun rememberServicePlayer(onFail: (String) -> Unit): ExoPlayer? {
    val ctx = LocalContext.current
    val fail by rememberUpdatedState(onFail)
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
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
                return@LaunchedEffect
            }
            delay(50)
        }
        fail("Medya servisi başlatılamadı")
    }
    return player
}


/** Shorts akisi icin servis omurlu, ana oynaticidan bagimsiz ExoPlayer. */
@Composable
fun rememberShortsPlayer(): ExoPlayer? {
    val ctx = LocalContext.current
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    DisposableEffect(Unit) {
        var future: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
        try {
            val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
            future = MediaController.Builder(ctx, token).buildAsync()
        } catch (t: Throwable) {
            Log.w("VYRO", "Shorts medya servisi baglanamadi", t)
        }
        onDispose { future?.let { runCatching { MediaController.releaseFuture(it) } } }
    }
    LaunchedEffect(Unit) {
        repeat(100) {
            PlaybackService.sharedShortsPlayer?.let {
                player = it
                return@LaunchedEffect
            }
            delay(50)
        }
    }
    return player
}

/** YouTube tarzı kontroller: oynat/duraklat, 5 saniyelik ileri/geri ve ardışık çift dokunuşta 5/10/15 saniyelik arama. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerOverlay(
    player: ExoPlayer,
    full: Boolean,
    onClose: () -> Unit,
    onFullscreen: () -> Unit,
    onSettings: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    var visible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var buffering by remember { mutableStateOf(player.playbackState == Player.STATE_BUFFERING) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var scrub by remember { mutableStateOf<Float?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var feedbackKey by remember { mutableIntStateOf(0) }
    var touchKey by remember { mutableIntStateOf(0) }
    var forwardTapCount by remember { mutableIntStateOf(0) }
    var backwardTapCount by remember { mutableIntStateOf(0) }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(p: Boolean) { isPlaying = p }
            override fun onPlaybackStateChanged(s: Int) { buffering = s == Player.STATE_BUFFERING }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    LaunchedEffect(player) {
        while (true) {
            pos = player.currentPosition.coerceAtLeast(0)
            dur = player.duration.takeIf { it > 0 } ?: 0L
            delay(400)
        }
    }
    LaunchedEffect(visible, isPlaying, touchKey) {
        if (visible && isPlaying && scrub == null) { delay(3500); visible = false }
    }
    LaunchedEffect(feedbackKey) { if (feedbackKey > 0) { delay(700); feedback = null } }
    LaunchedEffect(forwardTapCount) { if (forwardTapCount > 0) { delay(1200); forwardTapCount = 0 } }
    LaunchedEffect(backwardTapCount) { if (backwardTapCount > 0) { delay(1200); backwardTapCount = 0 } }

    fun seekBy(d: Long) {
        val max = if (dur > 0) dur else Long.MAX_VALUE
        val t = (player.currentPosition + d).coerceIn(0L, max)
        player.seekTo(t); pos = t
        feedback = if (d < 0) "« ${kotlin.math.abs(d) / 1000} sn" else "${d / 1000} sn »"; feedbackKey++
    }
    fun togglePlay() {
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        if (player.isPlaying) player.pause() else player.play()
        touchKey++
    }

    Box(
        modifier.pointerInput(Unit) {
            detectTapGestures(
                onTap = { visible = !visible; touchKey++ },
                onDoubleTap = { o ->
                    if (o.x < size.width / 2f) { backwardTapCount++; forwardTapCount = 0; seekBy(-5_000L * backwardTapCount) }
                    else { forwardTapCount++; backwardTapCount = 0; seekBy(5_000L * forwardTapCount) }
                },
            )
        }
    ) {
        val shownPos = scrub?.let { (it * dur).toLong() } ?: pos
        if (visible) {
            Box(Modifier.fillMaxSize().background(Color(0x66000000)))
            Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.KeyboardArrowDown, "Küçült", tint = Color.White, modifier = Modifier.size(34.dp)) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Ayarlar", tint = Color.White) }
            }
            Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(34.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleButton(44.dp, { seekBy(-5_000) }) { Text("«5", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                CircleButton(64.dp, { togglePlay() }) {
                    when {
                        buffering -> CircularProgressIndicator(Modifier.size(34.dp), color = Color.White, strokeWidth = 3.dp)
                        isPlaying -> Icon(painterResource(R.drawable.ic_pause), "Duraklat", tint = Color.White, modifier = Modifier.size(38.dp))
                        else -> Icon(Icons.Filled.PlayArrow, "Oynat", tint = Color.White, modifier = Modifier.size(42.dp))
                    }
                }
                CircleButton(44.dp, { seekBy(5_000) }) { Text("5»", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            }
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${fmtTime(shownPos)} / ${fmtTime(dur)}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onFullscreen) {
                        Icon(painterResource(if (full) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen), "Tam ekran", tint = Color.White)
                    }
                }
                Slider(
                    value = scrub ?: if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                    onValueChange = { scrub = it; touchKey++ },
                    onValueChangeFinished = { scrub?.let { player.seekTo((it * dur).toLong()) }; scrub = null },
                    enabled = dur > 0,
                    modifier = Modifier.fillMaxWidth().height(22.dp).padding(horizontal = 4.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = accent, activeTrackColor = accent,
                        inactiveTrackColor = Color(0x55FFFFFF),
                        disabledThumbColor = Color.Transparent, disabledActiveTrackColor = Color(0x55FFFFFF),
                        disabledInactiveTrackColor = Color(0x55FFFFFF),
                    ),
                )
            }
        } else if (dur > 0) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.dp).background(Color(0x55FFFFFF))) {
                Box(Modifier.fillMaxWidth((pos.toFloat() / dur).coerceIn(0f, 1f)).fillMaxHeight().background(accent))
            }
            if (buffering) CircularProgressIndicator(Modifier.align(Alignment.Center).size(34.dp), color = Color.White, strokeWidth = 3.dp)
        }
        feedback?.let { f ->
            Text(
                f, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                modifier = Modifier.align(if (f.startsWith("«")) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 40.dp)
                    .background(Color(0x99000000), RoundedCornerShape(24.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun CircleButton(size: androidx.compose.ui.unit.Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Color(0x66000000)).clickable(onClick = onClick),
        Alignment.Center,
    ) { content() }
}

/** Kalite secimi. 0 = Otomatik (veri tasarrufu). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualitySheet(options: List<Int>, selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF1C1B22)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
            Text("Kalite", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
            Text(
                "Düşük kalite daha az internet harcar.", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp),
            )
            (listOf(0) + options.filter { it > 0 }.distinct().sorted()).forEach { q ->
                Row(
                    Modifier.fillMaxWidth().clickable { onSelect(q) }.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (q == 0) "Otomatik (veri tasarrufu, ${Prefs.AUTO_HEIGHT}p)" else "${q}p", Modifier.weight(1f), fontSize = 16.sp)
                    if (selected == q) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
