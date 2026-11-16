package com.cleantube.app.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.cleantube.app.data.Auth

private val ALLOWED = listOf("google.com", "youtube.com", "gstatic.com", "googleusercontent.com", "googleapis.com")
private const val MOBILE_UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

/**
 * Sadece Google oturumunu almak icin kullanilir. JS koprusu YOK, dosya erisimi KAPALI,
 * yalnizca Google/YouTube alan adlarina gidilebilir. Cerezler alindiktan sonra WebView'dan silinir.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(onDone: () -> Unit, onCancel: () -> Unit) {
    BackHandler { onCancel() }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TextButton(onClick = onCancel, Modifier.fillMaxWidth()) { Text("Vazgeç") }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                WebView(c).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.userAgentString = MOBILE_UA
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    val cm = CookieManager.getInstance()
                    cm.setAcceptCookie(true); cm.setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                            val u = r.url
                            val ok = u.scheme == "https" && ALLOWED.any { (u.host ?: "").let { h -> h == it || h.endsWith(".$it") } }
                            return !ok
                        }
                        override fun onPageFinished(v: WebView, url: String) {
                            val host = android.net.Uri.parse(url).host ?: return
                            if (host.endsWith("youtube.com")) {
                                val ck = cm.getCookie("https://www.youtube.com")
                                if (ck != null && (ck.contains("SAPISID=") || ck.contains("__Secure-3PAPISID="))) {
                                    Auth.save(ck)
                                    cm.removeAllCookies(null); cm.flush()
                                    v.clearHistory(); v.clearCache(true)
                                    onDone()
                                }
                            }
                        }
                        override fun onPageStarted(v: WebView, url: String, f: Bitmap?) {}
                    }
                    loadUrl("https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fwww.youtube.com%2F")
                }
            },
            onRelease = { it.destroy() },
        )
    }
}
