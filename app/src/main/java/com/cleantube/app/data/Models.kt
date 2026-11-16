package com.cleantube.app.data

data class Video(
    val id: String,
    val title: String,
    val channel: String = "",
    val durationText: String = "",
    val views: String = "",
    val published: String = "",
) {
    val thumb: String get() = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
    val meta: String get() = listOf(channel, views, published).filter { it.isNotBlank() }.joinToString(" • ")
}

data class PlaylistItem(val id: String, val title: String)

data class Section(val title: String, val browseId: String, val isPlaylists: Boolean = false)

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
