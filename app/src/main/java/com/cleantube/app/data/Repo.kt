@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.cleantube.app.data

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.SubtitleConfiguration
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.linkhandler.LinkHandler
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

/** Reklamsiz icerik: NewPipeExtractor dogrudan akis adreslerini verir, reklam verisi hic alinmaz. */
object Repo {
    private val yt get() = ServiceList.YouTube
    private val idRegex = Regex("(?:v=|shorts/|youtu\\.be/|/embed/)([A-Za-z0-9_-]{11})")
    private val chRegex = Regex("(?:channel/|@)([A-Za-z0-9_-]+)")

    private fun thumbOf(item: Any): String = runCatching {
        item.javaClass.methods.firstOrNull { it.name == "getThumbnailUrl" && it.parameterCount == 0 }?.invoke(item) as? String
    }.getOrNull().orEmpty()

    private fun uploaderAvatarOf(item: Any): String = runCatching {
        item.javaClass.methods.firstOrNull { it.name == "getUploaderAvatarUrl" && it.parameterCount == 0 }?.invoke(item) as? String
    }.getOrNull().orEmpty()

    private fun StreamInfoItem.toVideo(): Video? {
        val id = idRegex.find(url)?.groupValues?.get(1) ?: return null
        val channelId = chRegex.find(uploaderUrl.orEmpty())?.groupValues?.get(1).orEmpty()
        val durSec = duration
        val live = runCatching { streamType == StreamType.LIVE_STREAM || streamType == StreamType.AUDIO_STREAM }.getOrDefault(false)
        return Video(
            id = id,
            title = name.orEmpty(),
            channel = uploaderName.orEmpty(),
            channelId = channelId,
            channelAvatar = uploaderAvatarOf(this),
            durationText = fmtDuration(durSec),
            views = runCatching { fmtViews(viewCount) }.getOrDefault(""),
            published = runCatching { textualUploadDate.orEmpty() }.getOrDefault(""),
            isLive = live,
            isShort = durSec in 1..61,
        )
    }

    private fun ChannelInfoItem.toChannel(): Channel = Channel(
        id = url.substringAfterLast("/"),
        name = name.orEmpty(),
        avatar = thumbOf(this),
        subscribers = runCatching { if (subscriberCount >= 0) fmtCount(subscriberCount) + " abone" else "" }.getOrDefault(""),
        description = runCatching { description.orEmpty() }.getOrDefault(""),
        verified = runCatching { isVerified }.getOrDefault(false),
    )

    private fun PlaylistInfoItem.toPlaylist(): PlaylistItem {
        val pid = url.substringAfter("list=").substringBefore("&")
        return PlaylistItem(
            id = pid.ifBlank { url.substringAfterLast("=") },
            title = name.orEmpty(),
            thumb = thumbOf(this),
            count = runCatching { if (streamCount >= 0) "$streamCount video" else "" }.getOrDefault(""),
            author = runCatching { uploaderName.orEmpty() }.getOrDefault(""),
        )
    }

    data class SearchPage(
        val videos: List<Video>,
        val channels: List<Channel>,
        val playlists: List<PlaylistItem>,
        val nextPage: Page? = null,
    )
    data class VideoPage(val videos: List<Video>, val nextPage: Page? = null)
    data class ChannelPage(val info: ChannelInfo, val videos: List<Video>, val nextPage: Page? = null)
    data class CommentsPage(val comments: List<Comment>, val nextPage: Page? = null, val info: CommentsInfo? = null)

    suspend fun search(q: String): List<Video> = searchPage(q).videos

    suspend fun searchPage(q: String): SearchPage = withContext(Dispatchers.IO) {
        runCatching {
            val ex = yt.getSearchExtractor(q)
            ex.fetchPage()
            val items = ex.initialPage.items
            SearchPage(
                videos = items.filterIsInstance<StreamInfoItem>().mapNotNull { it.toVideo() }.distinctBy { it.id },
                channels = items.filterIsInstance<ChannelInfoItem>().map { it.toChannel() }.distinctBy { it.id },
                playlists = items.filterIsInstance<PlaylistInfoItem>().map { it.toPlaylist() }.distinctBy { it.id },
                nextPage = null, // NewPipe paging API differs across versions; InnerTube continuation used where available
            )
        }.getOrDefault(SearchPage(emptyList(), emptyList(), emptyList(), null))
    }

