package com.cleantube.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

    data class BrowsePage(val videos: List<Video>, val playlists: List<PlaylistItem> = emptyList(), val continuation: String? = null)

    private suspend fun browseRequest(payload: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val cookie = Auth.cookies ?: error("Giriş yapılmadı")
        val ver = clientVersion()
        val ctx = JSONObject().put(
            "client", JSONObject().put("clientName", "WEB").put("clientVersion", ver).put("hl", "tr").put("gl", "TR")
        )
        payload.put("context", ctx)
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("$O/youtubei/v1/browse?prettyPrint=false").post(body)
            .header("Cookie", cookie)
            .header("Authorization", Auth.authHeader())
            .header("X-Goog-AuthUser", "0")
            .header("X-Origin", O).header("Origin", O).header("Referer", "$O/")
            .header("User-Agent", UA)
            .header("X-Youtube-Client-Name", "1").header("X-Youtube-Client-Version", ver)
            .build()
        client.newCall(req).execute().use { r ->
            if (r.code == 401 || r.code == 403) error("Oturum süresi dolmuş, tekrar giriş yap")
            if (!r.isSuccessful) error("Sunucu hatası (${r.code})")
            JSONObject(r.body?.string().orEmpty())
        }
    }

    private suspend fun browse(id: String): JSONObject = browseRequest(JSONObject().put("browseId", id))

    suspend fun videos(browseId: String): List<Video> = videosPage(browseId).videos

    suspend fun videosPage(browseId: String, continuation: String? = null): BrowsePage {
        val root = if (continuation != null) browseRequest(JSONObject().put("continuation", continuation)) else browse(browseId)
        val vids = LinkedHashMap<String, Video>()
        val pls = LinkedHashMap<String, PlaylistItem>()
        collect(root, vids, pls)
        return BrowsePage(vids.values.toList(), pls.values.toList(), findContinuation(root))
    }

    private fun findContinuation(node: Any?): String? {
        when (node) {
            is JSONArray -> for (i in 0 until node.length()) findContinuation(node.opt(i))?.let { return it }
            is JSONObject -> {
                // common continuation locations
                node.optJSONObject("continuationCommand")?.optString("token")?.takeIf { it.isNotBlank() }?.let { return it }
                node.optJSONObject("continuationEndpoint")?.optJSONObject("continuationCommand")?.optString("token")?.takeIf { it.isNotBlank() }?.let { return it }
                node.optJSONObject("nextContinuationData")?.optString("continuation")?.takeIf { it.isNotBlank() }?.let { return it }
                node.optJSONObject("reloadContinuationData")?.optString("continuation")?.takeIf { it.isNotBlank() }?.let { return it }
                node.optString("continuation").takeIf { it.isNotBlank() && node.has("continuation") && node.opt("continuation") is String }?.let { return it }
                node.optJSONObject("continuationItemRenderer")?.let { findContinuation(it) }?.let { return it }
                val keys = node.keys()
                while (keys.hasNext()) findContinuation(node.opt(keys.next()))?.let { return it }
            }
        }
        return null
    }

    suspend fun playlists(): List<PlaylistItem> = playlistsPage().playlists

    suspend fun playlistsPage(continuation: String? = null): BrowsePage = videosPage("FEplaylist_aggregation", continuation)

    private suspend fun post(endpoint: String, payload: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val cookie = Auth.cookies ?: error("Giriş yapılmadı")
        val ver = clientVersion()
        val ctx = JSONObject().put("client", JSONObject().put("clientName", "WEB").put("clientVersion", ver).put("hl", "tr").put("gl", "TR"))
        payload.put("context", ctx)
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("$O/youtubei/v1/$endpoint?prettyPrint=false").post(body)
            .header("Cookie", cookie).header("Authorization", Auth.authHeader())
            .header("X-Goog-AuthUser", "0").header("X-Origin", O).header("Origin", O).header("Referer", "$O/")
            .header("User-Agent", UA).header("X-Youtube-Client-Name", "1").header("X-Youtube-Client-Version", ver)
            .build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("İşlem başarısız (${r.code})")
            JSONObject(r.body?.string().orEmpty().ifBlank { "{}" })
        }
    }

    suspend fun setLike(videoId: String, like: Boolean): Result<Unit> = runCatching {
        val ep = if (like) "like/like" else "like/removelike"
        post(ep, JSONObject().put("target", JSONObject().put("videoId", videoId)))
        Unit
    }

    suspend fun subscribe(channelId: String, subscribe: Boolean): Result<Unit> = runCatching {
        val ep = if (subscribe) "subscription/subscribe" else "subscription/unsubscribe"
        post(ep, JSONObject().put("channelIds", JSONArray().put(channelId)).put("params", ""))
        Unit
    }

    suspend fun addToPlaylist(videoId: String, playlistId: String): Result<Unit> = runCatching {
        val action = JSONObject().put("addedVideoId", videoId).put("action", "ACTION_ADD_VIDEO")
        post("browse/edit_playlist", JSONObject().put("playlistId", playlistId.removePrefix("VL")).put("actions", JSONArray().put(action)))
        Unit
    }

    suspend fun postComment(videoId: String, text: String): Result<Unit> = runCatching {
        // Best-effort: endpoint/params may require additional context tokens in some cases.
        val params = "Eg0SC" + android.util.Base64.encodeToString(videoId.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
        val payload = JSONObject()
            .put("commentText", text)
            .put("createCommentParams", params)
        post("comment/create_comment", payload)
        Unit
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
        is JSONObject -> o.optString("simpleText").ifBlank {
            o.optJSONArray("runs")?.let { a -> (0 until a.length()).joinToString("") { a.optJSONObject(it)?.optString("text").orEmpty() } }
                ?: o.optString("content")
        }
        else -> ""
    }
}
