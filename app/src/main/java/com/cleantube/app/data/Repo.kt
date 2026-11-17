@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.cleantube.app.data

import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.ServiceList
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

    private fun StreamInfoItem.toVideo(): Video? {
        val id = idRegex.find(url)?.groupValues?.get(1) ?: return null
        val isShort = runCatching { isShortFormContent }.getOrDefault(false)
        return Video(id, name.orEmpty(), uploaderName.orEmpty(), fmtDuration(duration), fmtViews(viewCount), textualUploadDate.orEmpty(), isShort)
    }

    suspend fun search(q: String): List<Video> = withContext(Dispatchers.IO) {
        val ex = yt.getSearchExtractor(q)
        ex.fetchPage()
        ex.initialPage.items.filterIsInstance<StreamInfoItem>().mapNotNull { it.toVideo() }.distinctBy { it.id }
    }

    suspend fun trending(): List<Video> = withContext(Dispatchers.IO) {
        val list = runCatching {
            val k = yt.kioskList.defaultKioskExtractor
            k.fetchPage()
            k.initialPage.items.filterIsInstance<StreamInfoItem>().mapNotNull { it.toVideo() }.distinctBy { it.id }
        }.getOrNull()
        if (!list.isNullOrEmpty()) list else search("popüler videolar")
    }

    suspend fun resolve(id: String): StreamInfo = withContext(Dispatchers.IO) {
        StreamInfo.getInfo("https://www.youtube.com/watch?v=$id")
    }

    /** NewPipe'in YouTube yorum ayrıştırıcısı modern yorum entity'lerini ve continuation sayfalarını yönetir. */
    suspend fun comments(videoId: String): List<CommentItem> = withContext(Dispatchers.IO) {
        val url = "https://www.youtube.com/watch?v=$videoId"
        val info = CommentsInfo.getInfo(yt, url) ?: return@withContext emptyList()
        if (info.isCommentsDisabled) return@withContext emptyList()
        info.relatedItems.mapNotNull { item ->
            val id = item.commentId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val text = item.commentText.content().trim()
            if (text.isBlank()) return@mapNotNull null
            CommentItem(
                id = id,
                author = item.uploaderName.orEmpty(),
                text = text,
                likes = item.textualLikeCount.orEmpty(),
                avatar = item.uploaderAvatars.firstOrNull()?.url.orEmpty(),
            )
        }.distinctBy { it.id }.take(50)
    }

    fun related(info: StreamInfo): List<Video> =
        info.relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull { it.toVideo() }.distinctBy { it.id }

    private fun List<VideoStream>.progressive() = filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
    @JvmName("progressiveAudio")
    private fun List<AudioStream>.progressive() = filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }

    /** Secilebilir kaliteler (yukseklik). */
    fun qualities(info: StreamInfo): List<Int> =
        (info.videoOnlyStreams.progressive().map { it.height } + info.videoStreams.progressive().map { it.height })
            .filter { it > 0 }.distinct().sortedDescending()

    /**
     * Yalnizca ses kaynaklari. Video stream'i eklenmez; tercih edilen bitrate 96 kbps
     * altindaki M4A, bulunamazsa en dusuk bitrate'li progressive ses akisi.
     */
    fun audioCandidates(info: StreamInfo, f: DataSource.Factory, maxBitrate: Int = Prefs.MAX_AUDIO_BITRATE): List<() -> MediaSource> {
        val streams = info.audioStreams.filter {
            it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl
        }
        val preferred = streams.filter { it.averageBitrate in 1..maxBitrate }
        val selected = preferred.filter { it.format == MediaFormat.M4A }.maxByOrNull { it.averageBitrate }
            ?: preferred.maxByOrNull { it.averageBitrate }
            ?: streams.filter { it.format == MediaFormat.M4A }.minByOrNull { it.averageBitrate }
            ?: streams.minByOrNull { it.averageBitrate }
            ?: return emptyList()
        val source = ProgressiveMediaSource.Factory(f).createMediaSource(MediaItem.fromUri(selected.content))
        return listOf({ source })
    }

    /**
     * Oynatma icin SIRALI aday kaynaklar. Biri hata verirse oynatici siradakini dener
     * (progressive -> ayri ses/goruntu birlestirme -> DASH -> HLS). Her aday tembel uretilir.
     */
    fun candidates(info: StreamInfo, maxHeight: Int, f: DataSource.Factory): List<() -> MediaSource> {
        val out = ArrayList<() -> MediaSource>()
        val progressiveFactory = ProgressiveMediaSource.Factory(f)
        fun source(url: String) = progressiveFactory.createMediaSource(MediaItem.fromUri(url))

        val hls = info.hlsUrl?.takeIf { it.isNotBlank() }
        val dash = info.dashMpdUrl?.takeIf { it.isNotBlank() }
        val live = info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM
        // Canli yayinda once HLS.
        if (live && hls != null) out += { HlsMediaSource.Factory(f).createMediaSource(MediaItem.fromUri(hls)) }

        val muxed = info.videoStreams.progressive().filter { it.height in 1..maxHeight }.maxByOrNull { it.height }
            ?: info.videoStreams.progressive().minByOrNull { it.height }
        val videoOnly = info.videoOnlyStreams.progressive().filter { it.height in 1..maxHeight }
        val audioOnly = info.audioStreams.progressive()
        // Veri tasarrufu: ~130 kbps ve alti sesler arasindan en iyisi, yoksa en dusugu.
        fun pickAudio(fmt: MediaFormat): AudioStream? {
            val l = audioOnly.filter { it.format == fmt }
            return l.filter { it.averageBitrate in 1..130 }.maxByOrNull { it.averageBitrate } ?: l.minByOrNull { it.averageBitrate }
        }
        val mp4Video = videoOnly.filter { it.format == MediaFormat.MPEG_4 }.maxByOrNull { it.height }
        val m4aAudio = pickAudio(MediaFormat.M4A)
        val webmVideo = videoOnly.filter { it.format == MediaFormat.WEBM }.maxByOrNull { it.height }
        val webmAudio = pickAudio(MediaFormat.WEBMA)

        class Opt(val height: Int, val priority: Int, val make: () -> MediaSource)
        val opts = ArrayList<Opt>()
        muxed?.let { m -> opts.add(Opt(m.height, 0) { source(m.content) }) }
        if (mp4Video != null && m4aAudio != null) opts.add(Opt(mp4Video.height, 1) { MergingMediaSource(source(mp4Video.content), source(m4aAudio.content)) })
        if (webmVideo != null && webmAudio != null) opts.add(Opt(webmVideo.height, 2) { MergingMediaSource(source(webmVideo.content), source(webmAudio.content)) })
        // Secilen kaliteye en yakin (en yuksek) once; esitlikte hazir birlesik akis once.
        opts.sortedWith(compareByDescending<Opt> { it.height }.thenBy { it.priority }).forEach { out.add(it.make) }
        if (dash != null) out += { DashMediaSource.Factory(f).createMediaSource(MediaItem.fromUri(dash)) }
        if (!live && hls != null) out += { HlsMediaSource.Factory(f).createMediaSource(MediaItem.fromUri(hls)) }
        return out
    }
}
