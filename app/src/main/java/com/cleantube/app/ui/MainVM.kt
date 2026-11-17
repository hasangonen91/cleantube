package com.cleantube.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleantube.app.data.*
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

class MainVM : ViewModel() {
    var tab by mutableStateOf(0)
    var categoryIndex by mutableStateOf(0)
    var homeScrollRequest by mutableIntStateOf(0)
    var playing by mutableStateOf<Video?>(null)
    var playerExpanded by mutableStateOf(true)
    var queue by mutableStateOf<List<Video>>(emptyList())
    var queueIndex by mutableIntStateOf(-1)
    var showLogin by mutableStateOf(false)
    var loggedIn by mutableStateOf(Auth.loggedIn)

    var home by mutableStateOf<List<Video>>(emptyList())
    var homeLoading by mutableStateOf(false)
    var homeError by mutableStateOf<String?>(null)

    var shorts by mutableStateOf<List<Video>>(emptyList())
    var shortsLoading by mutableStateOf(false)
    var shortsIndex by mutableIntStateOf(0)

    var query by mutableStateOf("")
    var suggestions by mutableStateOf<List<String>>(emptyList())
    var results by mutableStateOf<List<Video>>(emptyList())
    var searching by mutableStateOf(false)
    var searchError by mutableStateOf<String?>(null)

    var section by mutableStateOf<Section?>(null)
    var libVideos by mutableStateOf<List<Video>>(emptyList())
    var libPlaylists by mutableStateOf<List<PlaylistItem>>(emptyList())
    var libLoading by mutableStateOf(false)
    var libError by mutableStateOf<String?>(null)

    /** Hicbir coroutine hatasi (Exception veya Error) uygulamanin ana surecini dusurmesin. */
    private val safe = CoroutineExceptionHandler { _, t -> Log.e("VYRO", "Yakalanmamis coroutine hatasi", t) }

    private fun friendly(t: Throwable, fallback: String): String {
        Log.w("VYRO", fallback, t)
        return when (t) {
            is java.io.IOException -> "İnternet bağlantısı yok veya sunucuya ulaşılamadı."
            else -> t.message?.takeIf { it.isNotBlank() }?.let { "$fallback ($it)" } ?: fallback
        }
    }

    private var homeJob: Job? = null
    private var homeRequestId = 0

    fun refreshHome() {
        homeScrollRequest++
        loadHome()
    }

    fun loadHome() {
        val requestId = ++homeRequestId
        homeJob?.cancel()
        categoryIndex = 0
        homeLoading = true
        homeError = null
        homeJob = viewModelScope.launch(safe) {
            try {
                val fresh = if (Auth.loggedIn) {
                    runCatching { InnerTube.videos("FEwhat_to_watch") }.getOrNull()?.takeIf { it.isNotEmpty() } ?: Repo.trending()
                } else Repo.trending()
                home = fresh
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                homeError = friendly(e, "Yüklenemedi")
            } finally {
                if (requestId == homeRequestId) homeLoading = false
            }
        }
    }

    private val shortsQueries = listOf(
        "#shorts", "#shorts komik", "#shorts müzik", "#shorts oyun", "#youtubeshorts", "#shorts futbol",
        "#shorts yemek", "#shorts kedi", "#shorts tiktok", "#shorts dans", "#shorts teknoloji", "#shorts spor",
    )
    private var shortsQ = 0
    private var shortsFetchJob: Job? = null

    private fun fetchShorts(minNew: Int) {
        if (shortsFetchJob?.isActive == true) return
        shortsLoading = true
        shortsFetchJob = viewModelScope.launch(safe) {
            val found = LinkedHashMap<String, Video>()
            shorts.forEach { found[it.id] = it }
            val before = found.size
            val maxQueries = if (before == 0) 3 else shortsQueries.size
            var tries = 0
            try {
                while (tries < maxQueries && found.size - before < minNew) {
                    val q = shortsQueries[shortsQ % shortsQueries.size]
                    shortsQ++; tries++
                    try {
                        Repo.search(q).forEach { v ->
                            val seconds = v.durationText.split(":").mapNotNull(String::toIntOrNull)
                                .fold(0L) { total, part -> total * 60 + part }
                            // Extractor bazen Shorts bayrağını boş döndürüyor; #shorts sorgusundaki
                            // süresiz sonuçları da ilk ekrana alıp akış çözümlemesini oynatıcıya bırak.
                            if (v.short || seconds in 1..180 || (q == "#shorts" && v.durationText.isBlank())) {
                                found.putIfAbsent(v.id, v)
                            }
                        }
                        if (found.size > shorts.size) shorts = found.values.toList()
                    } catch (e: CancellationException) { throw e }
                    catch (e: Throwable) { Log.w("VYRO", "Shorts sorgusu başarısız: $q", e) }
                    if (shorts.isNotEmpty() && found.size - before >= minNew) break
                }
                shorts = found.values.toList()
                Log.i("VYRO", "Shorts yüklendi: ${shorts.size}")
            } finally {
                shortsLoading = false
            }
        }
    }

