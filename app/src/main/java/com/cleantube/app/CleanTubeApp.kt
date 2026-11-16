package com.cleantube.app

import android.app.Application
import com.cleantube.app.data.Auth
import com.cleantube.app.data.HttpDownloader
import com.cleantube.app.data.LocalStore
import com.google.android.gms.cast.framework.CastContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class CleanTubeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Auth.init(this)
        LocalStore.init(this)
        runCatching { CastContext.getSharedInstance(this) }
        NewPipe.init(HttpDownloader(), Localization("tr", "TR"), ContentCountry("TR"))
    }
}
