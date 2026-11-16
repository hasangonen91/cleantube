package com.cleantube.app

import android.app.Application
import com.cleantube.app.data.Auth
import com.cleantube.app.data.HttpDownloader
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class CleanTubeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Auth.init(this)
        NewPipe.init(HttpDownloader(), Localization("tr", "TR"), ContentCountry("TR"))
    }
}
