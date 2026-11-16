package com.cleantube.app

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cleantube.app.data.LocalStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        if (id <= 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val c = dm.query(DownloadManager.Query().setFilterById(id))
                c?.use {
                    if (it.moveToFirst()) {
                        val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val uri = it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)) ?: ""
                        val title = it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)) ?: "Video"
                        val fileName = uri.substringAfterLast('/')
                        val ok = status == DownloadManager.STATUS_SUCCESSFUL
                        val downloads = LocalStore.getDownloads()
                        downloads.find { it.downloadId == id }?.let { d ->
                            LocalStore.updateDownloadStatus(d.videoId, d.fileName, if (ok) "completed" else "failed")
                        }
                        if (ok) LocalStore.addNotification("İndirme tamamlandı", title)
                    }
                }
            } finally { pending.finish() }
        }
    }
}
