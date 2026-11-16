package com.cleantube.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

@Composable
fun VideoCard(v: Video, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(bottom = 16.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceVariant)) {
            AsyncImage(v.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (v.durationText.isNotBlank()) {
                Text(
                    v.durationText, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            if (v.meta.isNotBlank()) {
                Text(v.meta, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun VideoList(videos: List<Video>, onClick: (Video) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize()) { items(videos, key = { it.id }) { VideoCard(it) { onClick(it) } } }
}

@Composable
fun Loading() = Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

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
        TopAppBar(
            title = { Text("CleanTube", fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        when {
            vm.homeLoading && vm.home.isEmpty() -> Loading()
            vm.homeError != null && vm.home.isEmpty() -> ErrorView(vm.homeError!!) { vm.loadHome() }
            else -> VideoList(vm.home, { vm.playing = it })
        }
    }
}

@Composable
fun SearchScreen(vm: MainVM) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        OutlinedTextField(
            value = vm.query, onValueChange = { vm.query = it },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            placeholder = { Text("YouTube'da ara") }, singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search() }),
        )
        when {
            vm.searching -> Loading()
            vm.searchError != null -> ErrorView(vm.searchError!!) { vm.search() }
            else -> VideoList(vm.results, { vm.playing = it })
        }
    }
}

private val sections = listOf(
    Section("Beğendiğim videolar", "VLLL"),
    Section("Beğendiğim müzikler", "VLLM"),
    Section("Sonra izle", "VLWL"),
    Section("Geçmiş", "FEhistory"),
    Section("Abonelik akışı", "FEsubscriptions"),
    Section("Oynatma listeleri", "", isPlaylists = true),
)

@Composable
fun LibraryScreen(vm: MainVM) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        if (!vm.loggedIn) {
            Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                Text("Kitaplığın", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Beğendiğin şarkılar, geçmişin ve oynatma listelerin için Google hesabınla giriş yap. " +
                        "Oturum bilgin yalnızca bu cihazda şifreli saklanır.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { vm.showLogin = true }) { Text("Google ile giriş yap") }
            }
            return@Column
        }
        val s = vm.section
        BackHandler(enabled = s != null) { vm.openSection(null) }
        if (s == null) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(sections) { sec ->
                    ListItem(
                        headlineContent = { Text(sec.title) },
                        modifier = Modifier.clickable { vm.openSection(sec) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
                item {
                    TextButton(onClick = { vm.logout() }, Modifier.padding(8.dp)) { Text("Çıkış yap ve oturumu sil", color = MaterialTheme.colorScheme.primary) }
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
                        ListItem(
                            headlineContent = { Text(p.title) },
                            modifier = Modifier.clickable { vm.openSection(Section(p.title, "VL" + p.id)) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
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
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                    NavigationBarItem(vm.tab == 0, { vm.tab = 0 }, { Icon(Icons.Filled.Home, null) }, label = { Text("Ana Sayfa") })
                    NavigationBarItem(vm.tab == 1, { vm.tab = 1 }, { Icon(Icons.Filled.Search, null) }, label = { Text("Ara") })
                    NavigationBarItem(vm.tab == 2, { vm.tab = 2 }, { Icon(Icons.Filled.Person, null) }, label = { Text("Kitaplık") })
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (vm.tab) { 0 -> HomeScreen(vm); 1 -> SearchScreen(vm); else -> LibraryScreen(vm) }
            }
        }
        if (playing != null) PlayerScreen(vm, playing)
    }
}
