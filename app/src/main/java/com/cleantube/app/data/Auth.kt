package com.cleantube.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import java.security.MessageDigest

/**
 * Oturum cerezleri YALNIZCA burada, Android Keystore ile sifreli tutulur.
 * Disariya sadece youtube.com'a istek atarken (InnerTube) gonderilir. Loglanmaz.
 *
 * Keystore/EncryptedSharedPreferences bazi cihazlarda (ozellikle bozuk anahtar, OEM Keystore
 * hatalari) istisna firlatabilir. Bu durumda uygulama COKMEZ: bozuk dosya silinip bir kez
 * daha denenir, yine olmazsa oturum yalnizca bellekte (diske yazilmadan) tutulur.
 */
object Auth {
    private const val TAG = "VYRO.Auth"
    private const val FILE = "secure_session"

    @Volatile private var prefs: SharedPreferences? = null
    @Volatile private var memoryCookie: String? = null

    fun init(ctx: Context) {
        prefs = try {
            open(ctx)
        } catch (t: Throwable) {
            Log.w(TAG, "Sifreli depo acilamadi, sifirlanip tekrar denenecek", t)
            try {
                runCatching { ctx.deleteSharedPreferences(FILE) }
                runCatching {
                    val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                    ks.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                }
                open(ctx)
            } catch (t2: Throwable) {
                Log.w(TAG, "Sifreli depo kullanilamiyor; oturum sadece bellekte tutulacak", t2)
                null
            }
        }
    }

    private fun open(ctx: Context): SharedPreferences {
        val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            ctx, FILE, key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    val cookies: String?
        get() = try { prefs?.getString("c", null) ?: memoryCookie } catch (t: Throwable) { memoryCookie }

    val loggedIn: Boolean get() = sapisid() != null

    fun save(cookieHeader: String) {
        memoryCookie = cookieHeader
        try { prefs?.edit()?.putString("c", cookieHeader)?.apply() } catch (t: Throwable) { Log.w(TAG, "Oturum kaydedilemedi", t) }
    }

    fun logout() {
        memoryCookie = null
        try { prefs?.edit()?.clear()?.apply() } catch (_: Throwable) {}
        try {
            val cm = CookieManager.getInstance()
            cm.removeAllCookies(null); cm.flush()
            WebStorage.getInstance().deleteAllData()
        } catch (_: Throwable) {}
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