    suspend fun searchAll(q: String): Triple<List<Video>, List<Channel>, List<PlaylistItem>> {
        val p = searchPage(q)
        return Triple(p.videos, p.channels, p.playlists)
    }

    suspend fun searchMore(page: Page): SearchPage = withContext(Dispatchers.IO) { SearchPage(emptyList(), emptyList(), emptyList(), null) }

    suspend fun suggestions(q: String): List<String> = withContext(Dispatchers.IO) {
        runCatching { yt.suggestionExtractor.suggestionList(q).take(8) }.getOrDefault(emptyList())
    }

    suspend fun trendingPage(): VideoPage = withContext(Dispatchers.IO) {
        runCatching {
            val k = yt.kioskList.defaultKioskExtractor
            k.fetchPage()
            VideoPage(k.initialPage.items.filterIsInstance<StreamInfoItem>().mapNotNull { it.toVideo() }.distinctBy { it.id }, null)
        }.getOrElse {
            VideoPage(searchPage("popüler videolar").videos, null)
        }
    }

    suspend fun trending(): List<Video> = trendingPage().videos
    suspend fun feedForCategory(category: String): List<Video> = feedPage(category).videos

    suspend fun feedPage(category: String): VideoPage = withContext(Dispatchers.IO) {
        val q = when (category) {
            "Mixler" -> "popüler mix müzik"
            "Müzik" -> "popüler müzik"
            "Canlı" -> "canlı yayın"
            "Oyun" -> "oyun videoları"
            "Podcast'ler" -> "podcast"
            "Teknoloji" -> "teknoloji"
            "Haberler" -> "haberler"
            "Spor" -> "spor"
            else -> null
        }
        if (q == null) trendingPage() else VideoPage(searchPage(q).videos, null)
    }

    suspend fun homeMore(page: Page): VideoPage = withContext(Dispatchers.IO) { VideoPage(emptyList(), null) }

    suspend fun shortsPage(): VideoPage = withContext(Dispatchers.IO) {
        // Try InnerTube reels/shorts endpoints first
        for (browseId in listOf("FEshorts", "FEmusic_shorts", "TAshorts")) {
            val inner = runCatching { InnerTube.videos(browseId) }.getOrDefault(emptyList())
            if (inner.isNotEmpty()) {
                android.util.Log.i("CleanTube", "Shorts loaded from $browseId: ${inner.size}")
                return@withContext VideoPage(inner.distinctBy { it.id }, null)
            }
        }
        val queries = listOf("#shorts", "shorts", "kısa videolar", "YouTube Shorts popüler")
        for (q in queries) {
            val page = runCatching { searchPage(q) }.getOrDefault(SearchPage(emptyList(), emptyList(), emptyList(), null))
            val all = page.videos.distinctBy { it.id }
            android.util.Log.i("CleanTube", "Shorts query '$q' returned ${all.size}")
            if (all.isEmpty()) continue
            val filtered = all.filter {
                val seconds = it.durationText.split(":").mapNotNull(String::toIntOrNull)
                    .fold(0L) { total, part -> total * 60 + part }
                seconds in 1..180 || it.isShort
            }.distinctBy { it.id }
            val vids = if (filtered.isNotEmpty()) filtered else all.take(30)
            return@withContext VideoPage(vids, page.nextPage)
        }
        // Last resort: InnerTube home or trending videos, prefer short ones
        val innerHome = runCatching { InnerTube.videos("FEwhat_to_watch") }.getOrDefault(emptyList())
        val trending = runCatching { trendingPage().videos }.getOrDefault(emptyList())
        val pool = (innerHome + trending).distinctBy { it.id }
        val shortish = pool.filter {
            val seconds = it.durationText.split(":").mapNotNull(String::toIntOrNull)
                .fold(0L) { total, part -> total * 60 + part }
            seconds in 1..180
        }
        val fallback = (shortish.ifEmpty { pool }).distinctBy { it.id }
        android.util.Log.i("CleanTube", "Shorts fallback pool: ${fallback.size}")
        VideoPage(fallback, null)
    }

    suspend fun shorts(): List<Video> = shortsPage().videos
    suspend fun shortsMore(page: Page): VideoPage = withContext(Dispatchers.IO) { VideoPage(emptyList(), null) }

