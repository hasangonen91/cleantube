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
    var playing by mutableStateOf<Video?>(null)
    var showLogin by mutableStateOf(false)
    var loggedIn by mutableStateOf(Auth.loggedIn)

    var home by mutableStateOf<List<Video>>(emptyList())
    var homeLoading by mutableStateOf(false)
    var homeError by mutableStateOf<String?>(null)

    var query by mutableStateOf("")
    var results by mutableStateOf<List<Video>>(emptyList())
    var searching by mutableStateOf(false)
    var searchError by mutableStateOf<String?>(null)

    var section by mutableStateOf<Section?>(null)
    var libVideos by mutableStateOf<List<Video>>(emptyList())
    var libPlaylists by mutableStateOf<List<PlaylistItem>>(emptyList())
    var libLoading by mutableStateOf(false)
    var libError by mutableStateOf<String?>(null)

    init { loadHome() }

    fun loadHome() = viewModelScope.launch {
        homeLoading = true; homeError = null
        try {
            home = if (Auth.loggedIn) {
                runCatching { InnerTube.videos("FEwhat_to_watch") }.getOrNull()?.takeIf { it.isNotEmpty() } ?: Repo.trending()
            } else Repo.trending()
        } catch (e: Exception) { homeError = e.message ?: "Yüklenemedi" }
        homeLoading = false
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
