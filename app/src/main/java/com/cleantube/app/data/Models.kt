package com.cleantube.app.data

data class Video(
    val id: String,
    val title: String,
    val channel: String = "",
    val channelId: String = "",
    val channelAvatar: String = "",
    val durationText: String = "",
    val views: String = "",
    val published: String = "",
    val isLive: Boolean = false,
    val isShort: Boolean = false,
) {
    val thumb: String get() = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
    val meta: String get() = listOf(channel, views, published).filter { it.isNotBlank() }.joinToString(" • ")
}

data class Channel(
    val id: String,
    val name: String,
    val avatar: String = "",
    val banner: String = "",
    val subscribers: String = "",
    val description: String = "",
    val verified: Boolean = false,
)

data class ChannelTab(val title: String, val browseId: String, val params: String? = null)

data class PlaylistItem(
    val id: String,
    val title: String,
    val thumb: String = "",
    val count: String = "",
    val author: String = "",
)

data class Comment(
    val id: String,
    val author: String,
    val avatar: String = "",
    val text: String,
    val published: String = "",
    val likes: String = "",
    val replyCount: Int = 0,
)

data class PagedResult<T>(
    val items: List<T>,
    val nextPage: Any? = null,
    val error: String? = null,
)

data class DownloadItem(
    val videoId: String,
    val title: String,
    val fileName: String,
    val quality: String = "",
    val downloadId: Long = -1,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "queued", // queued, completed, failed
)

data class Section(val title: String, val browseId: String, val isPlaylists: Boolean = false, val isSubscriptions: Boolean = false)

data class SearchSuggestion(val text: String)

enum class SearchFilter(val label: String, val param: String?) {
    ALL("Tümü", null),
    VIDEO("Videolar", "EgIQAQ%3D%3D"),
    CHANNEL("Kanallar", "EgIQAg%3D%3D"),
    PLAYLIST("Oynatma listeleri", "EgIQAw%3D%3D"),
    LIVE("Canlı", "EgJAAQ%3D%3D"),
}

fun fmtDuration(sec: Long): String {
    if (sec <= 0) return ""
    val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun fmtViews(n: Long): String = when {
    n < 0 -> ""
    n >= 1_000_000_000 -> "%.1f Mr görüntüleme".format(n / 1e9)
    n >= 1_000_000 -> "%.1f Mn görüntüleme".format(n / 1e6)
    n >= 1_000 -> "%d B görüntüleme".format(n / 1000)
    else -> "$n görüntüleme"
}

fun fmtCount(n: Long): String = when {
    n < 0 -> ""
    n >= 1_000_000_000 -> "%.1f Mr".format(n / 1e9)
    n >= 1_000_000 -> "%.1f Mn".format(n / 1e6)
    n >= 1_000 -> "%.1f B".format(n / 1e3)
    else -> "$n"
}
