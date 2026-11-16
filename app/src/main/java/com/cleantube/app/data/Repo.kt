@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.cleantube.app.data

import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.InfoItem
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.MediaItem.SubtitleConfiguration
import android.net.Uri

/** Reklamsiz icerik: NewPipeExtractor dogrudan akis adreslerini verir, reklam verisi hic alinmaz. */
object Repo {
    private val yt get() = ServiceList.YouTube
    private val idRegex = Regex("(?:v=|shorts/|youtu\\.be/|/embed/)([A-Za-z0-9_-]{11})")
    private val chRegex = Regex("(?:channel/|@)([A-Za-z0-9_-]+)")

    private fun StreamInfoItem.toVideo(): Video? {
        val id = idRegex.find(url)?.groupValues?.get(1) ?: return null
        val channelId = chRegex.find(uploaderUrl.orEmpty())?.groupValues?.get(1).orEmpty()
        val durSec = duration
        return Video(
            id = id,
            title = name.orEmpty(),
            channel = uploaderName.orEmpty(),
            channelId = channelId,
            channelAvatar = uploaderAvatarUrl.orEmpty(),
            durationText = fmtDuration(durSec),
            views = fmtViews(viewCount),
            published = textualUploadDate.orEmpty(),
            isLive = streamType == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM || streamType == org.schabi.newpipe.extractor.stream.StreamType.AUDIO_STREAM,
            isShort = durSec in 1..60,
        )
    }

    private fun ChannelInfoItem.toChannel(): Channel = Channel(
        id = url.substringAfterLast("/"),
        name = name.orEmpty(),
        avatar = thumbnailUrl.orEmpty(),
        subscribers = if (subscriberCount >= 0) fmtCount(subscriberCount) + " abone" else "",
        description = description.orEmpty(),
        verified = isVerified,
    )

    private fun PlaylistInfoItem.toPlaylist(): PlaylistItem {
        val pid = url.substringAfter("list=").substringBefore("&")
        return PlaylistItem(
            id = pid.ifBlank { url.substringAfterLast("=") },
            title = name.orEmpty(),
            thumb = thumbnailUrl.orEmpty(),
            count = if (streamCount >= 0) "$streamCount video" else "",
            author = uploaderName.orEmpty(),
        )
    }

    data class SearchPage(
        val videos: List<Video>,
        val channels: List<Channel>,
        val playlists: List<PlaylistItem>,
        val nextPage: Page?,
    )

    private var lastSearchExtractor: org.schabi.newpipe.extractor.search.SearchExtractor? = null

    suspend fun search(q: String): List<Video> = searchPage(q).videos

    suspend fun searchPage(q: String): SearchPage = withContext(Dispatchers.IO) {
        val ex = yt.getSearchExtractor(q)
        ex.fetchPage()
        lastSearchExtractor = ex
        val items = ex.initialPage.items
        SearchPage(
            items.filterIsInstance<StreamInfoItem>().mapNotNull { it.toVideo() }.distinctBy { it.id },
            items.filterIsInstance<ChannelInfoItem>().map { it.toChannel() }.distinctBy { it.id },
            items.filterIsInstance<PlaylistInfoItem>().map { it.toPlaylist() }.distinctBy { it.id },
            ex.nextPage,
        )
    }

    suspend fun searchAll(q: String): Triple<List<Video>, List<Channel>, List<PlaylistItem>> {
        val p = searchPage(q)
        return Triple(p.videos, p.channels, p.playlists)
    }

    suspend fun searchMore(page: Page): SearchPage = withContext(Dispatchers.IO) {
        val ex = lastSearchExtractor ?: return@withContext SearchPage(emptyList(), emptyList(), emptyList(), null)
        val info = runCatching { SearchInfo.getMoreItems(yt, ex, page) }.getOrNull()
            ?: return@withContext SearchPage(emptyList(), emptyList(), emptyList(), null)
        val items = info.items
        SearchPage(
            items.filterIsInstance<StreamInfoItem>().mapNotNull { it.toVideo() }.distinctBy { it.id },
            items.filterIsInstance<ChannelInfoItem>().map { it.toChannel() }.distinctBy { it.id },
            items.filterIsInstance<PlaylistInfoItem>().map { it.toPlaylist() }.distinctBy { it.id },
            info.nextPage,
        )
    }

