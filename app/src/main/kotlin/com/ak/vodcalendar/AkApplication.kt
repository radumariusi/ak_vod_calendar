package com.ak.vodcalendar

import android.app.Application
import android.content.Context
import com.ak.vodcalendar.core.calendar.EpisodeCalendarRepository
import com.ak.vodcalendar.core.model.AppSettings
import com.ak.vodcalendar.core.settings.SettingsRepository
import com.ak.vodcalendar.core.tmdb.TmdbHttpApi
import com.ak.vodcalendar.data.DataStoreSettingsRepository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AkApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Manual dependency wiring. Small enough for now; can move to Hilt later. */
class AppContainer(context: Context) {
    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(context.applicationContext)

    val episodeCalendar = EpisodeCalendarRepository(apiFactory = { key -> TmdbHttpApi(okHttp, key) })

    /** Key typed in Settings wins over the one baked in at build time. */
    fun tmdbKey(settings: AppSettings): String = settings.tmdbApiKey.trim().ifBlank { BuildConfig.TMDB_API_KEY }

    val hasBuiltInTmdbKey: Boolean get() = BuildConfig.TMDB_API_KEY.isNotBlank()
}
