package com.cleantube.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore("cleantube_local")

object LocalStore {
    private lateinit var appCtx: Context
    private val HISTORY = stringPreferencesKey("history_json")
    private val SEARCH_HISTORY = stringPreferencesKey("search_history_json")
    private val SAVED = stringSetPreferencesKey("saved_ids")
    private val LIKED = stringSetPreferencesKey("liked_ids")
    private val SUBS = stringSetPreferencesKey("sub_channel_ids")
    private val QUALITY = intPreferencesKey("default_quality")
    private val SPEED = stringPreferencesKey("playback_speed")
    private val PROGRESS = stringPreferencesKey("progress_json")
    private val WATCH_LATER = stringPreferencesKey("watch_later_json")
    private val DOWNLOADS = stringPreferencesKey("downloads_json")
    private val NOTIFICATIONS = stringPreferencesKey("notifications_json")

    fun init(ctx: Context) { appCtx = ctx.applicationContext }

    suspend fun getHistory(): List<Video> = appCtx.dataStore.data.map { p ->
        decodeVideos(p[HISTORY])
    }.first()

    suspend fun addHistory(v: Video) {
        appCtx.dataStore.edit { p ->
            val list = decodeVideos(p[HISTORY]).filter { it.id != v.id }.toMutableList()
            list.add(0, v)
            p[HISTORY] = encodeVideos(list.take(100))
        }
    }

    suspend fun clearHistory() { appCtx.dataStore.edit { it.remove(HISTORY); it.remove(PROGRESS) } }

    suspend fun removeHistoryItem(id: String) {
        appCtx.dataStore.edit { p ->
            val list = decodeVideos(p[HISTORY]).filter { it.id != id }
            p[HISTORY] = encodeVideos(list)
            val obj = runCatching { JSONObject(p[PROGRESS] ?: "{}") }.getOrDefault(JSONObject())
            obj.remove(id); p[PROGRESS] = obj.toString()
        }
    }

    suspend fun getWatchLater(): List<Video> = appCtx.dataStore.data.map { p -> decodeVideos(p[WATCH_LATER]) }.first()
    suspend fun toggleWatchLater(v: Video): Boolean {
        var added = false
        appCtx.dataStore.edit { p ->
            val list = decodeVideos(p[WATCH_LATER]).toMutableList()
            val idx = list.indexOfFirst { it.id == v.id }
            if (idx >= 0) { list.removeAt(idx); added = false } else { list.add(0, v); added = true }
            p[WATCH_LATER] = encodeVideos(list.take(200))
        }
        return added
    }
    suspend fun isWatchLater(id: String): Boolean = getWatchLater().any { it.id == id }
    suspend fun clearAllLocal() { appCtx.dataStore.edit { it.clear() } }

    suspend fun getDownloads(): List<DownloadItem> = appCtx.dataStore.data.map { p -> decodeDownloads(p[DOWNLOADS]) }.first()
    suspend fun addDownload(item: DownloadItem) {
        appCtx.dataStore.edit { p ->
            val list = decodeDownloads(p[DOWNLOADS]).filter { it.videoId != item.videoId || it.fileName != item.fileName }.toMutableList()
            list.add(0, item)
            p[DOWNLOADS] = encodeDownloads(list.take(200))
        }
    }
    suspend fun updateDownloadStatus(videoId: String, fileName: String, status: String) {
        appCtx.dataStore.edit { p ->
            val list = decodeDownloads(p[DOWNLOADS]).map { if (it.videoId == videoId && it.fileName == fileName) it.copy(status = status) else it }
            p[DOWNLOADS] = encodeDownloads(list)
        }
    }
    suspend fun removeDownload(videoId: String, fileName: String) {
        appCtx.dataStore.edit { p ->
            val list = decodeDownloads(p[DOWNLOADS]).filterNot { it.videoId == videoId && it.fileName == fileName }
            p[DOWNLOADS] = encodeDownloads(list)
        }
    }

    suspend fun getNotifications(): List<Pair<String, String>> = appCtx.dataStore.data.map { p ->
        val raw = p[NOTIFICATIONS] ?: return@map emptyList()
        runCatching { JSONArray(raw).let { a -> (0 until a.length()).map { val o = a.getJSONObject(it); o.optString("t") to o.optString("b") } } }.getOrDefault(emptyList())
    }.first()
    suspend fun addNotification(title: String, body: String) {
        appCtx.dataStore.edit { p ->
            val a = runCatching { JSONArray(p[NOTIFICATIONS] ?: "[]") }.getOrDefault(JSONArray())
            val n = JSONArray().put(JSONObject().put("t", title).put("b", body).put("ts", System.currentTimeMillis()))
            for (i in 0 until minOf(a.length(), 49)) n.put(a.getJSONObject(i))
            p[NOTIFICATIONS] = n.toString()
        }
    }
    suspend fun clearNotifications() { appCtx.dataStore.edit { it.remove(NOTIFICATIONS) } }

