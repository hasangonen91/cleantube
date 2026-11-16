package com.cleantube.app

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.cleantube.app.data.Video
import com.cleantube.app.player.PlaybackService
import com.cleantube.app.ui.AppRoot
import com.cleantube.app.ui.CleanTubeTheme
import com.cleantube.app.ui.MainVM

class MainActivity : ComponentActivity() {
    private val vm: MainVM by viewModels()
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        handleIntent(intent)
        setContent { CleanTubeTheme { AppRoot(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val playing = PlaybackService.sharedPlayer?.isPlaying == true && vm.playing != null
        if (playing && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
            } catch (_: Exception) {}
        }
    }

    private fun extractVideoId(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val r = Regex("(?:v=|youtu\\.be/|shorts/|/embed/)([A-Za-z0-9_-]{11})")
        return r.find(text)?.groupValues?.get(1)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            val vid = extractVideoId(text)
            if (vid != null && vid.length == 11) { vm.play(Video(id = vid, title = "Video açılıyor…")); return }
            val uri = runCatching { Uri.parse(text?.trim().orEmpty()) }.getOrNull() ?: return
            handleYouTubeUri(uri); return
        }
        val data: Uri = intent.data ?: return
        handleYouTubeUri(data)
    }

    private fun handleYouTubeUri(data: Uri) {
        val host = data.host.orEmpty()
        val path = data.path.orEmpty()
        val vid = when {
            host.contains("youtu.be") -> data.lastPathSegment
            path.startsWith("/shorts/") -> data.pathSegments.getOrNull(1)
            path.startsWith("/watch") -> data.getQueryParameter("v")
            else -> extractVideoId(data.toString())
        }
        if (vid != null && vid.length == 11) { vm.play(Video(id = vid, title = "Video açılıyor…")); return }
        if (path.startsWith("/channel/")) data.pathSegments.getOrNull(1)?.let { vm.openChannel(it) }
        else if (path.startsWith("/@")) vm.openChannel(path.removePrefix("/"))
        else if (path.startsWith("/c/") || path.startsWith("/user/")) vm.openChannel(data.toString())
    }
}
