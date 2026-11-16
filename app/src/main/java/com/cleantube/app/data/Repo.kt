@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.cleantube.app.data

import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream

/** Reklamsiz icerik: NewPipeExtractor dogrudan akis adreslerini verir, reklam verisi hic alinmaz. */
object Repo {
    private val yt get() = ServiceList.YouTube
    private val idRegex = Regex("(?:v=|shorts/|youtu\\.be/|/embed/)([A-Za-z0-9_-]{11})")

    private fun StreamInfoItem.toVideo(): Video? {
        val id = idRegex.find(url)?.groupValues?.get(1) ?: return null
        return Video(id, name.orEmpty(), uploaderName.orEmpty(), fmtDuration(duration), fmtViews(viewCount), textualUploadDate.orEmpty())
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

    fun related(info: StreamInfo): List<Video> =
        info.relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull { it.toVideo() }.distinctBy { it.id }

    private fun List<VideoStream>.progressive() = filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
    @JvmName("progressiveAudio")
    private fun List<AudioStream>.progressive() = filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }

    /** Secilebilir kaliteler (yukseklik). */
    fun qualities(info: StreamInfo): List<Int> =
        (info.videoOnlyStreams.progressive().map { it.height } + info.videoStreams.progressive().map { it.height })
            .filter { it > 0 }.distinct().sortedDescending()

    fun buildSource(info: StreamInfo, maxHeight: Int, f: DataSource.Factory): MediaSource? {
        val pf = ProgressiveMediaSource.Factory(f)
        fun src(url: String) = pf.createMediaSource(MediaItem.fromUri(url))

        val vOnly = info.videoOnlyStreams.progressive().filter { it.height in 1..maxHeight }
        val audios = info.audioStreams.progressive()
        // 1) MP4 video + M4A ses
        val mp4v = vOnly.filter { it.format == MediaFormat.MPEG_4 }.maxByOrNull { it.height }
        val m4a = audios.filter { it.format == MediaFormat.M4A }.maxByOrNull { it.averageBitrate }
        if (mp4v != null && m4a != null) return MergingMediaSource(src(mp4v.content), src(m4a.content))
        // 2) WebM video + WebM ses
        val wv = vOnly.filter { it.format == MediaFormat.WEBM }.maxByOrNull { it.height }
        val wa = audios.filter { it.format == MediaFormat.WEBMA }.maxByOrNull { it.averageBitrate }
        if (wv != null && wa != null) return MergingMediaSource(src(wv.content), src(wa.content))
        // 3) Birlesik (sesli) akis
        val muxed = info.videoStreams.progressive().filter { it.height in 1..maxHeight }.maxByOrNull { it.height }
            ?: info.videoStreams.progressive().maxByOrNull { it.height }
        if (muxed != null) return src(muxed.content)
        // 4) Canli yayin / HLS
        val hls = info.hlsUrl
        if (!hls.isNullOrBlank()) return HlsMediaSource.Factory(f).createMediaSource(MediaItem.fromUri(hls))
        return null
    }
}