    private fun encodeDownloads(list: List<DownloadItem>): String {
        val a = JSONArray()
        list.forEach { d ->
            a.put(JSONObject().put("videoId", d.videoId).put("title", d.title).put("fileName", d.fileName)
                .put("quality", d.quality).put("downloadId", d.downloadId).put("createdAt", d.createdAt).put("status", d.status))
        }
        return a.toString()
    }
    private fun decodeDownloads(raw: String?): List<DownloadItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                DownloadItem(o.optString("videoId"), o.optString("title"), o.optString("fileName"), o.optString("quality"), o.optLong("downloadId", -1), o.optLong("createdAt"), o.optString("status", "queued"))
            }
        }.getOrDefault(emptyList())
    }

    suspend fun saveProgress(videoId: String, positionMs: Long, durationMs: Long) {
        if (videoId.isBlank() || positionMs < 0) return
        appCtx.dataStore.edit { p ->
            val obj = runCatching { JSONObject(p[PROGRESS] ?: "{}") }.getOrDefault(JSONObject())
            obj.put(videoId, JSONObject().put("p", positionMs).put("d", durationMs))
            // keep small
            val keys = obj.keys().asSequence().toList()
            if (keys.size > 200) keys.take(keys.size - 200).forEach { obj.remove(it) }
            p[PROGRESS] = obj.toString()
        }
    }

    suspend fun getProgress(videoId: String): Long {
        return appCtx.dataStore.data.map { p ->
            val obj = runCatching { JSONObject(p[PROGRESS] ?: "{}") }.getOrDefault(JSONObject())
            val e = obj.optJSONObject(videoId) ?: return@map 0L
            val pos = e.optLong("p", 0L); val dur = e.optLong("d", 0L)
            if (dur > 0 && pos > dur - 10_000) 0L else pos
        }.first()
    }

    suspend fun getSearchHistory(): List<String> = appCtx.dataStore.data.map { p ->
        val raw = p[SEARCH_HISTORY] ?: return@map emptyList()
        runCatching { JSONArray(raw).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrDefault(emptyList())
    }.first()

    suspend fun addSearch(q: String) {
        val t = q.trim(); if (t.isBlank()) return
        appCtx.dataStore.edit { p ->
            val list = getSearchHistorySync(p[SEARCH_HISTORY]).filterNot { it.equals(t, true) }.toMutableList()
            list.add(0, t)
            p[SEARCH_HISTORY] = JSONArray(list.take(20)).toString()
        }
    }

    private fun getSearchHistorySync(raw: String?): List<String> =
        if (raw == null) emptyList() else runCatching { JSONArray(raw).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrDefault(emptyList())

    suspend fun removeSearch(q: String) {
        appCtx.dataStore.edit { p ->
            val list = getSearchHistorySync(p[SEARCH_HISTORY]).filterNot { it.equals(q, true) }
            p[SEARCH_HISTORY] = JSONArray(list).toString()
        }
    }

    suspend fun isLiked(id: String): Boolean = appCtx.dataStore.data.map { id in (it[LIKED] ?: emptySet()) }.first()
    suspend fun toggleLike(id: String): Boolean {
        var liked = false
        appCtx.dataStore.edit { p ->
            val set = (p[LIKED] ?: emptySet()).toMutableSet()
            liked = if (id in set) { set.remove(id); false } else { set.add(id); true }
            p[LIKED] = set
        }
        return liked
    }

    suspend fun isSubscribed(channelId: String): Boolean = appCtx.dataStore.data.map { channelId in (it[SUBS] ?: emptySet()) }.first()
    suspend fun toggleSub(channelId: String): Boolean {
        var sub = false
        appCtx.dataStore.edit { p ->
            val set = (p[SUBS] ?: emptySet()).toMutableSet()
            sub = if (channelId in set) { set.remove(channelId); false } else { set.add(channelId); true }
            p[SUBS] = set
        }
        return sub
    }

    suspend fun getSubs(): Set<String> = appCtx.dataStore.data.map { it[SUBS] ?: emptySet() }.first()
    suspend fun getDefaultQuality(): Int = appCtx.dataStore.data.map { it[QUALITY] ?: 720 }.first()
    suspend fun setDefaultQuality(q: Int) { appCtx.dataStore.edit { it[QUALITY] = q } }
    suspend fun getSpeed(): Float = appCtx.dataStore.data.map { it[SPEED]?.toFloatOrNull() ?: 1f }.first()
    suspend fun setSpeed(s: Float) { appCtx.dataStore.edit { it[SPEED] = s.toString() } }

    private fun encodeVideos(list: List<Video>): String {
        val a = JSONArray()
        list.forEach { v ->
            a.put(JSONObject().put("id", v.id).put("title", v.title).put("channel", v.channel)
                .put("channelId", v.channelId).put("channelAvatar", v.channelAvatar)
                .put("durationText", v.durationText).put("views", v.views).put("published", v.published))
        }
        return a.toString()
    }

    private fun decodeVideos(raw: String?): List<Video> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Video(
                    id = o.optString("id"), title = o.optString("title"), channel = o.optString("channel"),
                    channelId = o.optString("channelId"), channelAvatar = o.optString("channelAvatar"),
                    durationText = o.optString("durationText"), views = o.optString("views"), published = o.optString("published"),
                )
            }
        }.getOrDefault(emptyList())
    }
}