    suspend fun resolve(id: String): StreamInfo = withContext(Dispatchers.IO) {
        val url = "https://www.youtube.com/watch?v=$id"
        runCatching {
            val lh = LinkHandler(url, url, id)
            val extractor = yt.getStreamExtractor(lh)
            extractor.fetchPage()
            StreamInfo.getInfo(extractor)
        }.recoverCatching { StreamInfo.getInfo(yt, url) }
         .recoverCatching { StreamInfo.getInfo(url) }
         .getOrElse { e ->
            android.util.Log.e("CleanTube", "resolve failed", e)
            throw if (e is Exception) e else Exception(e.message ?: "Stream çözümlenemedi", e)
        }
    }

    private fun <T> relatedItemsOf(obj: Any, cls: Class<T>): List<T> = runCatching {
        val m = obj.javaClass.methods.firstOrNull { (it.name == "getRelatedItems" || it.name == "getRelatedStreams") && it.parameterCount == 0 }
        (m?.invoke(obj) as? List<*>)?.filterIsInstance(cls).orEmpty()
    }.getOrDefault(emptyList())

    fun related(info: StreamInfo): List<Video> =
        relatedItemsOf(info, StreamInfoItem::class.java).mapNotNull { it.toVideo() }.distinctBy { it.id }

    private fun channelUrl(channelIdOrUrl: String): String = when {
        channelIdOrUrl.startsWith("http") -> channelIdOrUrl
        channelIdOrUrl.startsWith("@") -> "https://www.youtube.com/$channelIdOrUrl"
        channelIdOrUrl.startsWith("UC") -> "https://www.youtube.com/channel/$channelIdOrUrl"
        else -> "https://www.youtube.com/$channelIdOrUrl"
    }

    suspend fun channelPage(channelIdOrUrl: String): ChannelPage = withContext(Dispatchers.IO) {
        val info = ChannelInfo.getInfo(yt, channelUrl(channelIdOrUrl))
        val videos = relatedItemsOf(info, StreamInfoItem::class.java).mapNotNull { it.toVideo() }.distinctBy { it.id }
        ChannelPage(info, videos, null)
    }

    suspend fun channel(channelIdOrUrl: String): ChannelInfo = channelPage(channelIdOrUrl).info
    fun channelVideos(info: ChannelInfo): List<Video> =
        relatedItemsOf(info, StreamInfoItem::class.java).mapNotNull { it.toVideo() }.distinctBy { it.id }

    suspend fun channelMore(info: ChannelInfo, page: Page): ChannelPage = withContext(Dispatchers.IO) { ChannelPage(info, emptyList(), null) }

    private fun CommentsInfoItem.toComment(i: Int): Comment = Comment(
        id = runCatching { commentId.orEmpty() }.getOrDefault("c$i"),
        author = runCatching { uploaderName.orEmpty() }.getOrDefault(""),
        avatar = uploaderAvatarOf(this),
        text = runCatching { commentText?.content.orEmpty() }.getOrDefault(""),
        published = runCatching { textualUploadDate.orEmpty() }.getOrDefault(""),
        likes = runCatching { if (likeCount >= 0) fmtCount(likeCount.toLong()) else "" }.getOrDefault(""),
        replyCount = runCatching { replyCount }.getOrDefault(0),
    )

    suspend fun comments(videoId: String): List<Comment> = commentsPage(videoId).comments

    suspend fun commentsPage(videoId: String): CommentsPage = withContext(Dispatchers.IO) {
        runCatching {
            val info = CommentsInfo.getInfo("https://www.youtube.com/watch?v=$videoId")
            val list = relatedItemsOf(info, CommentsInfoItem::class.java).mapIndexed { i, c -> c.toComment(i) }.filter { it.text.isNotBlank() }
            CommentsPage(list, null, info)
        }.getOrDefault(CommentsPage(emptyList(), null, null))
    }

    suspend fun commentsMore(info: CommentsInfo, page: Page): CommentsPage = withContext(Dispatchers.IO) { CommentsPage(emptyList(), null, info) }

    data class PlaylistPage(val title: String, val videos: List<Video>, val nextPage: Page? = null)

    suspend fun playlistPage(idOrUrl: String): PlaylistPage = withContext(Dispatchers.IO) {
        val url = when {
            idOrUrl.startsWith("http") -> idOrUrl
            idOrUrl.startsWith("RD") || idOrUrl.startsWith("PL") || idOrUrl.startsWith("UU") || idOrUrl.startsWith("OL") || idOrUrl.startsWith("VL") ->
                "https://www.youtube.com/playlist?list=${idOrUrl.removePrefix("VL")}"
            else -> "https://www.youtube.com/playlist?list=$idOrUrl"
        }
        runCatching {
            val info = PlaylistInfo.getInfo(yt, url)
            val videos = runCatching {
                info.relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull { it.toVideo() }.distinctBy { it.id }
            }.getOrElse { relatedItemsOf(info, StreamInfoItem::class.java).mapNotNull { it.toVideo() }.distinctBy { it.id } }
            PlaylistPage(runCatching { info.name.orEmpty() }.getOrDefault("Playlist"), videos, null)
        }.getOrDefault(PlaylistPage("Playlist", emptyList(), null))
    }

