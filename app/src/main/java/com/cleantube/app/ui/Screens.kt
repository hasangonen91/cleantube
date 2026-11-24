package com.cleantube.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.ui.unit.dp
import com.cleantube.app.data.LocalStore
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.cleantube.app.data.*

private val categories = listOf("Senin için", "Mixler", "Müzik", "Canlı", "Oyun", "Podcast'ler", "Teknoloji", "Haberler", "Spor")

@Composable
fun Loading() = Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }

@Composable
fun ErrorView(msg: String, onRetry: (() -> Unit)?) = Column(
    Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally,
) {
    Text(msg, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (onRetry != null) { Spacer(Modifier.height(12.dp)); Button(onClick = onRetry) { Text("Tekrar dene") } }
}

@Composable
fun VideoCard(v: Video, onClick: () -> Unit, onChannel: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(bottom = 18.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceVariant)) {
            AsyncImage(v.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (v.durationText.isNotBlank() || v.isLive) {
                val label = if (v.isLive) "CANLI" else v.durationText
                Text(
                    label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                        .background(if (v.isLive) Color(0xFFCC0000) else Color(0xCC000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                    .then(if (onChannel != null && v.channelId.isNotBlank()) Modifier.clickable { onChannel() } else Modifier),
                Alignment.Center
            ) {
                if (v.channelAvatar.isNotBlank()) AsyncImage(v.channelAvatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Text(v.channel.take(1).ifBlank { "C" }.uppercase(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(3.dp))
                Text(v.meta, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
fun VideoRow(v: Video, onClick: () -> Unit, onChannel: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Box(Modifier.width(168.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            AsyncImage(v.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (v.durationText.isNotBlank()) {
                Text(v.durationText, color = Color.White, fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(v.channel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = if (onChannel != null && v.channelId.isNotBlank()) Modifier.clickable { onChannel() } else Modifier)
            Text(listOf(v.views, v.published).filter { it.isNotBlank() }.joinToString(" • "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainVM) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.PlayCircle, "CleanTube", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(6.dp))
            Text("CleanTube", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.showNotifications = true }) { Icon(Icons.Filled.Notifications, "Bildirimler") }
            IconButton(onClick = { vm.showSearch = true }) { Icon(Icons.Filled.Search, "Ara") }
            IconButton(onClick = { vm.showCastInfo = true }) { Icon(Icons.Filled.Cast, "Yayınla (Cast)") }
            IconButton(onClick = { vm.showSettings = true }) { Icon(Icons.Filled.Settings, "Ayarlar") }
            Box(Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).clickable { if (!vm.loggedIn) vm.showLogin = true else vm.tab = RootTab.LIBRARY }, Alignment.Center) {
                Text(if (vm.loggedIn) "H" else "?", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(4.dp))
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEachIndexed { index, category ->
                FilterChip(
                    selected = vm.categoryIndex == index,
                    onClick = { vm.loadCategory(index, category) },
                    label = { Text(category, fontSize = 13.sp) },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.onSurface, selectedLabelColor = MaterialTheme.colorScheme.surface)
                )
            }
        }
        when {
            vm.homeLoading && vm.home.isEmpty() -> Loading()
            vm.homeError != null && vm.home.isEmpty() -> ErrorView(vm.homeError!!) { vm.loadHome() }
            else -> {
                val listState = rememberLazyListState()
                LaunchedEffect(listState, vm.home.size, vm.homeNextPage, vm.homeLoadingMore) {
                    snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                        .collect { last ->
                            if (vm.homeNextPage != null && !vm.homeLoadingMore && last >= (listState.layoutInfo.totalItemsCount - 4).coerceAtLeast(0)) {
                                vm.homeLoadMore()
                            }
                        }
                }
                LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    if (vm.categoryIndex == 0 && vm.shorts.isNotEmpty()) item { ShortsShelf(vm) }
                    if (vm.homePlaylists.isNotEmpty()) {
                        item { Text("Mixler ve oynatma listeleri", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(vm.homePlaylists, key = { "hp_${it.id}" }) { p ->
                                    Column(Modifier.width(220.dp).clip(RoundedCornerShape(12.dp)).clickable { vm.playPlaylist(p) }.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.35f))) {
                                        Box(Modifier.fillMaxWidth().height(124.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                            if (p.thumb.isNotBlank()) AsyncImage(p.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                            Box(Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color(0xCC000000), RoundedCornerShape(6.dp)).padding(horizontal=6.dp, vertical=2.dp)) { Text(p.count.ifBlank { "Mix" }, color=Color.White, fontSize=11.sp) }
                                        }
                                        Column(Modifier.padding(8.dp)) {
                                            Text(p.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                            if (p.author.isNotBlank()) Text(p.author, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    items(vm.home, key = { "h_${it.id}" }) { v ->
                        VideoCard(v, onClick = { vm.play(v, vm.home) }, onChannel = { if (v.channelId.isNotBlank()) vm.openChannel(v.channelId) else if (v.channel.isNotBlank()) vm.openChannel(v.channel) })
                    }
                    if (vm.homeNextPage != null) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                if (vm.homeLoadingMore) CircularProgressIndicator(Modifier.size(28.dp))
                                else OutlinedButton(onClick = { vm.homeLoadMore() }) { Text("Daha fazla göster") }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ShortsShelf(vm: MainVM) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.PlayCircle, null, tint = Color(0xFFFF0033), modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text("Shorts", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(vm.shorts.take(12), key = { it.id }) { video ->
                Column(Modifier.width(150.dp).clickable { vm.play(video, vm.shorts) }) {
                    Box(Modifier.fillMaxWidth().height(266.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        AsyncImage(video.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xAA000000)))))
                        Column(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                            Text(video.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(video.views, color = Color.White.copy(alpha = .8f), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        Divider(Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .2f))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortsScreen(vm: MainVM) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val items = vm.shorts
    if (items.isEmpty()) {
        if (vm.shortsLoading) Loading() else ErrorView(vm.shortsError ?: "Shorts yüklenemedi.", { vm.loadShorts() })
        return
    }
    val pager = rememberPagerState(pageCount = { items.size.coerceAtLeast(1) })
    var settledPage by remember { mutableIntStateOf(0) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }

    LaunchedEffect(Unit) {
        runCatching { context.startService(android.content.Intent(context, com.cleantube.app.player.PlaybackService::class.java)) }
        repeat(80) { com.cleantube.app.player.PlaybackService.sharedPlayer?.let { player = it; return@LaunchedEffect }; kotlinx.coroutines.delay(50) }
    }

    LaunchedEffect(pager, items.size, vm.shortsNextPage, vm.shortsLoadingMore) {
        snapshotFlow { runCatching { pager.settledPage }.getOrDefault(0) }.collect { page ->
            settledPage = page
            if (vm.shortsNextPage != null && !vm.shortsLoadingMore && page >= items.size - 3) vm.shortsLoadMore()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val p = player
            if (p != null) { runCatching { p.stop(); p.clearMediaItems() } }
        }
    }

    VerticalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { items.getOrNull(it)?.id ?: it }) { page ->
        val video = items.getOrNull(page) ?: return@VerticalPager
        val isActive = page == settledPage
        ShortPage(
            vm = vm,
            video = video,
            isActive = isActive,
            player = if (isActive) player else null,
            onShare = {
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, "https://youtu.be/${video.id}") }
                context.startActivity(android.content.Intent.createChooser(intent, "Paylaş"))
            }
        )
    }
}

@Composable
private fun ShortPage(vm: MainVM, video: Video, isActive: Boolean, player: ExoPlayer?, onShare: () -> Unit) {
    var loading by remember(video.id, isActive) { mutableStateOf(isActive) }
    var error by remember(video.id, isActive) { mutableStateOf<String?>(null) }
    var isPlaying by remember(video.id, isActive) { mutableStateOf(false) }
    var loadedFor by remember(video.id) { mutableStateOf(false) }
    var retryTick by remember(video.id) { mutableIntStateOf(0) }

    LaunchedEffect(isActive, player, video.id, retryTick) {
        val p = player ?: return@LaunchedEffect
        if (!isActive) return@LaunchedEffect
        loading = true; error = null
        try {
            val info = Repo.resolve(video.id)
            val factory = DefaultHttpDataSource.Factory()
                .setUserAgent(HttpDownloader.USER_AGENT).setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(20_000).setReadTimeoutMs(30_000)
            val src = Repo.buildSource(info, vm.defaultQuality, factory) ?: run { error = "Akış bulunamadı"; loading = false; return@LaunchedEffect }
            p.stop(); p.clearMediaItems()
            p.setMediaSource(src)
            p.repeatMode = Player.REPEAT_MODE_ONE
            p.prepare()
            p.playWhenReady = true
            loadedFor = true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = e.message ?: "Short oynatılamadı"
        } finally { loading = false }
    }

    DisposableEffect(isActive, player, video.id) {
        val p = player ?: return@DisposableEffect onDispose {}
        if (!isActive) return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlayerError(e: androidx.media3.common.PlaybackException) { error = e.localizedMessage ?: "Oynatma hatası" }
        }
        p.addListener(listener)
        isPlaying = p.isPlaying
        onDispose { runCatching { p.removeListener(listener) } }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (isActive && player != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                        keepScreenOn = true
                    }
                },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize()
            )
            if (!isPlaying) {
                // Tap to pause/play
                Box(Modifier.fillMaxSize().clickable {
                    if (player.isPlaying) player.pause() else player.play()
                })
            } else {
                Box(Modifier.fillMaxSize().clickable {
                    if (player.isPlaying) player.pause() else player.play()
                })
            }
        } else {
            AsyncImage(video.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }

        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color(0xCC000000)))))
        if (isActive && loading) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        if (isActive && error != null) {
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error ?: "Hata", color = Color.White)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { error = null; loadedFor = false; retryTick++ }) { Text("Tekrar dene") }
            }
        }
        if (isActive && !loading && error == null && !isPlaying && loadedFor) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Oynat", tint = Color.White.copy(alpha = .9f), modifier = Modifier.align(Alignment.Center).size(64.dp).background(Color(0x66000000), CircleShape).padding(12.dp))
        }

        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 16.dp, end = 72.dp, bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(Color.Gray), Alignment.Center) { Text(video.channel.take(1).uppercase(), color = Color.White, fontSize = 12.sp) }
                Spacer(Modifier.width(8.dp))
                Text(video.channel, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { if (video.channelId.isNotBlank()) vm.toggleSub(video.channelId) }, shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp), modifier = Modifier.height(30.dp)) {
                    Text("Abone ol", fontSize = 12.sp, color = Color.White)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(video.title, color = Color.White, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Column(Modifier.align(Alignment.BottomEnd).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            IconButton(onClick = { if (isActive && player != null) { if (player.isPlaying) player.pause() else player.play() } }) {
                Icon(if (isActive && isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Oynat/Duraklat", tint = Color.White, modifier = Modifier.size(30.dp))
            }
            IconButton(onClick = { vm.toggleLike(video.id) }) { Icon(Icons.Filled.ThumbUp, "Beğen", tint = Color.White) }
            IconButton(onClick = { vm.play(video, vm.shorts) }) { Icon(Icons.Filled.Comment, "Yorumlar", tint = Color.White) }
            IconButton(onClick = onShare) { Icon(Icons.Filled.Share, "Paylaş", tint = Color.White) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(vm: MainVM) {
    LaunchedEffect(Unit) { vm.loadSubs() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Abonelikler", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.showSettings = true }) { Icon(Icons.Filled.Settings, null) }
        }
        if (!vm.loggedIn && vm.subsFeed.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Subscriptions, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text("Yeni videoları kaçırma", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Favori kanallarının videolarını görmek için giriş yap.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { vm.showLogin = true }) { Text("Giriş yap") }
            }
            return@Column
        }
        when {
            vm.subsLoading -> Loading()
            vm.subsFeed.isEmpty() -> ErrorView("Abonelik akışı boş. Kanalları takip et veya giriş yap.", { vm.loadSubs() })
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(vm.subsFeed, key = { "s_${it.id}" }) { v -> VideoCard(v, { vm.play(v, vm.subsFeed) }) { if (v.channelId.isNotBlank()) vm.openChannel(v.channelId) } }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
fun SearchScreen(vm: MainVM) {
    var active by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = vm.query,
                onValueChange = { vm.onQueryChange(it); active = true },
                modifier = Modifier.weight(1f),
                placeholder = { Text("CleanTube'da ara") }, singleLine = true,
                shape = RoundedCornerShape(28.dp),
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (vm.query.isNotBlank()) IconButton(onClick = { vm.query = ""; vm.suggestions = emptyList() }) { Icon(Icons.Filled.Close, null) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { active = false; vm.search() }),
            )
        }
        if (active && (vm.suggestions.isNotEmpty() || vm.searchHistory.isNotEmpty()) && vm.query.length < 2 || (active && vm.suggestions.isNotEmpty())) {
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                items(vm.suggestions, key = { "sg_$it" }) { s ->
                    ListItem(headlineContent = { Text(s) }, leadingContent = { Icon(Icons.Filled.Search, null) },
                        modifier = Modifier.clickable { active = false; vm.search(s) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                }
                if (vm.query.isBlank()) items(vm.searchHistory, key = { "sh_$it" }) { h ->
                    ListItem(headlineContent = { Text(h) }, leadingContent = { Icon(Icons.Filled.History, null) },
                        trailingContent = { IconButton(onClick = { vm.removeSearchHistoryItem(h) }) { Icon(Icons.Filled.Close, null) } },
                        modifier = Modifier.clickable { active = false; vm.search(h) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                }
            }
        }
        when {
            vm.searching -> Loading()
            vm.searchError != null -> ErrorView(vm.searchError!!) { vm.search() }
            vm.results.isEmpty() && vm.searchChannels.isEmpty() -> {
                if (vm.query.isBlank()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Son aramalar", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        vm.searchHistory.forEach { h ->
                            Row(Modifier.fillMaxWidth().clickable { vm.search(h) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(12.dp)); Text(h)
                            }
                        }
                    }
                } else Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Sonuç bulunamadı", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            else -> {
                val listState = rememberLazyListState()
                LaunchedEffect(listState, vm.results.size, vm.searchNextPage, vm.searchLoadingMore) {
                    snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
                        if (vm.searchNextPage != null && !vm.searchLoadingMore && last >= (listState.layoutInfo.totalItemsCount - 4).coerceAtLeast(0)) vm.searchLoadMore()
                    }
                }
                LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    if (vm.searchChannels.isNotEmpty()) {
                        item { Text("Kanallar", Modifier.padding(12.dp), fontWeight = FontWeight.Bold) }
                        items(vm.searchChannels.take(3), key = { "c_${it.id}" }) { c ->
                            ListItem(
                                headlineContent = { Text(c.name) },
                                supportingContent = { Text(c.subscribers) },
                                leadingContent = { Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), Alignment.Center) { if (c.avatar.isNotBlank()) AsyncImage(c.avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Text(c.name.take(1).uppercase()) } },
                                trailingContent = { OutlinedButton(onClick = { vm.openChannel(c.id) }) { Text("Görüntüle") } },
                                modifier = Modifier.clickable { vm.openChannel(c.id) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                    if (vm.searchPlaylists.isNotEmpty()) {
                        item { Text("Oynatma listeleri / Mixler", Modifier.padding(12.dp), fontWeight = FontWeight.Bold) }
                        items(vm.searchPlaylists.take(6), key = { "p_${it.id}" }) { p ->
                            ListItem(
                                headlineContent = { Text(p.title) },
                                supportingContent = { Text(listOf(p.author, p.count).filter { it.isNotBlank() }.joinToString(" • ")) },
                                leadingContent = {
                                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), Alignment.Center) {
                                        if (p.thumb.isNotBlank()) AsyncImage(p.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        else Icon(Icons.Filled.PlaylistPlay, null)
                                    }
                                },
                                modifier = Modifier.clickable { vm.playPlaylist(p) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                    items(vm.results, key = { "r_${it.id}" }) { v -> VideoRow(v, { vm.play(v, vm.results) }) { if (v.channelId.isNotBlank()) vm.openChannel(v.channelId) } }
                    if (vm.searchNextPage != null) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                if (vm.searchLoadingMore) CircularProgressIndicator(Modifier.size(28.dp))
                                else OutlinedButton(onClick = { vm.searchLoadMore() }) { Text("Daha fazla yükle") }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

private val sections = listOf(
    Section("Geçmiş (cihaz)", "LOCAL_HISTORY"),
    Section("Daha sonra izle (yerel)", "LOCAL_WL"),
    Section("Geçmiş (hesap)", "FEhistory"),
    Section("Oynatma listeleri", "", isPlaylists = true),
    Section("Daha sonra izle (hesap)", "VLWL"),
    Section("Beğendiğim videolar", "VLLL"),
    Section("Beğendiğim müzikler", "VLLM"),
)

@Composable
fun LibraryScreen(vm: MainVM) {
    LaunchedEffect(Unit) { vm.refreshLocal() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Kitaplık", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.showDownloads = true }) { Icon(Icons.Filled.Download, "İndirmeler") }
            IconButton(onClick = { vm.showSettings = true }) { Icon(Icons.Filled.Settings, "Ayarlar") }
            if (vm.loggedIn) IconButton(onClick = { vm.logout() }) { Icon(Icons.Filled.Logout, "Çıkış") }
            else TextButton(onClick = { vm.showLogin = true }) { Text("Giriş yap") }
        }
        val s = vm.section
        BackHandler(enabled = s != null) { vm.openSection(null) }
        if (s == null) {
            LazyColumn(Modifier.fillMaxSize()) {
                if (vm.localHistory.isNotEmpty()) {
                    item { Text("Son izlenenler", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontWeight = FontWeight.SemiBold) }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(vm.localHistory.take(10), key = { it.id }) { v ->
                                Column(Modifier.width(160.dp).clickable { vm.play(v, vm.localHistory) }) {
                                    Box(Modifier.fillMaxWidth().aspectRatio(16f/9f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                        AsyncImage(v.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                items(sections) { sec ->
                    ListItem(
                        leadingContent = { Icon(if (sec.isPlaylists) Icons.Filled.PlaylistPlay else Icons.Filled.History, null) },
                        headlineContent = { Text(sec.title) },
                        modifier = Modifier.clickable { vm.openSection(sec) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.openSection(null) }) { Icon(Icons.Filled.ArrowBack, "Geri") }
                Text(s.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            when {
                vm.libLoading -> Loading()
                vm.libError != null -> ErrorView(vm.libError!!) { vm.openSection(s) }
                s.isPlaylists -> LazyColumn(Modifier.fillMaxSize()) {
                    if (vm.libPlaylists.isEmpty()) item { ErrorView("Oynatma listesi bulunamadı (giriş gerekebilir).", null) }
                    items(vm.libPlaylists, key = { it.id }) { p ->
                        ListItem(headlineContent = { Text(p.title) }, supportingContent = { if (p.count.isNotBlank()) Text(p.count) },
                            leadingContent = { Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), Alignment.Center) { if (p.thumb.isNotBlank()) AsyncImage(p.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Icon(Icons.Filled.PlaylistPlay, null) } },
                            modifier = Modifier.clickable { vm.playPlaylist(p) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                    }
                    if (vm.libContinuation != null) item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            if (vm.libLoadingMore) CircularProgressIndicator(Modifier.size(24.dp)) else OutlinedButton(onClick = { vm.sectionLoadMore() }) { Text("Daha fazla yükle") }
                        }
                    }
                }
                vm.libVideos.isEmpty() -> ErrorView("Burada içerik yok.", null)
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(vm.libVideos, key = { it.id }) { v ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { VideoRow(v, { vm.play(v, vm.libVideos) }) }
                            if (s.browseId == "LOCAL_HISTORY") IconButton(onClick = { vm.removeHistoryItem(v.id) }) { Icon(Icons.Filled.Delete, "Sil") }
                            if (s.browseId == "LOCAL_WL") IconButton(onClick = { vm.toggleWatchLater(v) }) { Icon(Icons.Filled.Delete, "Kaldır") }
                        }
                    }
                    if (vm.libContinuation != null) item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            if (vm.libLoadingMore) CircularProgressIndicator(Modifier.size(24.dp)) else OutlinedButton(onClick = { vm.sectionLoadMore() }) { Text("Daha fazla yükle") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelScreen(vm: MainVM) {
    val info = vm.channelInfo
    BackHandler { vm.closeChannel() }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        when {
            vm.channelLoading -> { Row(Modifier.padding(8.dp)) { IconButton(onClick = { vm.closeChannel() }) { Icon(Icons.Filled.ArrowBack, null) } }; Loading() }
            vm.channelError != null -> { Row(Modifier.padding(8.dp)) { IconButton(onClick = { vm.closeChannel() }) { Icon(Icons.Filled.ArrowBack, null) } }; ErrorView(vm.channelError!!, null) }
            info != null -> {
                val listState = rememberLazyListState()
                LaunchedEffect(listState, vm.channelVideos.size, vm.channelNextPage, vm.channelLoadingMore) {
                    snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
                        if (vm.channelNextPage != null && !vm.channelLoadingMore && last >= (listState.layoutInfo.totalItemsCount - 3).coerceAtLeast(0)) vm.channelLoadMore()
                    }
                }
                LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    item {
                        val banner = runCatching { info.javaClass.methods.firstOrNull { m -> m.name == "getBannerUrl" }?.invoke(info) as? String }.getOrNull().orEmpty()
                        val avatar = runCatching { info.javaClass.methods.firstOrNull { m -> m.name == "getAvatarUrl" }?.invoke(info) as? String }.getOrNull().orEmpty()
                        Box(Modifier.fillMaxWidth().height(120.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                            if (banner.isNotBlank()) AsyncImage(banner, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            IconButton(onClick = { vm.closeChannel() }, modifier = Modifier.padding(4.dp)) { Icon(Icons.Filled.ArrowBack, "Geri", tint = Color.White) }
                        }
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), Alignment.Center) {
                                if (avatar.isNotBlank()) AsyncImage(avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                else Text(info.name.take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = 22.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(info.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                val sub = if (info.subscriberCount >= 0) fmtCount(info.subscriberCount) + " abone" else ""
                                Text(listOf(info.id, sub).filter { it.isNotBlank() }.joinToString(" • "), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        val chId = info.url.substringAfterLast("/")
                        Row(Modifier.padding(horizontal = 16.dp)) {
                            var subbed by remember(chId) { mutableStateOf(false) }
                            LaunchedEffect(chId) { subbed = LocalStore.isSubscribed(chId) }
                            Button(onClick = { vm.toggleSub(chId); subbed = !subbed }, shape = RoundedCornerShape(20.dp)) { Text(if (subbed) "Abone olundu" else "Abone ol") }
                        }
                        if (info.description.isNotBlank()) Text(info.description, Modifier.padding(16.dp), maxLines = 4, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Videolar", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontWeight = FontWeight.Bold)
                    }
                    items(vm.channelVideos, key = { it.id }) { v -> VideoRow(v, { vm.play(v, vm.channelVideos) }) }
                    if (vm.channelNextPage != null) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                if (vm.channelLoadingMore) CircularProgressIndicator(Modifier.size(28.dp))
                                else OutlinedButton(onClick = { vm.channelLoadMore() }) { Text("Daha fazla video yükle") }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: MainVM) {
    BackHandler { vm.showSettings = false }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.showSettings = false }) { Icon(Icons.Filled.ArrowBack, null) }
            Text("Ayarlar", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item { Text("Oynatma", Modifier.padding(16.dp), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary) }
            item {
                var qMenu by remember { mutableStateOf(false) }
                ListItem(headlineContent = { Text("Varsayılan kalite") }, supportingContent = { Text("${vm.defaultQuality}p") },
                    modifier = Modifier.clickable { qMenu = true }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                if (qMenu) AlertDialog(onDismissRequest = { qMenu = false }, confirmButton = {}, title = { Text("Kalite") }, text = {
                    Column { listOf(360, 480, 720, 1080).forEach { q -> TextButton(onClick = { vm.setQuality(q); qMenu = false }) { Text("${q}p") } } }
                })
            }
            item {
                ListItem(headlineContent = { Text("Otomatik oynat") }, supportingContent = { Text("Sonraki videoya geç") },
                    trailingContent = { Switch(checked = vm.autoplay, onCheckedChange = { vm.autoplay = it }) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            }
            item { Divider() }
            item { Text("Hesap", Modifier.padding(16.dp), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary) }
            item {
                ListItem(headlineContent = { Text(if (vm.loggedIn) "Çıkış yap" else "Giriş yap") },
                    supportingContent = { Text(if (vm.loggedIn) "Oturum çerezleri cihazdan silinir" else "Kitaplık ve abonelikler için") },
                    modifier = Modifier.clickable { if (vm.loggedIn) vm.logout() else { vm.showSettings = false; vm.showLogin = true } },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            }
            item {
                ListItem(headlineContent = { Text("Yerel geçmişi temizle") }, supportingContent = { Text("İzleme ve ilerleme verileri silinir") },
                    modifier = Modifier.clickable { vm.clearLocalHistory() }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            }
            item {
                ListItem(headlineContent = { Text("Tüm yerel verileri temizle") }, supportingContent = { Text("Geçmiş, arama, beğeni, abonelik, ilerleme silinir") },
                    modifier = Modifier.clickable { vm.clearAllLocalData() }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            }
            item { Divider() }
            item { Text("İndirmeler ve bildirimler", Modifier.padding(16.dp), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary) }
            item {
                ListItem(headlineContent = { Text("İndirmeler") }, supportingContent = { Text("İndirilen videolar ve çevrimdışı oynatma") },
                    modifier = Modifier.clickable { vm.showSettings = false; vm.showDownloads = true }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            }
            item {
                ListItem(headlineContent = { Text("Bildirimler") }, supportingContent = { Text("Uygulama içi bildirim merkezi") },
                    modifier = Modifier.clickable { vm.showSettings = false; vm.showNotifications = true }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            }
            item { Divider() }
            item { Text("Hakkında", Modifier.padding(16.dp), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary) }
            item {
                ListItem(headlineContent = { Text("CleanTube") }, supportingContent = { Text("Reklamsız YouTube istemcisi • NewPipeExtractor + Media3 • Güvenli oturum") },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            }
        }
    }
}

@Composable
fun DownloadsScreen(vm: MainVM) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { vm.refreshDownloads() }
    BackHandler { vm.showDownloads = false }
    // periodic refresh for progress/status
    LaunchedEffect(vm.showDownloads, vm.downloads.size) {
        while (vm.showDownloads) {
            kotlinx.coroutines.delay(1500)
            vm.refreshDownloads()
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.showDownloads = false }) { Icon(Icons.Filled.ArrowBack, "Geri") }
            Text("İndirmeler", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        }
        if (vm.downloads.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Henüz indirme yok. Videolardan \"İndir\" ile kuyruğa ekleyin (çevrimdışı oynatma için dosya hazır olmalı).", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(vm.downloads, key = { it.fileName + it.videoId }) { d ->
                    val file = remember(d.fileName) {
                        val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                        java.io.File(dir, d.fileName)
                    }
                    val progress = remember(d.downloadId, vm.downloads) { com.cleantube.app.data.DownloadUtil.query(ctx, d.downloadId) }
                    val fileExists = file.exists() && file.length() > 0
                    val statusText = when {
                        fileExists || progress?.isSuccess == true -> "Çevrimdışı hazır"
                        progress?.isFailed == true -> "Başarısız"
                        progress != null && progress.percent >= 0 -> "İndiriliyor %${progress.percent}"
                        progress?.isRunning == true -> "İndiriliyor..."
                        d.status == "failed" -> "Başarısız"
                        else -> "Kuyrukta"
                    }
                    ListItem(
                        headlineContent = { Text(d.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Column {
                                Text(listOf(d.quality, statusText).filter { it.isNotBlank() }.joinToString(" • "))
                                if (progress != null && progress.percent in 0..99) {
                                    LinearProgressIndicator(progress = (progress.percent / 100f).coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                                }
                            }
                        },
                        leadingContent = { Icon(Icons.Filled.Download, contentDescription = null) },
                        trailingContent = {
                            Row {
                                if (fileExists) IconButton(onClick = { vm.play(Video(id = d.videoId, title = d.title)) }) { Icon(Icons.Filled.PlayArrow, "Çevrimdışı oynat") }
                                IconButton(onClick = {
                                    runCatching {
                                        if (d.downloadId > 0) (ctx.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager).remove(d.downloadId)
                                        if (file.exists()) file.delete()
                                    }
                                    vm.removeDownloadItem(d.videoId, d.fileName)
                                }) { Icon(Icons.Filled.Delete, "İptal/Sil") }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
                item { Text("İndirmeler sistem DownloadManager kuyruğu ile yönetilir. İlerleme periyodik yenilenir; dosya hazır olduğunda çevrimdışı oynatılabilir.", modifier = Modifier.padding(16.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
fun NotificationsScreen(vm: MainVM) {
    LaunchedEffect(Unit) { vm.refreshLocal() }
    BackHandler { vm.showNotifications = false }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.showNotifications = false }) { Icon(Icons.Filled.ArrowBack, "Geri") }
            Text("Bildirimler", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.weight(1f))
            if (vm.appNotifications.isNotEmpty()) TextButton(onClick = { vm.clearAppNotifications() }) { Text("Temizle") }
        }
        if (vm.appNotifications.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Bildirim yok.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(vm.appNotifications.size) { i ->
                    val n = vm.appNotifications[i]
                    ListItem(headlineContent = { Text(n.first) }, supportingContent = { Text(n.second) },
                        leadingContent = { Icon(Icons.Filled.Notifications, null) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                    Divider()
                }
            }
        }
    }
}

@Composable
fun AppRoot(vm: MainVM) {
    if (vm.showLogin) { LoginScreen(onDone = { vm.onLoggedIn() }, onCancel = { vm.showLogin = false }); return }
    if (vm.showSettings) { SettingsScreen(vm); return }
    if (vm.showDownloads) { DownloadsScreen(vm); return }
    if (vm.showNotifications) { NotificationsScreen(vm); return }
    if (vm.showCastInfo) {
        AlertDialog(
            onDismissRequest = { vm.showCastInfo = false },
            confirmButton = { TextButton(onClick = { vm.showCastInfo = false }) { Text("Tamam") } },
            title = { Text("Yayınla (Cast)") },
            text = {
                Column {
                    Text("Google Cast SDK/framework entegre edildi (varsayılan medya alıcısı). Aynı ağdaki Cast uyumlu cihazlarda Google Play Hizmetleri gereklidir.")
                    Spacer(Modifier.height(12.dp))
                    AndroidView(
                        factory = { c ->
                            androidx.mediarouter.app.MediaRouteButton(c).apply {
                                val selector = androidx.mediarouter.media.MediaRouteSelector.Builder()
                                    .addControlCategory(com.google.android.gms.cast.CastMediaControlIntent.categoryForCast(com.google.android.gms.cast.CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
                                    .build()
                                routeSelector = selector
                                contentDescription = "Cast cihazı seç"
                            }
                        },
                        modifier = Modifier.height(48.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Not: Oynatma oturumunun Cast cihazına aktarımı için cihazda uçtan uca test gerekir.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
    if (vm.showSearch) {
        BackHandler { vm.showSearch = false }
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.showSearch = false }) { Icon(Icons.Filled.ArrowBack, "Geri") }
                Text("Arama", fontWeight = FontWeight.Bold)
            }
            SearchScreen(vm)
        }
        return
    }
    if (vm.channelInfo != null || vm.channelLoading || vm.channelError != null) { ChannelScreen(vm); return }

    val hasMini = vm.playing != null && !vm.playerExpanded
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                if (hasMini) MiniPlayer(vm)
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    NavigationBarItem(vm.tab == RootTab.HOME, { vm.tab = RootTab.HOME }, { Icon(if (vm.tab == RootTab.HOME) Icons.Filled.Home else Icons.Outlined.Home, "Ana Sayfa") }, label = { Text("Ana Sayfa") })
                    NavigationBarItem(vm.tab == RootTab.SHORTS, { vm.tab = RootTab.SHORTS; vm.loadShorts() }, { Icon(Icons.Outlined.PlayCircle, "Shorts") }, label = { Text("Shorts") })
                    NavigationBarItem(vm.tab == RootTab.SUBS, { vm.tab = RootTab.SUBS; vm.loadSubs() }, { Icon(if (vm.tab == RootTab.SUBS) Icons.Filled.Subscriptions else Icons.Outlined.Subscriptions, "Abonelikler") }, label = { Text("Abonelikler") })
                    NavigationBarItem(vm.tab == RootTab.LIBRARY, { vm.tab = RootTab.LIBRARY }, { Icon(if (vm.tab == RootTab.LIBRARY) Icons.Filled.VideoLibrary else Icons.Outlined.VideoLibrary, "Kitaplık") }, label = { Text("Kitaplık") })
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (vm.tab) {
                RootTab.HOME -> HomeScreen(vm)
                RootTab.SHORTS -> ShortsScreen(vm)
                RootTab.SUBS -> SubscriptionsScreen(vm)
                RootTab.LIBRARY -> LibraryScreen(vm)
            }
        }
    }

    if (vm.playing != null && vm.playerExpanded) {
        PlayerSheet(vm)
    }
}
