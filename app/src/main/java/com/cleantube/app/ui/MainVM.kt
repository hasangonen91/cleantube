package com.cleantube.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleantube.app.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.File
import android.os.Environment

enum class RootTab { HOME, SHORTS, SUBS, LIBRARY }

class MainVM : ViewModel() {
    var tab by mutableStateOf(RootTab.HOME)
    var categoryIndex by mutableIntStateOf(0)
    var showLogin by mutableStateOf(false)
    var loggedIn by mutableStateOf(Auth.loggedIn)

    // Home
    var home by mutableStateOf<List<Video>>(emptyList())
    var homeLoading by mutableStateOf(false)
    var homeLoadingMore by mutableStateOf(false)
    var homeNextPage by mutableStateOf<Any?>(null)
    var homeError by mutableStateOf<String?>(null)

    // Shorts
    var shorts by mutableStateOf<List<Video>>(emptyList())
    var shortsLoading by mutableStateOf(false)
    var shortsLoadingMore by mutableStateOf(false)
    var shortsNextPage by mutableStateOf<Any?>(null)

    // Search
    var query by mutableStateOf("")
    var results by mutableStateOf<List<Video>>(emptyList())
    var searchChannels by mutableStateOf<List<Channel>>(emptyList())
    var searchPlaylists by mutableStateOf<List<PlaylistItem>>(emptyList())
    var suggestions by mutableStateOf<List<String>>(emptyList())
    var searchHistory by mutableStateOf<List<String>>(emptyList())
    var searching by mutableStateOf(false)
    var searchLoadingMore by mutableStateOf(false)
    var searchNextPage by mutableStateOf<Any?>(null)
    var searchError by mutableStateOf<String?>(null)
    private var suggestJob: Job? = null

    // Library / sections
    var section by mutableStateOf<Section?>(null)
    var libVideos by mutableStateOf<List<Video>>(emptyList())
    var libPlaylists by mutableStateOf<List<PlaylistItem>>(emptyList())
    var libLoading by mutableStateOf(false)
    var libLoadingMore by mutableStateOf(false)
    var libContinuation by mutableStateOf<String?>(null)
    var libError by mutableStateOf<String?>(null)
    var localHistory by mutableStateOf<List<Video>>(emptyList())

    // Subscriptions (local follows + logged-in browse)
    var subsFeed by mutableStateOf<List<Video>>(emptyList())
    var subsLoading by mutableStateOf(false)
    var subsChannels by mutableStateOf<List<Channel>>(emptyList())

    // Channel screen
    var channelInfo by mutableStateOf<ChannelInfo?>(null)
    var channelVideos by mutableStateOf<List<Video>>(emptyList())
    var channelLoading by mutableStateOf(false)
    var channelLoadingMore by mutableStateOf(false)
    var channelNextPage by mutableStateOf<Any?>(null)
    var channelError by mutableStateOf<String?>(null)

    // Player / queue / miniplayer
    var playing by mutableStateOf<Video?>(null)
    var playerExpanded by mutableStateOf(true)
    var queue = mutableStateListOf<Video>()
    var queueIndex by mutableIntStateOf(-1)
    var currentStreamInfo by mutableStateOf<StreamInfo?>(null)
    var comments by mutableStateOf<List<Comment>>(emptyList())
    var commentsLoading by mutableStateOf(false)
    var commentsLoadingMore by mutableStateOf(false)
    var commentsNextPage by mutableStateOf<Any?>(null)
    var commentsInfoObj by mutableStateOf<Any?>(null)
    var playbackSpeed by mutableFloatStateOf(1f)
    var autoplay by mutableStateOf(true)
    var shuffle by mutableStateOf(false)
    var repeatMode by mutableIntStateOf(0) // 0 off, 1 all, 2 one
    var watchLater by mutableStateOf<List<Video>>(emptyList())

    // Settings / overlays
    var showSettings by mutableStateOf(false)
    var showSearch by mutableStateOf(false)
    var showDownloads by mutableStateOf(false)
    var showNotifications by mutableStateOf(false)
    var showCastInfo by mutableStateOf(false)
    var defaultQuality by mutableIntStateOf(720)

    var downloads by mutableStateOf<List<DownloadItem>>(emptyList())
    var appNotifications by mutableStateOf<List<Pair<String, String>>>(emptyList())

