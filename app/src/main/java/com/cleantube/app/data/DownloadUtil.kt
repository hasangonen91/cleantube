package com.cleantube.app.data

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor

data class DownloadProgress(val status: Int, val downloaded: Long, val total: Long) {
    val percent: Int get() = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else -1
    val isRunning: Boolean get() = status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_PAUSED
    val isSuccess: Boolean get() = status == DownloadManager.STATUS_SUCCESSFUL
    val isFailed: Boolean get() = status == DownloadManager.STATUS_FAILED
}

object DownloadUtil {
    fun query(context: Context, downloadId: Long): DownloadProgress? {
        if (downloadId <= 0) return null
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val q = DownloadManager.Query().setFilterById(downloadId)
        val c: Cursor = dm.query(q) ?: return null
        c.use {
            if (!it.moveToFirst()) return null
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            return DownloadProgress(status, downloaded, total)
        }
    }
}
