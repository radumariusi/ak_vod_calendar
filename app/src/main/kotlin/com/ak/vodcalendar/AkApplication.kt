package com.ak.vodcalendar

import android.app.Application
import android.content.Context
import com.ak.vodcalendar.core.calendar.EpisodeCalendarRepository
import com.ak.vodcalendar.core.calendar.MovieCalendarRepository
import com.ak.vodcalendar.core.launch.WikidataClient
import com.ak.vodcalendar.core.model.AppSettings
import com.ak.vodcalendar.core.omdb.OmdbClient
import com.ak.vodcalendar.core.settings.SettingsRepository
import com.ak.vodcalendar.core.tmdb.CachingTmdbApi
import com.ak.vodcalendar.core.tmdb.TmdbHttpApi
import com.ak.vodcalendar.data.DataStoreSettingsRepository
import com.ak.vodcalendar.update.UpdateManager
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class AkApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
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

    /** One cached TMDB client per key, so moving between days reuses answers. */
    private val tmdbApis = ConcurrentHashMap<String, CachingTmdbApi>()

    private fun tmdbApi(key: String): CachingTmdbApi =
        tmdbApis.getOrPut(key) { CachingTmdbApi(TmdbHttpApi(okHttp, key)) }

    val omdb = OmdbClient(okHttp)

    /** In-app updates from the GitHub "Latest release". */
    val updates = UpdateManager(context, okHttp)

    /** Netflix / Disney+ / Prime Video / Apple TV ids of a title, for "Deschide în …". */
    val streamingIds = WikidataClient(okHttp)

    val episodeCalendar = EpisodeCalendarRepository(apiFactory = ::tmdbApi, imdbRatings = omdb)

    val movieCalendar = MovieCalendarRepository(apiFactory = ::tmdbApi, imdbRatings = omdb)

    /** "Reîncarcă" drops cached TMDB answers. */
    fun clearCaches() {
        tmdbApis.values.forEach { it.clear() }
        streamingIds.clear()
    }

    /** Key typed in Settings wins over the one baked in at build time. */
    fun tmdbKey(settings: AppSettings): String = settings.tmdbApiKey.trim().ifBlank { BuildConfig.TMDB_API_KEY }

    fun omdbKey(settings: AppSettings): String = settings.omdbApiKey.trim().ifBlank { BuildConfig.OMDB_API_KEY }

    val hasBuiltInTmdbKey: Boolean get() = BuildConfig.TMDB_API_KEY.isNotBlank()
    val hasBuiltInOmdbKey: Boolean get() = BuildConfig.OMDB_API_KEY.isNotBlank()
}
