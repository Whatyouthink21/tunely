package com.tunely.app

import android.app.Application
import com.tunely.app.data.AppDatabase
import com.tunely.app.data.HttpDownloader
import com.tunely.app.data.SettingsManager
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class TunelyApp : Application() {
    val http: OkHttpClient by lazy { OkHttpClient.Builder().build() }
    val db: AppDatabase by lazy { AppDatabase.create(this) }
    val settings: SettingsManager by lazy { SettingsManager(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        NewPipe.init(HttpDownloader(http), Localization("en", "US"), ContentCountry("US"))
    }

    companion object {
        lateinit var instance: TunelyApp
            private set
    }
}
