package com.cleantube.app.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cleantube.app.data.Section
import com.cleantube.app.data.Video
import com.cleantube.app.R
import com.cleantube.app.player.PlaybackService

private val categories = listOf("Senin için", "Müzik", "Canlı", "Oyun", "Teknoloji", "Haberler", "Spor")

@Composable
private fun BrandMark(size: androidx.compose.ui.unit.Dp = 30.dp) {
    Image(
        painter = painterResource(R.drawable.ic_vyro),
        contentDescription = "VYRO",
        modifier = Modifier.size(size),
    )
}

@Composable
fun VideoCard(v: Video, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp).padding(bottom = 22.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(v.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (v.durationText.isNotBlank()) {
                Text(v.durationText, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                        .background(Color(0xE6000000), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 2.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 11.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), Alignment.Center) {
                Text(v.channel.take(1).ifBlank { "C" }.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp)
                if (v.meta.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(v.meta, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
                }
            }
        }
    }
}

@Composable
fun VideoList(videos: List<Video>, onClick: (Video) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize()) { items(videos, key = { it.id }) { VideoCard(it) { onClick(it) } } }
}

@Composable
fun Loading() = Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }

@Composable
fun ErrorView(msg: String, onRetry: (() -> Unit)?) = Column(
    Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally,
) {
    Text(msg, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (onRetry != null) { Spacer(Modifier.height(12.dp)); Button(onClick = onRetry) { Text("Tekrar dene") } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainVM) {
    val listState = rememberLazyListState()
    val pull = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val triggered = remember { androidx.compose.runtime.mutableStateOf(false) }
    val pullConnection = remember(vm) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 && available.y > 0f) {
                    pull.floatValue += available.y
                    if (pull.floatValue > 120f && !triggered.value) { triggered.value = true; vm.refreshHome() }
                    return Offset(0f, available.y * 0.35f)
                }
                if (available.y < 0f) { pull.floatValue = 0f; triggered.value = false }
                return Offset.Zero
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                pull.floatValue = 0f; triggered.value = false
                return Velocity.Zero
            }
        }
    }
    LaunchedEffect(vm.homeScrollRequest) { listState.animateScrollToItem(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandMark(30.dp)
            Spacer(Modifier.width(9.dp))
            Text("VYRO", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-.6).sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.refreshHome() }) { Icon(Icons.Filled.Refresh, "Yenile") }
            IconButton(onClick = { vm.tab = 2 }) { Icon(Icons.Filled.Search, "Ara") }
            IconButton(onClick = { vm.tab = 2 }) { Icon(Icons.Filled.Person, "Hesap") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { vm.tab = 3; vm.openSection(Section("Mixler", "FEmusic")) }, label = { Text("♫ Mixler") }, shape = RoundedCornerShape(9.dp))
            categories.forEachIndexed { index, category ->
                FilterChip(
                    selected = (vm.categoryIndex == index),
                    onClick = {
                        vm.categoryIndex = index
                        if (index > 0) {
                            vm.query = category
                            vm.search()
                        } else vm.loadHome()
                    },
                    label = { Text(category, fontSize = 13.sp) },
                    shape = RoundedCornerShape(9.dp),
                )
            }
        }
        when {
            vm.homeLoading && vm.home.isEmpty() -> Loading()
            vm.homeError != null && vm.home.isEmpty() -> ErrorView(vm.homeError!!) { vm.loadHome() }
            else -> Box(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize().nestedScroll(pullConnection), state = listState) {
                    if (vm.categoryIndex == 0 && vm.shorts.isNotEmpty()) {
                        item { ShortsShelf(vm) }
                    }
                    items(if (vm.categoryIndex == 0) vm.home else vm.results, key = { it.id }) {
                        VideoCard(it) { vm.play(it, if (vm.categoryIndex == 0) vm.home else vm.results) }
                    }
                }
                if (pull.floatValue > 0f || vm.homeLoading) {
                    Surface(
                        Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                        shape = CircleShape,
                        tonalElevation = 5.dp,
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(if (vm.homeLoading) "Yenileniyor…" else "Yenilemek için çek", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortsShelf(vm: MainVM) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandMark(27.dp)
            Spacer(Modifier.width(9.dp))
            Text("Shorts", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { vm.tab = 1 }) { Text("Tümünü gör") }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(vm.shorts.take(12), key = { _, v -> v.id }) { index, video ->
                Column(Modifier.width(154.dp).clickable { vm.openShorts(index) }) {
                    AsyncImage(video.thumb, null, Modifier.fillMaxWidth().height(238.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                    Spacer(Modifier.height(7.dp))
                    Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(video.channel, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun SearchScreen(vm: MainVM) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = vm.query, onValueChange = { vm.query = it; vm.results = emptyList(); vm.suggest(it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("YouTube'da ara") }, singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search() }),
            )
            IconButton(onClick = { vm.search() }) { Icon(Icons.Filled.Search, "Ara") }
        }
        if (vm.query.isNotBlank() && vm.suggestions.isNotEmpty() && !vm.searching && vm.results.isEmpty()) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(vm.suggestions) { suggestion ->
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.Search, null) },
                        headlineContent = { Text(suggestion) },
                        modifier = Modifier.clickable { vm.query = suggestion; vm.search() },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        } else when {
            vm.searching -> Loading()
            vm.searchError != null -> ErrorView(vm.searchError!!) { vm.search() }
            else -> VideoList(vm.results, { vm.play(it, vm.results) })
        }
    }
}

private val sections = listOf(
    Section("Geçmiş", "FEhistory"),
    Section("Mixler", "FEmusic"),
    Section("Oynatma listeleri", "", isPlaylists = true),
    Section("Daha sonra izle", "VLWL"),
    Section("Beğendiğim videolar", "VLLL"),
    Section("Beğendiğim müzikler", "VLLM"),
    Section("Abonelikler", "FEsubscriptions"),
)

@Composable
fun LibraryScreen(vm: MainVM) {
    Column(Modifier.fillMaxSize()) {
        Text("Kitaplık", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp))
        if (!vm.loggedIn && vm.section?.browseId != "FEmusic") {
            Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                BrandMark(56.dp)
                Spacer(Modifier.height(16.dp))
                Text("Kitaplığını yanında taşı", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Geçmişini, beğendiğin videoları ve oynatma listelerini görmek için Google hesabınla giriş yap.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { vm.showLogin = true }, shape = RoundedCornerShape(24.dp)) { Text("Google ile giriş yap") }
            }
            return@Column
        }
        val s = vm.section
        BackHandler(enabled = s != null) { vm.openSection(null) }
        if (s == null) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(sections) { sec ->
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.PlayArrow, null) },
                        headlineContent = { Text(sec.title, fontWeight = FontWeight.Medium) },
                        modifier = Modifier.clickable { vm.openSection(sec) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
                item {
                    TextButton(onClick = { vm.logout() }, Modifier.padding(8.dp)) { Text("Çıkış yap ve oturumu sil") }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.openSection(null) }) { Icon(Icons.Filled.ArrowBack, "Geri") }
                Text(s.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (!s.isPlaylists && s.browseId != "FEmusic" && vm.libVideos.isNotEmpty()) {
                    TextButton(onClick = { vm.play(vm.libVideos.first(), vm.libVideos) }) { Text("Tümünü oynat") }
                }
            }
            when {
                vm.libLoading -> Loading()
                vm.libError != null -> ErrorView(vm.libError!!) { vm.openSection(s) }
                s.isPlaylists || s.browseId == "FEmusic" -> {
                    if (vm.libPlaylists.isEmpty()) {
                        ErrorView("Burada gösterilecek mix veya oynatma listesi bulunamadı.", null)
                    } else LazyColumn(Modifier.fillMaxSize()) {
                        items(vm.libPlaylists, key = { it.id }) { p ->
                            ListItem(
                                leadingContent = { Icon(Icons.Filled.PlayArrow, null) },
                                headlineContent = { Text(p.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text("Oynatma listesini aç") },
                                trailingContent = { TextButton(onClick = { vm.playPlaylist(p) }) { Text("Oynat") } },
                                modifier = Modifier.clickable { vm.openSection(Section(p.title, if (p.id.startsWith("VL")) p.id else "VL" + p.id)) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }
                vm.libVideos.isEmpty() -> ErrorView("Burada gösterilecek bir şey bulunamadı.", null)
                else -> VideoList(vm.libVideos, { vm.play(it, vm.libVideos) })
            }
        }
    }
}

@Composable
fun AppRoot(vm: MainVM) {
    if (vm.showLogin) { LoginScreen(onDone = { vm.onLoggedIn() }, onCancel = { vm.showLogin = false }); return }
    val activity = LocalContext.current as? Activity
    var showExitDialog by remember { mutableStateOf(false) }
    BackHandler(enabled = true) {
        when {
            vm.playing != null && vm.playerExpanded -> vm.minimizePlayer()
            vm.tab != 0 -> vm.tab = 0
            vm.playing != null && !vm.playerExpanded -> {
                PlaybackService.sharedPlayer?.let { p ->
                    runCatching { p.pause(); p.stop(); p.clearMediaItems() }
                }
                vm.closePlayer()
            }
            else -> showExitDialog = true
        }
    }
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("VYRO'dan çıkılsın mı?") },
            text = { Text("Çıkarsan oynatma durdurulacak.") },
            confirmButton = {
                TextButton(onClick = {
                    PlaybackService.sharedPlayer?.let { p -> runCatching { p.pause(); p.stop(); p.clearMediaItems() } }
                    PlaybackService.sharedShortsPlayer?.let { p -> runCatching { p.pause(); p.stop(); p.clearMediaItems() } }
                    vm.closePlayer()
                    showExitDialog = false
                    activity?.finish()
                }) { Text("Çıkış yap") }
            },
            dismissButton = { TextButton(onClick = { showExitDialog = false }) { Text("Vazgeç") } },
        )
    }
    val hasMini = vm.playing != null && !vm.playerExpanded
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                if (hasMini) MiniPlayer(vm)
                NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                    NavigationBarItem(vm.tab == 0, {
                        if (vm.tab == 0) vm.refreshHome() else vm.tab = 0
                        vm.minimizePlayer()
                    }, { Icon(Icons.Filled.Home, null) }, label = { Text("Ana Sayfa") })
                    NavigationBarItem(vm.tab == 1, {
                        vm.tab = 1; vm.loadShorts(); vm.minimizePlayer()
                    }, { Icon(Icons.Filled.PlayArrow, null) }, label = { Text("Shorts") })
                    NavigationBarItem(vm.tab == 2, { vm.tab = 2; vm.minimizePlayer() }, { Icon(Icons.Filled.Search, null) }, label = { Text("Ara") })
                    NavigationBarItem(vm.tab == 3, { vm.tab = 3; vm.minimizePlayer() }, { Icon(Icons.Filled.Person, null) }, label = { Text("Kitaplık") })
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (vm.tab) { 0 -> HomeScreen(vm); 1 -> ShortsScreen(vm); 2 -> SearchScreen(vm); else -> LibraryScreen(vm) }
            if (vm.playing != null) PlayerScreen(vm, vm.playing!!)
        }
    }
}
