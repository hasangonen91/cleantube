package com.cleantube.app.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.cleantube.app.data.Auth

private val ALLOWED = listOf(
    "accounts.google.com", "google.com", "youtube.com", "gstatic.com",
    "googleusercontent.com", "googleapis.com", "recaptcha.net", "withgoogle.com"
)
private const val MOBILE_UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

/**
 * Sadece Google oturumunu almak icin kullanilir. JS koprusu YOK, dosya erisimi KAPALI,
 * yalnizca izinli HTTPS alan adlarina gidilebilir. Cerezler alindiktan sonra WebView temizlenir.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(onDone: () -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    DisposableEffect(Unit) {
        val w = (ctx as? Activity)?.window
        w?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { w?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    val cleanup = {
        runCatching {
            CookieManager.getInstance().apply { removeAllCookies(null); flush() }
            WebStorage.getInstance().deleteAllData()
        }
    }
    BackHandler { cleanup(); onCancel() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TextButton(onClick = { cleanup(); onCancel() }, Modifier.fillMaxWidth()) { Text("Vazgeç ve oturumu temizle") }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    WebView.setWebContentsDebuggingEnabled(false)
                    WebView(c).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.allowFileAccessFromFileURLs = false
                        settings.allowUniversalAccessFromFileURLs = false
                        settings.setGeolocationEnabled(false)
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        settings.setSupportMultipleWindows(false)
                        settings.cacheMode = WebSettings.LOAD_NO_CACHE
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.userAgentString = MOBILE_UA
                        isVerticalScrollBarEnabled = true
                        val cm = CookieManager.getInstance()
                        cm.setAcceptCookie(true); cm.setAcceptThirdPartyCookies(this, true)
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            private fun allowed(url: android.net.Uri): Boolean {
                                val host = (url.host ?: return false).lowercase()
                                return url.scheme == "https" && ALLOWED.any { host == it || host.endsWith(".$it") }
                            }
                            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean = !allowed(r.url)
                            override fun onPageFinished(v: WebView, url: String) {
                                val host = android.net.Uri.parse(url).host ?: return
                                if (host.endsWith("youtube.com")) {
                                    val ck = cm.getCookie("https://www.youtube.com")
                                    if (ck != null && (ck.contains("SAPISID=") || ck.contains("__Secure-3PAPISID="))) {
                                        Auth.save(ck)
                                        cm.removeAllCookies(null); cm.flush()
                                        v.clearHistory(); v.clearCache(true)
                                        v.loadUrl("about:blank")
                                        onDone()
                                    }
                                }
                            }
                            override fun onPageStarted(v: WebView, url: String, f: Bitmap?) {}
                        }
                        loadUrl("https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fwww.youtube.com%2F")
                    }
                },
                onRelease = { it.stopLoading(); it.destroy() },
            )
        }
    }
}