    suspend fun suggestions(q: String): List<String> = withContext(Dispatchers.IO) {
        runCatching { yt.suggestionExtractor.suggestionList(q).take(8) }.getOrDefault(emptyList())
    }

    data class VideoPage(val videos: List<Video>, val nextPage: Page?, val kiosk: org.schabi.newpipe.extractor.kiosk.KioskExtractor<*>? = null)

    private var lastKiosk: org.schabi.newpipe.extractor.kiosk.KioskExtractor<*>? = null
    private var lastFeedQuery: String? = null

    suspend fun trendingPage(): VideoPage = withContext(Dispatchers.IO) {
        runCatching {
            val k = yt.kioskList.defaultKioskExtractor
            k.fetchPage()
            lastKiosk = k; lastFeedQuery = null
            VideoPage(k.initialPage.items.filterIsInstance<StreamInfoItem>().mapNotNull { it.toVideo() }.distinctBy { it.id }, k.nextPage, k)
        }.getOrElse {
            val p = searchPage("popüler videolar"); lastKiosk = null; lastFeedQuery = "popüler videolar"
            VideoPage(p.videos, p.nextPage, null)
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
        if (q == null) trendingPage() else {
            lastKiosk = null; lastFeedQuery = q
            val p = searchPage(q)
            VideoPage(p.videos, p.nextPage, null)
        }
    }

    suspend fun homeMore(page: Page): VideoPage = withContext(Dispatchers.IO) {
        val k = lastKiosk
        if (k != null) {
            runCatching {
                val more = org.schabi.newpipe.extractor.kiosk.KioskInfo.getMoreItems(yt, k, page)
                VideoPage(more.items.filterIsInstance<StreamInfoItem>().mapNotNull { it.toVideo() }.distinctBy { it.id }, more.nextPage, k)
            }.getOrDefault(VideoPage(emptyList(), null, k))
        } else {
            val p = runCatching { searchMore(page) }.getOrNull() ?: Repo.SearchPage(emptyList(), emptyList(), emptyList(), null)
            VideoPage(p.videos, p.nextPage, null)
        }
    }

    suspend fun shortsPage(): VideoPage = withContext(Dispatchers.IO) {
        val p = searchPage("YouTube Shorts popüler")
        lastFeedQuery = "YouTube Shorts popüler"
        val vids = p.videos.filter {
            val seconds = it.durationText.split(":").mapNotNull(String::toIntOrNull)
                .fold(0L) { total, part -> total * 60 + part }
            seconds in 1..61 || it.isShort
        }.distinctBy { it.id }
        VideoPage(vids, p.nextPage, null)
    }

    suspend fun shorts(): List<Video> = shortsPage().videos

    suspend fun shortsMore(page: Page): VideoPage = withContext(Dispatchers.IO) {
        val p = searchMore(page)
        val vids = p.videos.filter {
            val seconds = it.durationText.split(":").mapNotNull(String::toIntOrNull)
                .fold(0L) { total, part -> total * 60 + part }
            seconds in 1..61 || it.isShort
        }.distinctBy { it.id }
        VideoPage(vids, p.nextPage, null)
    }

    suspend fun resolve(id: String): StreamInfo = withContext(Dispatchers.IO) {
        StreamInfo.getInfo("https://www.youtube.com/watch?v=$id")
    }

    fun related(info: StreamInfo): List<Video> =
        info.relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull { it.toVideo() }.distinctBy { it.id }

    data class ChannelPage(val info: ChannelInfo, val videos: List<Video>, val nextPage: Page?)
    data class CommentsPage(val comments: List<Comment>, val nextPage: Page?, val info: CommentsInfo?)

    suspend fun channelPage(channelIdOrUrl: String): ChannelPage = withContext(Dispatchers.IO) {
        val url = when {
            channelIdOrUrl.startsWith("http") -> channelIdOrUrl
            channelIdOrUrl.startsWith("@") -> "https://www.youtube.com/$channelIdOrUrl"
            channelIdOrUrl.startsWith("UC") -> "https://www.youtube.com/channel/$channelIdOrUrl"
            else -> "https://www.youtube.com/$channelIdOrUrl"
        }
        val info = ChannelInfo.getInfo(url)
        ChannelPage(info, info.relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull { it.toVideo() }.distinctBy { it.id }, info.nextPage)
    }

    suspend fun channel(channelIdOrUrl: String): ChannelInfo = channelPage(channelIdOrUrl).info
    fun channelVideos(info: ChannelInfo): List<Video> =
        info.relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull { it.toVideo() }.distinctBy { it.id }

    suspend fun channelMore(info: ChannelInfo, page: Page): ChannelPage = withContext(Dispatchers.IO) {
        runCatching {
            val more = ChannelInfo.getMoreItems(yt, info, page)
            ChannelPage(info, more.items.filterIsInstance<StreamInfoItem>().mapNotNull { it.toVideo() }.distinctBy { it.id }, more.nextPage)
        }.getOrElse { ChannelPage(info, emptyList(), null) }
    }

    suspend fun comments(videoId: String): List<Comment> = commentsPage(videoId).comments

    suspend fun commentsPage(videoId: String): CommentsPage = withContext(Dispatchers.IO) {
        runCatching {
            val info = CommentsInfo.getInfo("https://www.youtube.com/watch?v=$videoId")
            val list = info.relatedItems.mapIndexed { i, c ->
                Comment(
                    id = c.commentId ?: "c$i",
                    author = c.uploaderName.orEmpty(),
                    avatar = c.uploaderAvatarUrl.orEmpty(),
                    text = c.commentText?.content.orEmpty(),
                    published = c.textualUploadDate.orEmpty(),
                    likes = if (c.likeCount >= 0) fmtCount(c.likeCount.toLong()) else "",
                    replyCount = c.replyCount,
                )
            }.filter { it.text.isNotBlank() }
            CommentsPage(list, info.nextPage, info)
        }.getOrDefault(CommentsPage(emptyList(), null, null))
    }

    suspend fun commentsMore(info: CommentsInfo, page: Page): CommentsPage = withContext(Dispatchers.IO) {
        runCatching {
            val more = CommentsInfo.getMoreItems(yt, info, page)
            val list = more.items.mapIndexed { i, c ->
                Comment(
                    id = c.commentId ?: "m$i",
                    author = c.uploaderName.orEmpty(),
                    avatar = c.uploaderAvatarUrl.orEmpty(),
                    text = c.commentText?.content.orEmpty(),
                    published = c.textualUploadDate.orEmpty(),
                    likes = if (c.likeCount >= 0) fmtCount(c.likeCount.toLong()) else "",
                    replyCount = c.replyCount,
                )
            }.filter { it.text.isNotBlank() }
            CommentsPage(list, more.nextPage, info)
        }.getOrDefault(CommentsPage(emptyList(), null, info))
    }

    private fun List<VideoStream>.progressive() = filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
    @JvmName("progressiveAudio")
    private fun List<AudioStream>.progressive() = filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }

    /** Secilebilir kaliteler (yukseklik). */
    fun qualities(info: StreamInfo): List<Int> =
        (info.videoOnlyStreams.progressive().map { it.height } + info.videoStreams.progressive().map { it.height })
            .filter { it > 0 }.distinct().sortedDescending()

    private fun subtitleConfigs(info: StreamInfo): List<SubtitleConfiguration> = runCatching {
        info.subtitles.orEmpty().mapNotNull { s ->
            val url = s.content ?: return@mapNotNull null
            val lang = s.languageTag ?: s.code ?: "und"
            val mime = when {
                url.contains(".vtt", true) -> MimeTypes.TEXT_VTT
                url.contains(".srt", true) -> MimeTypes.APPLICATION_SUBRIP
                url.contains(".ttml", true) || url.contains(".xml", true) -> MimeTypes.APPLICATION_TTML
                else -> MimeTypes.TEXT_VTT
            }
            SubtitleConfiguration.Builder(Uri.parse(url))
                .setMimeType(mime)
                .setLanguage(lang)
                .setLabel(lang)
                .setSelectionFlags(if (info.subtitlesDefault == s) C.SELECTION_FLAG_DEFAULT else 0)
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
