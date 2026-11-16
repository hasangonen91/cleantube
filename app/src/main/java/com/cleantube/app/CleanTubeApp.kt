package com.cleantube.app

import android.app.Application
import android.util.Log
import com.cleantube.app.data.Auth
import com.cleantube.app.data.HttpDownloader
import com.cleantube.app.data.Prefs
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class CleanTubeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Hicbir baslatma hatasi uygulamayi oldurmemeli; ekranlar hatayi kontrollu gosterir.
        Prefs.init(this)
        try { Auth.init(this) } catch (t: Throwable) { Log.e("VYRO", "Auth.init basarisiz", t) }
        try {
            NewPipe.init(HttpDownloader(), Localization("tr", "TR"), ContentCountry("TR"))
        } catch (t: Throwable) { Log.e("VYRO", "NewPipe.init basarisiz", t) }
    }
}