    fun loadShorts() { if (shorts.isEmpty()) fetchShorts(12) }
    fun moreShorts() { fetchShorts(8) }
    fun openShorts(index: Int) { shortsIndex = index; tab = 1; if (playing != null) playerExpanded = false }

    fun prefetchStreamInfo(id: String) {
        if (id.isBlank()) return
        viewModelScope.launch(safe) { runCatching { streamInfo(id) } }
    }

    fun play(video: Video, fromList: List<Video> = emptyList()) {
        val list = (fromList.ifEmpty { listOf(video) }).distinctBy { it.id }
        queue = list
        queueIndex = list.indexOfFirst { it.id == video.id }.coerceAtLeast(0)
        playing = video
        playerExpanded = true
    }

    fun playNext() {
        val next = queueIndex + 1
        if (next in queue.indices) {
            queueIndex = next
            playing = queue[next]
            playerExpanded = true
        } else {
            closePlayer()
        }
    }

    fun minimizePlayer() { if (playing != null) playerExpanded = false }
    fun expandPlayer() { if (playing != null) playerExpanded = true }
    fun closePlayer() { playing = null; playerExpanded = false; queue = emptyList(); queueIndex = -1 }

    private val infoCache = LinkedHashMap<String, org.schabi.newpipe.extractor.stream.StreamInfo>()

    /** Akis bilgisini (kisa omurlu URL'ler) kucuk bir onbellekle cozer; sonraki Shorts onceden yuklenir. */
    suspend fun streamInfo(id: String): org.schabi.newpipe.extractor.stream.StreamInfo {
        synchronized(infoCache) { infoCache[id] }?.let { return it }
        val i = Repo.resolve(id)
        synchronized(infoCache) {
            infoCache[id] = i
            while (infoCache.size > 8) infoCache.remove(infoCache.keys.first())
        }
        return i
    }

    fun suggest(value: String) = viewModelScope.launch(safe) {
        kotlinx.coroutines.delay(250)
        if (query != value || value.isBlank()) { suggestions = emptyList(); return@launch }
        suggestions = runCatching { InnerTube.suggestions(value) }.getOrDefault(emptyList())
    }

    fun search() = viewModelScope.launch(safe) {
        val q = query.trim()
        if (q.isEmpty()) return@launch
        suggestions = emptyList()
        searching = true; searchError = null
        try { results = Repo.search(q) } catch (e: CancellationException) { throw e } catch (e: Throwable) { searchError = friendly(e, "Arama başarısız") }
        searching = false
    }

    fun openSection(s: Section?) {
        section = s
        libVideos = emptyList(); libPlaylists = emptyList(); libError = null
        if (s == null) return
        viewModelScope.launch(safe) {
            libLoading = true
            try {
                when {
                    s.browseId == "FEmusic" -> libPlaylists = InnerTube.mixes()
                    s.isPlaylists -> libPlaylists = InnerTube.playlists()
                    else -> libVideos = InnerTube.videos(if (s.browseId.startsWith("VL")) s.browseId else "VL" + s.browseId)
                }
            } catch (e: CancellationException) { throw e } catch (e: Throwable) { libError = friendly(e, "Yüklenemedi") }
            libLoading = false
        }
    }

    fun playPlaylist(playlist: PlaylistItem) = viewModelScope.launch(safe) {
        val browseId = if (playlist.id.startsWith("VL")) playlist.id else "VL" + playlist.id
        section = Section(playlist.title, browseId)
        libLoading = true
        libError = null
        try {
            val videos = InnerTube.videos(browseId)
            libVideos = videos
            if (videos.isNotEmpty()) play(videos.first(), videos)
            else libError = "Bu listede oynatılabilir video bulunamadı."
        } catch (e: CancellationException) { throw e }
        catch (e: Throwable) { libError = friendly(e, "Liste oynatılamadı") }
        finally { libLoading = false }
    }

    fun onLoggedIn() { loggedIn = true; showLogin = false; loadHome() }

    fun logout() {
        Auth.logout(); loggedIn = false; section = null
        libVideos = emptyList(); libPlaylists = emptyList(); loadHome()
    }

    // Ozellikler (shortsQueries, infoCache...) tanimlandiktan SONRA baslat.
    init { loadHome(); loadShorts() }
}