    init {
        loadHome(); loadShorts(); refreshLocal()
    }

    fun refreshLocal() = viewModelScope.launch {
        localHistory = LocalStore.getHistory()
        searchHistory = LocalStore.getSearchHistory()
        defaultQuality = LocalStore.getDefaultQuality()
        playbackSpeed = LocalStore.getSpeed()
        watchLater = LocalStore.getWatchLater()
        downloads = LocalStore.getDownloads()
        appNotifications = LocalStore.getNotifications()
    }

    fun refreshDownloads() = viewModelScope.launch { downloads = LocalStore.getDownloads() }
    fun removeDownloadItem(videoId: String, fileName: String) = viewModelScope.launch { LocalStore.removeDownload(videoId, fileName); refreshDownloads() }
    fun clearAppNotifications() = viewModelScope.launch { LocalStore.clearNotifications(); refreshLocal() }

    fun loadHome() = viewModelScope.launch {
        categoryIndex = 0
        homeLoading = true; homeError = null; homeNextPage = null
        try {
            if (Auth.loggedIn) {
                val personal = runCatching { InnerTube.videos("FEwhat_to_watch") }.getOrNull()
                if (!personal.isNullOrEmpty()) { home = personal; homeNextPage = null }
                else { val p = Repo.trendingPage(); home = p.videos; homeNextPage = p.nextPage }
            } else {
                val p = Repo.trendingPage(); home = p.videos; homeNextPage = p.nextPage
            }
        } catch (e: Exception) { homeError = e.message ?: "Yüklenemedi" }
        homeLoading = false
    }

    fun loadCategory(index: Int, name: String) = viewModelScope.launch {
        categoryIndex = index
        if (index == 0) { loadHome(); return@launch }
        homeLoading = true; homeError = null; homeNextPage = null
        try { val p = Repo.feedPage(name); home = p.videos; homeNextPage = p.nextPage } catch (e: Exception) { homeError = e.message ?: "Yüklenemedi" }
        homeLoading = false
    }

    fun homeLoadMore() = viewModelScope.launch {
        val np = homeNextPage as? org.schabi.newpipe.extractor.Page ?: return@launch
        if (homeLoadingMore) return@launch
        homeLoadingMore = true
        try {
            val p = Repo.homeMore(np)
            home = (home + p.videos).distinctBy { it.id }
            homeNextPage = p.nextPage
        } catch (_: Exception) { homeNextPage = null }
        homeLoadingMore = false
    }

    fun loadShorts() = viewModelScope.launch {
        if (shorts.isNotEmpty()) return@launch
        shortsLoading = true; shortsNextPage = null
        try { val p = Repo.shortsPage(); shorts = p.videos; shortsNextPage = p.nextPage } catch (_: Exception) { shorts = emptyList() }
        shortsLoading = false
    }

    fun shortsLoadMore() = viewModelScope.launch {
        val np = shortsNextPage as? org.schabi.newpipe.extractor.Page ?: return@launch
        if (shortsLoadingMore) return@launch
        shortsLoadingMore = true
        try {
            val p = Repo.shortsMore(np)
            shorts = (shorts + p.videos).distinctBy { it.id }
            shortsNextPage = p.nextPage
        } catch (_: Exception) { shortsNextPage = null }
        shortsLoadingMore = false
    }

    fun loadSubs() = viewModelScope.launch {
        subsLoading = true
        try {
            val localIds = LocalStore.getSubs()
            subsChannels = localIds.map { Channel(id = it, name = it.removePrefix("UC"), avatar = "") }
            subsFeed = if (Auth.loggedIn) {
                runCatching { InnerTube.videos("FEsubscriptions") }.getOrDefault(emptyList())
            } else emptyList()
        } catch (_: Exception) { }
        subsLoading = false
    }

    fun onQueryChange(q: String) {
        query = q
        suggestJob?.cancel()
        if (q.length < 2) { suggestions = emptyList(); return }
        suggestJob = viewModelScope.launch {
            delay(250)
            suggestions = runCatching { Repo.suggestions(q) }.getOrDefault(emptyList())
        }
    }

