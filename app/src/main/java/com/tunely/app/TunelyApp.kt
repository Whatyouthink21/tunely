package com.tunely.app

import android.app.Application
import com.tunely.app.data.AppDatabase
import com.tunely.app.data.HttpDownloader
import com.tunely.app.data.MusicCatalog
import com.tunely.app.data.SettingsManager
import com.tunely.app.data.SourceRegistry
import com.tunely.app.data.UserAgents
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.concurrent.TimeUnit

class TunelyApp : Application() {

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val db: AppDatabase by lazy { AppDatabase.create(this) }
    val settings: SettingsManager by lazy { SettingsManager(this) }
    val registry: SourceRegistry by lazy { SourceRegistry(http) }
    val catalog: MusicCatalog by lazy {
        MusicCatalog(
            http = http,
            audius = registry.source("audius") as com.tunely.app.data.AudiusSource,
            deezer = registry.source("deezer") as com.tunely.app.data.DeezerSource,
            itunes = registry.source("itunes") as com.tunely.app.data.ITunesSource,
            radio = registry.source("radio") as com.tunely.app.data.RadioSource
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        NewPipe.init(HttpDownloader(http), Localization("en", "US"), ContentCountry("US"))
    }

    companion object {
        lateinit var instance: TunelyApp
            private set

        const val USER_AGENT = UserAgents.NEWPIPE
    }
}
