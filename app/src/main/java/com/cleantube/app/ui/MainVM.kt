package com.cleantube.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleantube.app.data.*
import kotlinx.coroutines.launch

class MainVM : ViewModel() {
    var tab by mutableStateOf(0)
    var categoryIndex by mutableStateOf(0)
    var playing by mutableStateOf<Video?>(null)
    var showLogin by mutableStateOf(false)
    var loggedIn by mutableStateOf(Auth.loggedIn)

    var home by mutableStateOf<List<Video>>(emptyList())
    var homeLoading by mutableStateOf(false)
    var homeError by mutableStateOf<String?>(null)

    var shorts by mutableStateOf<List<Video>>(emptyList())
    var shortsLoading by mutableStateOf(false)

    var query by mutableStateOf("")
    var results by mutableStateOf<List<Video>>(emptyList())
    var searching by mutableStateOf(false)
    var searchError by mutableStateOf<String?>(null)

    var section by mutableStateOf<Section?>(null)
    var libVideos by mutableStateOf<List<Video>>(emptyList())
    var libPlaylists by mutableStateOf<List<PlaylistItem>>(emptyList())
    var libLoading by mutableStateOf(false)
    var libError by mutableStateOf<String?>(null)

    init { loadHome(); loadShorts() }

    fun loadHome() = viewModelScope.launch {
        categoryIndex = 0
        homeLoading = true; homeError = null
        try {
            home = if (Auth.loggedIn) {
                runCatching { InnerTube.videos("FEwhat_to_watch") }.getOrNull()?.takeIf { it.isNotEmpty() } ?: Repo.trending()
            } else Repo.trending()
        } catch (e: Exception) { homeError = e.message ?: "Yüklenemedi" }
        homeLoading = false
    }

    fun loadShorts() = viewModelScope.launch {
        if (shorts.isNotEmpty()) return@launch
        shortsLoading = true
        try {
            shorts = Repo.search("YouTube Shorts popüler").filter {
                val seconds = it.durationText.split(":").mapNotNull(String::toIntOrNull)
                    .fold(0L) { total, part -> total * 60 + part }
                seconds in 1..180
            }.take(30)
        } catch (_: Exception) {
            shorts = emptyList()
        }
        shortsLoading = false
    }

    fun search() = viewModelScope.launch {
        val q = query.trim()
        if (q.isEmpty()) return@launch
        searching = true; searchError = null
        try { results = Repo.search(q) } catch (e: Exception) { searchError = e.message ?: "Arama başarısız" }
        searching = false
    }

    fun openSection(s: Section?) {
        section = s
        libVideos = emptyList(); libPlaylists = emptyList(); libError = null
        if (s == null) return
        viewModelScope.launch {
            libLoading = true
            try {
                if (s.isPlaylists) libPlaylists = InnerTube.playlists() else libVideos = InnerTube.videos(s.browseId)
            } catch (e: Exception) { libError = e.message ?: "Yüklenemedi" }
            libLoading = false
        }
    }

    fun onLoggedIn() { loggedIn = true; showLogin = false; loadHome() }

    fun logout() {
        Auth.logout(); loggedIn = false; section = null
        libVideos = emptyList(); libPlaylists = emptyList(); loadHome()
    }
}