    private fun List<VideoStream>.progressive() = filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
    @JvmName("progressiveAudio")
    private fun List<AudioStream>.progressive() = filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }

    /** Secilebilir kaliteler (yukseklik). */
    fun qualities(info: StreamInfo): List<Int> =
        runCatching {
            (info.videoOnlyStreams.progressive().map { it.height } + info.videoStreams.progressive().map { it.height })
                .filter { it > 0 }.distinct().sortedDescending()
        }.getOrDefault(emptyList())

    private fun subtitleConfigs(info: StreamInfo): List<SubtitleConfiguration> = runCatching {
        info.subtitles.orEmpty().mapNotNull { s ->
            val url = runCatching { s.content }.getOrNull() ?: return@mapNotNull null
            val lang = runCatching { s.languageTag }.getOrNull().orEmpty().ifBlank { "und" }
            val mime = when {
                url.contains(".vtt", true) -> MimeTypes.TEXT_VTT
                url.contains(".srt", true) -> MimeTypes.APPLICATION_SUBRIP
                url.contains(".ttml", true) || url.contains(".xml", true) -> MimeTypes.APPLICATION_TTML
                else -> MimeTypes.TEXT_VTT
            }
            val isDefault = runCatching {
                val d = info.javaClass.methods.firstOrNull { it.name == "getSubtitlesDefault" && it.parameterCount == 0 }?.invoke(info)
                d != null && d == s
            }.getOrDefault(false)
            SubtitleConfiguration.Builder(Uri.parse(url))
                .setMimeType(mime)
                .setLanguage(lang)
                .setLabel(lang)
                .setSelectionFlags(if (isDefault) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }
    }.getOrDefault(emptyList())

    private fun mediaItem(url: String, subs: List<SubtitleConfiguration>): MediaItem =
        MediaItem.Builder().setUri(url).setSubtitleConfigurations(subs).build()

    fun buildSource(info: StreamInfo, maxHeight: Int, f: DataSource.Factory): MediaSource? = runCatching {
        val progressiveFactory = ProgressiveMediaSource.Factory(f)
        val subs = subtitleConfigs(info)
        fun source(url: String) = progressiveFactory.createMediaSource(mediaItem(url, subs))

        val muxed = info.videoStreams.progressive()
            .filter { it.height in 1..maxHeight }
            .maxByOrNull { it.height }
            ?: info.videoStreams.progressive().maxByOrNull { it.height }
        if (muxed != null) return@runCatching source(muxed.content)

        val videoOnly = info.videoOnlyStreams.progressive().filter { it.height in 1..maxHeight }
        val audioOnly = info.audioStreams.progressive()
        val mp4Video = videoOnly.filter { it.format == MediaFormat.MPEG_4 }.maxByOrNull { it.height }
        val m4aAudio = audioOnly.filter { it.format == MediaFormat.M4A }.maxByOrNull { it.averageBitrate }
        if (mp4Video != null && m4aAudio != null) {
            return@runCatching MergingMediaSource(source(mp4Video.content), source(m4aAudio.content))
        }

        val webmVideo = videoOnly.filter { it.format == MediaFormat.WEBM }.maxByOrNull { it.height }
        val webmAudio = audioOnly.filter { it.format == MediaFormat.WEBMA }.maxByOrNull { it.averageBitrate }
        if (webmVideo != null && webmAudio != null) {
            return@runCatching MergingMediaSource(source(webmVideo.content), source(webmAudio.content))
        }

        val dash = info.dashMpdUrl
        if (!dash.isNullOrBlank()) return@runCatching DashMediaSource.Factory(f).createMediaSource(MediaItem.Builder().setUri(dash).setSubtitleConfigurations(subs).build())
        val hls = info.hlsUrl
        if (!hls.isNullOrBlank()) return@runCatching HlsMediaSource.Factory(f).createMediaSource(MediaItem.Builder().setUri(hls).setSubtitleConfigurations(subs).build())
        null
    }.getOrNull()
}
