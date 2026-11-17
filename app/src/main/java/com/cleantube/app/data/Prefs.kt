package com.cleantube.app.data

import android.content.Context
import android.content.SharedPreferences

/** Hassas olmayan ayarlar (yalnizca kalite tercihi). Oturum bilgisi BURADA tutulmaz. */
object Prefs {
    /** "Otomatik" = veri tasarrufu icin bu yukseklik. */
    const val AUTO_HEIGHT = 360
    val DEFAULT_QUALITIES = listOf(144, 240, 360, 480, 720, 1080)

    @Volatile private var sp: SharedPreferences? = null
    @Volatile private var mem = 0

    fun init(ctx: Context) { sp = try { ctx.getSharedPreferences("vyro_prefs", Context.MODE_PRIVATE) } catch (_: Throwable) { null } }

    /** 0 = Otomatik; aksi halde secilen en yuksek yukseklik (p). */
    var quality: Int
        get() = try { sp?.getInt("quality", 0) ?: mem } catch (_: Throwable) { mem }
        set(v) { mem = v; try { sp?.edit()?.putInt("quality", v)?.apply() } catch (_: Throwable) {} }

    /** Ses modu: video parçasını hiç istemez; yalnızca düşük bitrate ses akışını seçer. */
    var audioOnly: Boolean
        get() = try { sp?.getBoolean("audio_only", false) ?: false } catch (_: Throwable) { false }
        set(v) { try { sp?.edit()?.putBoolean("audio_only", v)?.apply() } catch (_: Throwable) {} }

    const val MAX_AUDIO_BITRATE = 96

    fun heightFor(q: Int): Int = if (q > 0) q else AUTO_HEIGHT
}
