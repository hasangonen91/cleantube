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
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.cleantube.app.data.Auth

private val ALLOWED = listOf(
    "accounts.google.com", "google.com", "youtube.com", "gstatic.com",
    "googleusercontent.com", "googleapis.com", "recaptcha.net", "withgoogle.com"
)
private const val MOBILE_UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
private const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

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
    val cm = remember { CookieManager.getInstance() }
    val cleanup = {
        runCatching {
            cm.removeAllCookies(null); cm.flush()
            WebStorage.getInstance().deleteAllData()
        }
        Unit
    }
    // Google oturum cerezlerini topla; basariliysa kaydet ve bitir.
    val finish: () -> Boolean = {
        val ck = listOf(
            "https://www.youtube.com", "https://m.youtube.com",
            "https://accounts.google.com", "https://www.google.com",
        ).mapNotNull { runCatching { cm.getCookie(it) }.getOrNull() }
            .joinToString("; ")
        val ok = ck.contains("SAPISID=") || ck.contains("__Secure-3PAPISID=") || ck.contains("__Secure-1PAPISID=")
        android.util.Log.i("CleanTube", "login check: loggedIn=$ok cookieLen=${ck.length}")
        if (ok) {
            Auth.save(ck)
            cleanup()
            onDone()
        }
        ok
    }
    BackHandler { cleanup(); onCancel() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { cleanup(); onCancel() }) { Text("Vazgeç") }
                TextButton(onClick = {
                    if (!finish()) Toast.makeText(ctx, "Giriş tamamlanmadı. Google hesabına giriş yapıp tekrar dene.", Toast.LENGTH_SHORT).show()
                }) { Text("Girişi tamamla") }
            }
            Text(
                "Google bazen uygulama içi tarayıcıda girişi engelleyebilir. Giriş yaptıktan sonra \"Girişi tamamla\"ya bas.",
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.setSupportZoom(true)
                        isVerticalScrollBarEnabled = true
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
                                android.util.Log.i("CleanTube", "login page: $host")
                                if (finish()) { v.loadUrl("about:blank"); return }
                                // Google "guvenli olmayan tarayici" uyarisi verdiyse masaustu UA ile tekrar dene
                                v.evaluateJavascript("(document.body&&document.body.innerText||'').slice(0,3000)") { text ->
                                    val t = text ?: ""
                                    if (t.contains("may not be secure", true) || t.contains("couldn't sign you in", true)) {
                                        if (v.settings.userAgentString != DESKTOP_UA) {
                                            v.settings.userAgentString = DESKTOP_UA
                                            v.reload()
                                        }
                                    }
                                }
                            }
                            override fun onPageStarted(v: WebView, url: String, f: Bitmap?) {}
                        }
                        loadUrl("https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fm.youtube.com%2F")
                    }
                },
                onRelease = { it.stopLoading(); it.destroy() },
            )
        }
    }
}
