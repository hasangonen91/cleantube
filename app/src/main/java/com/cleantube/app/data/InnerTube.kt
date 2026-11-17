package com.cleantube.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Giris yapilmis hesabin kitapligi / onerileri. Sadece video ve liste ogeleri okunur; reklam ogeleri atlanir. */
object InnerTube {
    private const val O = "https://www.youtube.com"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
    private val client = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()
    private var version: String? = null

    private fun clientVersion(): String {
        version?.let { return it }
        val v = runCatching {
            client.newCall(Request.Builder().url("$O/").header("User-Agent", UA).build()).execute().use { r ->
                Regex("\"INNERTUBE_CONTEXT_CLIENT_VERSION\":\"([^\"]+)\"").find(r.body?.string().orEmpty())?.groupValues?.get(1)
            }
        }.getOrNull() ?: "2.20250101.00.00"
        version = v
        return v
    }

    private suspend fun browse(id: String): JSONObject = withContext(Dispatchers.IO) {
        val cookie = Auth.cookies
        val ver = clientVersion()
        val ctx = JSONObject().put(
            "client", JSONObject().put("clientName", "WEB").put("clientVersion", ver).put("hl", "tr").put("gl", "TR")
        )
        val body = JSONObject().put("context", ctx).put("browseId", id).toString()
            .toRequestBody("application/json".toMediaType())
        val builder = Request.Builder().url("$O/youtubei/v1/browse?prettyPrint=false").post(body)
            .header("X-Origin", O).header("Origin", O).header("Referer", "$O/")
            .header("User-Agent", UA)
            .header("X-Youtube-Client-Name", "1").header("X-Youtube-Client-Version", ver)
        if (!cookie.isNullOrBlank()) {
            builder.header("Cookie", cookie)
                .header("Authorization", Auth.authHeader())
                .header("X-Goog-AuthUser", "0")
        }
        val req = builder.build()
        client.newCall(req).execute().use { r ->
            if (r.code == 401 || r.code == 403) error("Oturum süresi dolmuş, tekrar giriş yap")
            if (!r.isSuccessful) error("Sunucu hatası (${r.code})")
            JSONObject(r.body?.string().orEmpty())
        }
    }

    suspend fun videos(browseId: String): List<Video> {
        val out = LinkedHashMap<String, Video>()
        collect(browse(browseId), out, null)
        return out.values.toList()
    }

    suspend fun playlists(): List<PlaylistItem> {
        val out = LinkedHashMap<String, Video>()
        val pl = LinkedHashMap<String, PlaylistItem>()
        collect(browse("FEplaylist_aggregation"), out, pl)
        return pl.values.toList()
    }

    /** Public music feed: Mixler oturum açmadan da listelenebilir. */
    suspend fun mixes(): List<PlaylistItem> = withContext(Dispatchers.IO) {
        val out = LinkedHashMap<String, Video>()
        val pl = LinkedHashMap<String, PlaylistItem>()
        collect(browse("FEmusic"), out, pl)
        pl.values.toList()
    }


    suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val url = "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q=" + Uri.encode(query)
        client.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).execute().use { r ->
            if (!r.isSuccessful) return@withContext emptyList()
            val arr = JSONArray(r.body?.string().orEmpty())
            val values = arr.optJSONArray(1) ?: JSONArray()
            (0 until values.length()).mapNotNull { values.optString(it).takeIf(String::isNotBlank) }.distinct().take(8)
        }
    }

    suspend fun comments(videoId: String): List<CommentItem> = withContext(Dispatchers.IO) {
        val ver = clientVersion()
        val ctx = JSONObject().put(
            "client",
            JSONObject().put("clientName", "WEB").put("clientVersion", ver).put("hl", "tr").put("gl", "TR")
        )
        val out = LinkedHashMap<String, CommentItem>()
        var token: String? = null
        var page = 0
        do {
            val payload = JSONObject().put("context", ctx)
            if (page == 0) {
                payload.put("videoId", videoId).put("racyCheckOk", true).put("contentCheckOk", true)
            } else {
                payload.put("continuation", token)
            }
            val builder = Request.Builder().url("$O/youtubei/v1/next?prettyPrint=false")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .header("User-Agent", UA)
                .header("X-Youtube-Client-Name", "1")
                .header("X-Youtube-Client-Version", ver)
                .header("Origin", O)
                .header("Referer", "$O/watch?v=$videoId")
            val cookie = Auth.cookies
            if (!cookie.isNullOrBlank()) {
                builder.header("Cookie", cookie)
                    .header("Authorization", Auth.authHeader())
                    .header("X-Goog-AuthUser", "0")
            }
            val json = client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) error("YouTube yorum isteği başarısız (${response.code})")
                JSONObject(response.body?.string().orEmpty())
            }
            collectComments(json, out)
            token = if (out.isEmpty()) firstCommentContinuation(json) else null
            page++
        } while (out.isEmpty() && !token.isNullOrBlank() && page < 3)
        out.values.toList()
    }

    /** İlk /next cevabı çoğu zaman yorumları değil, yorum panelinin continuation token'ını döndürür. */
    private fun firstCommentContinuation(root: JSONObject): String? {
        fun scan(node: Any?, inComments: Boolean): String? {
            when (node) {
                is JSONArray -> {
                    for (i in 0 until node.length()) scan(node.opt(i), inComments)?.let { return it }
                }
                is JSONObject -> {
                    val panel = node.optString("panelIdentifier")
                    val commentScope = inComments || panel.contains("comment", true) ||
                        node.has("commentSectionRenderer") || node.has("commentsHeaderRenderer")
                    node.optJSONObject("continuationEndpoint")?.optJSONObject("continuationCommand")
                        ?.optString("token")?.takeIf { commentScope && it.isNotBlank() }?.let { return it }
                    node.optJSONObject("continuationCommand")?.optString("token")
                        ?.takeIf { commentScope && it.isNotBlank() }?.let { return it }
                    node.optJSONObject("nextContinuationData")?.optString("continuation")
                        ?.takeIf { commentScope && it.isNotBlank() }?.let { return it }
                    node.optJSONObject("reloadContinuationData")?.optString("continuation")
                        ?.takeIf { commentScope && it.isNotBlank() }?.let { return it }
                    val keys = node.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val childScope = commentScope || key.contains("comment", true) ||
                            key == "continuationItemRenderer"
                        scan(node.opt(key), childScope)?.let { return it }
                    }
                }
            }
            return null
        }
        return scan(root, false)
    }

    private fun collectComments(node: Any?, out: MutableMap<String, CommentItem>) {
        when (node) {
            is JSONArray -> for (i in 0 until node.length()) collectComments(node.opt(i), out)
            is JSONObject -> {
                val legacy = node.optJSONObject("commentRenderer")
                if (legacy != null) {
                    val id = legacy.optString("commentId")
                    val author = txt(legacy.opt("authorText")).ifBlank { txt(legacy.opt("authorName")) }
                    val body = txt(legacy.opt("contentText")).ifBlank { txt(legacy.opt("content")) }
                    val likes = txt(legacy.opt("voteCount"))
                    val avatar = imageUrl(legacy.opt("authorThumbnail"))
                    if (id.isNotBlank() && body.isNotBlank()) {
                        out.putIfAbsent(id, CommentItem(id, author, body, likes, avatar))
                    }
                }

                val modern = node.optJSONObject("commentViewModel") ?: node.optJSONObject("commentEntityPayload")
                if (modern != null) {
                    val props = modern.optJSONObject("properties") ?: modern
                    val authorNode = props.opt("authorText") ?: props.opt("authorName") ?: props.opt("author")
                    val authorObject = authorNode as? JSONObject
                    val author = txt(authorNode).ifBlank {
                        txt(authorObject?.opt("displayName")).ifBlank { txt(authorObject?.opt("name")) }
                    }
                    val body = txt(props.opt("contentText"))
                        .ifBlank { txt(props.opt("commentText")) }
                        .ifBlank { txt(props.opt("content")) }
                        .ifBlank { txt(props.opt("contentRuns")) }
                    val toolbar = props.optJSONObject("toolbar")
                    val id = modern.optString("commentId")
                        .ifBlank { modern.optString("commentKey") }
                        .ifBlank { modern.optString("key") }
                        .ifBlank { node.optString("entityKey") }
                    val likes = txt(props.opt("voteCount"))
                        .ifBlank { txt(props.opt("likeCount")) }
                        .ifBlank { txt(toolbar?.opt("likeCountA11y")) }
                    val avatar = imageUrl(props.opt("avatar"))
                        .ifBlank { imageUrl(props.opt("authorThumbnail")) }
                        .ifBlank { imageUrl(authorObject?.opt("avatar")) }
                    if (id.isNotBlank() && body.isNotBlank()) {
                        out.putIfAbsent(id, CommentItem(id, author, body, likes, avatar))
                    }
                }

                val keys = node.keys()
                while (keys.hasNext()) collectComments(node.opt(keys.next()), out)
            }
        }
    }

    private fun imageUrl(node: Any?): String {
        if (node !is JSONObject) return ""
        node.optJSONArray("thumbnails")?.optJSONObject(0)?.optString("url")?.takeIf { !it.isNullOrBlank() }?.let { return it }
        val image = node.optJSONObject("image")
        image?.optJSONArray("sources")?.optJSONObject(0)?.optString("url")?.takeIf { !it.isNullOrBlank() }?.let { return it }
        node.optJSONArray("sources")?.optJSONObject(0)?.optString("url")?.takeIf { !it.isNullOrBlank() }?.let { return it }
        return node.optString("url")
    }
    private val SKIP = setOf("adSlotRenderer", "adPlacementRenderer", "promotedSparklesWebRenderer", "displayAdRenderer")

    private fun collect(node: Any?, vids: MutableMap<String, Video>, pls: MutableMap<String, PlaylistItem>?) {
        when (node) {
            is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i), vids, pls)
            is JSONObject -> {
                val id = node.optString("videoId")
                if (id.length == 11 && node.has("title")) {
                    val t = txt(node.opt("title"))
                    if (t.isNotBlank() && id !in vids) {
                        val channel = txt(node.opt("shortBylineText")).ifBlank { txt(node.opt("longBylineText")) }
                            .ifBlank { txt(node.opt("ownerText")) }
                        vids[id] = Video(
                            id, t, channel,
                            txt(node.opt("lengthText")),
                            txt(node.opt("shortViewCountText")).ifBlank { txt(node.opt("viewCountText")) },
                            txt(node.opt("publishedTimeText")),
                        )
                    }
                }
                val ctype = node.optString("contentType")
                val cid = node.optString("contentId")
                if (ctype == "LOCKUP_CONTENT_TYPE_VIDEO" && cid.length == 11 && cid !in vids) {
                    val meta = node.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
                    val title = meta?.optJSONObject("title")?.optString("content").orEmpty()
                    if (title.isNotBlank()) {
                        val parts = ArrayList<String>()
                        gatherText(meta?.optJSONObject("metadata"), parts)
                        val dur = findFirst(node.optJSONObject("contentImage"), "thumbnailBadgeViewModel")?.optString("text").orEmpty()
                        vids[cid] = Video(cid, title, parts.getOrElse(0) { "" }, dur, parts.getOrElse(1) { "" }, parts.getOrElse(2) { "" })
                    }
                }
                if (pls != null) {
                    if (ctype == "LOCKUP_CONTENT_TYPE_PLAYLIST" && cid.isNotBlank()) {
                        val title = node.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
                            ?.optJSONObject("title")?.optString("content").orEmpty()
                        if (title.isNotBlank()) pls.putIfAbsent(cid, PlaylistItem(cid, title))
                    }
                    val pid = node.optString("playlistId")
                    if (pid.isNotBlank() && !node.has("videoId") && node.has("title") &&
                        (node.has("thumbnail") || node.has("thumbnailRenderer") || node.has("videoCountText"))
                    ) {
                        val t = txt(node.opt("title"))
                        if (t.isNotBlank()) pls.putIfAbsent(pid, PlaylistItem(pid, t))
                    }
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (k in SKIP) continue
                    collect(node.opt(k), vids, pls)
                }
            }
        }
    }

    private fun gatherText(node: Any?, out: MutableList<String>) {
        when (node) {
            is JSONArray -> for (i in 0 until node.length()) gatherText(node.opt(i), out)
            is JSONObject -> {
                node.optJSONObject("text")?.optString("content")?.takeIf { it.isNotBlank() }?.let { out.add(it) }
                val keys = node.keys()
                while (keys.hasNext()) { val k = keys.next(); if (k != "text") gatherText(node.opt(k), out) }
            }
        }
    }

    private fun findFirst(node: Any?, key: String): JSONObject? {
        when (node) {
            is JSONArray -> for (i in 0 until node.length()) findFirst(node.opt(i), key)?.let { return it }
            is JSONObject -> {
                node.optJSONObject(key)?.let { return it }
                val keys = node.keys()
                while (keys.hasNext()) findFirst(node.opt(keys.next()), key)?.let { return it }
            }
        }
        return null
    }

    private fun txt(o: Any?): String = when (o) {
        is String -> o
        is JSONObject -> {
            val simple = o.optString("simpleText")
            if (simple.isNotBlank()) simple
            else {
                val runs = o.optJSONArray("runs") ?: o.optJSONArray("contentRuns")
                val fromRuns = runs?.let { a ->
                    (0 until a.length()).joinToString("") { index ->
                        val item = a.optJSONObject(index)
                        txt(item?.opt("text") ?: item?.opt("content"))
                    }
                }.orEmpty()
                if (fromRuns.isNotBlank()) fromRuns
                else {
                    val content = o.opt("content") ?: o.opt("text") ?: o.opt("contentText") ?: o.opt("displayText")
                    if (content != null && content !== o) txt(content) else ""
                }
            }
        }
        is JSONArray -> (0 until o.length()).joinToString("") { txt(o.opt(it)) }
        else -> ""
    }
}
