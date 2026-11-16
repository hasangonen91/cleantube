package com.cleantube.app.ui

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cleantube.app.data.Section
import com.cleantube.app.data.Video

private val categories = listOf("Tümü", "Müzik", "Canlı", "Oyun", "Mix'ler", "Teknoloji", "Haberler", "Spor")

@Composable
private fun BrandMark(size: androidx.compose.ui.unit.Dp = 30.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(9.dp)).background(Color(0xFFFF0033)), Alignment.Center) {
        Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(size * .78f))
    }
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
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandMark(30.dp)
            Spacer(Modifier.width(9.dp))
            Text("CleanTube", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-.6).sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.tab = 1 }) { Icon(Icons.Filled.Search, "Ara") }
            IconButton(onClick = { vm.tab = 2 }) { Icon(Icons.Filled.Person, "Hesap") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEachIndexed { index, category ->
                FilterChip(
                    selected = (vm.categoryIndex == index),
                    onClick = {
                        vm.categoryIndex = index
                        if (index > 0) {
                            vm.query = when (category) {
                                "Mix'ler" -> "müzik mix"
                                else -> category
                            }
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
            else -> VideoList(if (vm.categoryIndex == 0) vm.home else vm.results, { vm.playing = it })
        }
    }
}

@Composable
fun SearchScreen(vm: MainVM) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = vm.query, onValueChange = { vm.query = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("YouTube'da ara") }, singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search() }),
            )
            IconButton(onClick = { vm.search() }) { Icon(Icons.Filled.Search, "Ara") }
        }
        when {
            vm.searching -> Loading()
            vm.searchError != null -> ErrorView(vm.searchError!!) { vm.search() }
            else -> VideoList(vm.results, { vm.playing = it })
        }
    }
}

private val sections = listOf(
    Section("Geçmiş", "FEhistory"),
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
        if (!vm.loggedIn) {
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
                Text(s.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            when {
                vm.libLoading -> Loading()
                vm.libError != null -> ErrorView(vm.libError!!) { vm.openSection(s) }
                s.isPlaylists -> LazyColumn(Modifier.fillMaxSize()) {
                    items(vm.libPlaylists, key = { it.id }) { p ->
                        ListItem(headlineContent = { Text(p.title) }, modifier = Modifier.clickable { vm.openSection(Section(p.title, "VL" + p.id)) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                    }
                }
                vm.libVideos.isEmpty() -> ErrorView("Burada gösterilecek bir şey bulunamadı.", null)
                else -> VideoList(vm.libVideos, { vm.playing = it })
            }
        }
    }
}

@Composable
fun AppRoot(vm: MainVM) {
    if (vm.showLogin) { LoginScreen(onDone = { vm.onLoggedIn() }, onCancel = { vm.showLogin = false }); return }
    val playing = vm.playing
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                NavigationBarItem(vm.tab == 0, { vm.tab = 0 }, { Icon(Icons.Filled.Home, null) }, label = { Text("Ana Sayfa") })
                NavigationBarItem(vm.tab == 1, { vm.tab = 1 }, { Icon(Icons.Filled.Search, null) }, label = { Text("Keşfet") })
                NavigationBarItem(vm.tab == 2, { vm.tab = 2 }, { Icon(Icons.Filled.Person, null) }, label = { Text("Kitaplık") })
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (vm.tab) { 0 -> HomeScreen(vm); 1 -> SearchScreen(vm); else -> LibraryScreen(vm) }
            if (playing != null) PlayerScreen(vm, playing)
        }
    }
}
