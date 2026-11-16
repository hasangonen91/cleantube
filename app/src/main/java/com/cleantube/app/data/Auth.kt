package com.cleantube.app.data

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

/**
 * Oturum cerezleri YALNIZCA burada, Android Keystore ile sifreli tutulur.
 * Disariya sadece youtube.com'a istek atarken (InnerTube) gonderilir. Loglanmaz.
 */
object Auth {
    private lateinit var prefs: SharedPreferences

    fun init(ctx: Context) {
        val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        prefs = EncryptedSharedPreferences.create(
            ctx, "secure_session", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    val cookies: String? get() = prefs.getString("c", null)
    val loggedIn: Boolean get() = sapisid() != null

    fun save(cookieHeader: String) { prefs.edit().putString("c", cookieHeader).apply() }

    fun logout() {
        prefs.edit().clear().apply()
        val cm = CookieManager.getInstance()
        cm.removeAllCookies(null); cm.flush()
        WebStorage.getInstance().deleteAllData()
    }

    private fun sapisid(): String? {
        val c = cookies ?: return null
        return Regex("(?:^|;\\s*)SAPISID=([^;]+)").find(c)?.groupValues?.get(1)
            ?: Regex("(?:^|;\\s*)__Secure-3PAPISID=([^;]+)").find(c)?.groupValues?.get(1)
    }

    fun authHeader(origin: String = "https://www.youtube.com"): String {
        val sid = sapisid() ?: error("Oturum yok")
        val ts = System.currentTimeMillis() / 1000
        val hash = MessageDigest.getInstance("SHA-1").digest("$ts $sid $origin".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${ts}_$hash"
    }
}