    fun search(term: String = query) = viewModelScope.launch {
        val q = term.trim()
        if (q.isEmpty()) return@launch
        query = q
        suggestions = emptyList()
        LocalStore.addSearch(q); refreshLocal()
        searching = true; searchError = null; searchNextPage = null
        try {
            val p = Repo.searchPage(q)
            results = p.videos; searchChannels = p.channels; searchPlaylists = p.playlists; searchNextPage = p.nextPage
        } catch (e: Exception) { searchError = e.message ?: "Arama başarısız" }
        searching = false
    }

    fun searchLoadMore() = viewModelScope.launch {
        val np = searchNextPage as? org.schabi.newpipe.extractor.Page ?: return@launch
        if (searchLoadingMore) return@launch
        searchLoadingMore = true
        try {
            val p = Repo.searchMore(np)
            results = (results + p.videos).distinctBy { it.id }
            searchChannels = (searchChannels + p.channels).distinctBy { it.id }
            searchPlaylists = (searchPlaylists + p.playlists).distinctBy { it.id }
            searchNextPage = p.nextPage
        } catch (_: Exception) { searchNextPage = null }
        searchLoadingMore = false
    }

    fun openSection(s: Section?) {
        section = s
        libVideos = emptyList(); libPlaylists = emptyList(); libError = null; libContinuation = null
        if (s == null) { refreshLocal(); return }
        viewModelScope.launch {
            libLoading = true
            try {
                when {
                    s.browseId == "LOCAL_HISTORY" -> { libVideos = LocalStore.getHistory() }
                    s.browseId == "LOCAL_WL" -> { libVideos = LocalStore.getWatchLater() }
                    s.isPlaylists -> { val p = InnerTube.playlistsPage(); libPlaylists = p.playlists; libContinuation = p.continuation }
                    else -> { val p = InnerTube.videosPage(s.browseId); libVideos = p.videos; libContinuation = p.continuation }
                }
            } catch (e: Exception) { libError = e.message ?: "Yüklenemedi" }
            libLoading = false
        }
    }

    fun sectionLoadMore() = viewModelScope.launch {
        val s = section ?: return@launch
        val cont = libContinuation ?: return@launch
        if (libLoadingMore) return@launch
        libLoadingMore = true
        try {
            when {
                s.isPlaylists -> { val p = InnerTube.playlistsPage(cont); libPlaylists = (libPlaylists + p.playlists).distinctBy { it.id }; libContinuation = p.continuation }
                else -> { val p = InnerTube.videosPage(s.browseId, cont); libVideos = (libVideos + p.videos).distinctBy { it.id }; libContinuation = p.continuation }
            }
        } catch (_: Exception) { libContinuation = null }
        libLoadingMore = false
    }

    fun openChannel(idOrUrl: String) = viewModelScope.launch {
        channelLoading = true; channelError = null; channelInfo = null; channelVideos = emptyList(); channelNextPage = null
        try {
            val p = Repo.channelPage(idOrUrl)
            channelInfo = p.info
            channelVideos = p.videos
            channelNextPage = p.nextPage
        } catch (e: Exception) { channelError = e.message ?: "Kanal yüklenemedi" }
        channelLoading = false
    }

    fun channelLoadMore() = viewModelScope.launch {
        val info = channelInfo ?: return@launch
        val np = channelNextPage as? org.schabi.newpipe.extractor.Page ?: return@launch
        if (channelLoadingMore) return@launch
        channelLoadingMore = true
        try {
            val p = Repo.channelMore(info, np)
            channelVideos = (channelVideos + p.videos).distinctBy { it.id }
            channelNextPage = p.nextPage
        } catch (_: Exception) { channelNextPage = null }
        channelLoadingMore = false
    }

    fun closeChannel() { channelInfo = null; channelVideos = emptyList(); channelError = null; channelNextPage = null }

    fun loadComments(videoId: String) = viewModelScope.launch {
        commentsLoading = true; commentsNextPage = null; commentsInfoObj = null
        try {
            val p = Repo.commentsPage(videoId)
            comments = p.comments; commentsNextPage = p.nextPage; commentsInfoObj = p.info
        } catch (_: Exception) { comments = emptyList() }
        commentsLoading = false
    }

    fun commentsLoadMore() = viewModelScope.launch {
        val np = commentsNextPage as? org.schabi.newpipe.extractor.Page ?: return@launch
        val info = commentsInfoObj as? org.schabi.newpipe.extractor.comments.CommentsInfo ?: return@launch
        if (commentsLoadingMore) return@launch
        commentsLoadingMore = true
        try {
            val p = Repo.commentsMore(info, np)
            comments = (comments + p.comments).distinctBy { it.id }
            commentsNextPage = p.nextPage
        } catch (_: Exception) { commentsNextPage = null }
        commentsLoadingMore = false
    }

    fun postComment(videoId: String, text: String, onResult: (Boolean) -> Unit = {}) = viewModelScope.launch {
        if (!loggedIn || text.isBlank()) { onResult(false); return@launch }
        val ok = InnerTube.postComment(videoId, text.trim()).isSuccess
        if (ok) loadComments(videoId)
        onResult(ok)
    }

    fun play(video: Video, fromList: List<Video> = emptyList()) {
        val list = if (fromList.isNotEmpty()) fromList else listOf(video)
        queue.clear(); queue.addAll(list.distinctBy { it.id })
        queueIndex = queue.indexOfFirst { it.id == video.id }.coerceAtLeast(0)
        playing = video
        playerExpanded = true
        currentStreamInfo = null; comments = emptyList(); commentsNextPage = null; commentsInfoObj = null
        viewModelScope.launch { LocalStore.addHistory(video); refreshLocal() }
        loadComments(video.id)
    }

    private fun playAt(index: Int) {
        if (index !in queue.indices) return
        queueIndex = index
        val v = queue[queueIndex]
        playing = v; playerExpanded = true; currentStreamInfo = null; comments = emptyList(); commentsNextPage = null; commentsInfoObj = null
        viewModelScope.launch { LocalStore.addHistory(v); refreshLocal() }
        loadComments(v.id)
    }

    fun playNext(auto: Boolean = false) {
        if (queue.isEmpty()) return
        if (repeatMode == 2 && auto) { playing?.let { play(it, queue.toList()) }; return }
        val next = when {
            shuffle -> (queue.indices - queueIndex).randomOrNull() ?: queue.indices.random()
            queueIndex + 1 < queue.size -> queueIndex + 1
            repeatMode == 1 -> 0
            else -> -1
        }
        if (next >= 0) playAt(next)
    }

    fun playPrev() {
        if (queueIndex - 1 >= 0) playAt(queueIndex - 1) else if (repeatMode == 1 && queue.isNotEmpty()) playAt(queue.lastIndex)
    }

    fun minimizePlayer() { playerExpanded = false }
    fun expandPlayer() { if (playing != null) playerExpanded = true }
    fun closePlayer() { playing = null; playerExpanded = false; currentStreamInfo = null }

    fun toggleLike(id: String) = viewModelScope.launch {
        val nowLiked = LocalStore.toggleLike(id)
        if (loggedIn) runCatching { InnerTube.setLike(id, nowLiked) }
    }
    fun toggleSub(channelId: String) = viewModelScope.launch {
        val cleanId = if (channelId.startsWith("UC")) channelId else channelId
        val nowSub = LocalStore.toggleSub(cleanId)
        if (loggedIn && cleanId.startsWith("UC")) runCatching { InnerTube.subscribe(cleanId, nowSub) }
        loadSubs()
    }

    fun clearLocalHistory() = viewModelScope.launch { LocalStore.clearHistory(); refreshLocal(); if (section?.browseId == "LOCAL_HISTORY") openSection(section) }
    fun removeHistoryItem(id: String) = viewModelScope.launch { LocalStore.removeHistoryItem(id); refreshLocal() }
    fun removeSearchHistoryItem(q: String) = viewModelScope.launch { LocalStore.removeSearch(q); refreshLocal() }
    fun toggleWatchLater(v: Video) = viewModelScope.launch { LocalStore.toggleWatchLater(v); refreshLocal() }
    fun clearAllLocalData() = viewModelScope.launch { LocalStore.clearAllLocal(); refreshLocal(); libVideos = emptyList() }

    fun setQuality(q: Int) = viewModelScope.launch { defaultQuality = q; LocalStore.setDefaultQuality(q) }
    fun setSpeedValue(s: Float) = viewModelScope.launch { playbackSpeed = s; LocalStore.setSpeed(s) }

    fun onLoggedIn() { loggedIn = true; showLogin = false; loadHome(); loadSubs() }

    fun logout() {
        Auth.logout(); loggedIn = false; section = null
        libVideos = emptyList(); libPlaylists = emptyList(); subsFeed = emptyList()
        loadHome()
    }
}
