package com.ak.vodcalendar.core.calendar

import com.ak.vodcalendar.core.filter.ContentClassifier
import com.ak.vodcalendar.core.model.AppSettings
import com.ak.vodcalendar.core.model.ContentFilter
import com.ak.vodcalendar.core.model.EpisodeEntry
import com.ak.vodcalendar.core.model.ProviderAvailability
import com.ak.vodcalendar.core.model.Region
import com.ak.vodcalendar.core.model.StreamingApp
import com.ak.vodcalendar.core.time.ReleaseTimeResolver
import com.ak.vodcalendar.core.tmdb.EpisodeDto
import com.ak.vodcalendar.core.tmdb.TmdbApi
import com.ak.vodcalendar.core.tmdb.TmdbException
import com.ak.vodcalendar.core.tmdb.TmdbImages
import com.ak.vodcalendar.core.tmdb.TvDetailsDto
import com.ak.vodcalendar.core.tmdb.TvSummaryDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException

/**
 * Builds the series calendar from TMDB.
 *
 * 1. `discover/tv` with `air_date` filters finds shows with an episode in the window.
 * 2. Hidden categories (anime, Turkish, …) are dropped before any extra request.
 * 3. For each show: details + watch providers, then the seasons of the last/next
 *    episode to get every episode in the window.
 * 4. Each episode gets the user's matching app (and its RO/US region) and a release
 *    moment in Romania time.
 *
 * Quality thresholds and anticipated lists are applied in stage 2.
 */
class EpisodeCalendarRepository(
    private val apiFactory: (apiKey: String) -> TmdbApi,
    private val resolver: ReleaseTimeResolver = ReleaseTimeResolver(),
    private val maxDiscoverPages: Int = 5,
    private val concurrency: Int = 8,
) {

    suspend fun episodesBetween(
        from: LocalDate,
        to: LocalDate,
        settings: AppSettings,
        apiKey: String,
    ): List<EpisodeEntry> = coroutineScope {
        require(!to.isBefore(from)) { "to < from" }
        val api = apiFactory(apiKey)
        // Widen by a day: a US evening airing lands on the next day in Romania,
        // a Pacific midnight drop can land on the previous evening for other zones.
        val windowFrom = from.minusDays(1)
        val windowTo = to.plusDays(1)

        val shows = discoverShows(api, windowFrom, windowTo)
            .filter { show ->
                val categories = ContentClassifier.classify(show.originalLanguage, show.originCountry, show.genreIds)
                categories.none { it in settings.hiddenCategories }
            }

        val semaphore = Semaphore(concurrency)
        val results = shows.map { show ->
            async {
                semaphore.withPermit {
                    try {
                        Result.success(episodesForShow(api, show.id, windowFrom, windowTo, settings))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }
            }
        }.awaitAll()

        if (results.isNotEmpty() && results.all { it.isFailure }) {
            throw results.first().exceptionOrNull() ?: TmdbException("TMDB: eroare necunoscută")
        }

        results.flatMap { it.getOrDefault(emptyList()) }
            .filter { !it.releaseAt.toLocalDate().isBefore(from) && !it.releaseAt.toLocalDate().isAfter(to) }
            .filter { entry -> entry.categories.none { it in settings.hiddenCategories } }
            .distinctBy { Triple(it.showId, it.seasonNumber, it.episodeNumber) }
            .sortedWith(compareBy({ it.releaseAt }, { it.showName }, { it.seasonNumber }, { it.episodeNumber }))
    }

    private suspend fun discoverShows(api: TmdbApi, from: LocalDate, to: LocalDate): List<TvSummaryDto> {
        val first = api.discoverTvAiring(from, to, page = 1)
        val lastPage = minOf(first.totalPages, maxDiscoverPages)
        val rest = (2..lastPage).flatMap { page -> api.discoverTvAiring(from, to, page).results }
        return (first.results + rest).distinctBy { it.id }
    }

    private suspend fun episodesForShow(
        api: TmdbApi,
        showId: Int,
        from: LocalDate,
        to: LocalDate,
        settings: AppSettings,
    ): List<EpisodeEntry> {
        val details = api.tvDetails(showId)
        val anchors = listOfNotNull(details.lastEpisodeToAir, details.nextEpisodeToAir)
        val seasonNumbers = anchors.map { it.seasonNumber }.filter { it > 0 }.distinct()

        val episodes = seasonNumbers.flatMap { season ->
            try {
                api.season(showId, season).episodes
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                anchors.filter { it.seasonNumber == season }
            }
        }.ifEmpty { anchors }

        val categories = ContentClassifier.classify(
            details.originalLanguage,
            details.originCountry,
            details.genres.map { it.id },
            details.type,
        )
        val (app, region) = matchInstalledApp(details, settings)
        val availability = availability(details)
        val networkIds = details.networks.map { it.id }.toSet()

        return episodes
            .mapNotNull { episode -> episode.parsedAirDate()?.let { episode to it } }
            .filter { (_, date) -> !date.isBefore(from) && !date.isAfter(to) }
            .map { (episode, date) ->
                val release = resolver.resolve(date, details.originCountry, networkIds, region)
                toEntry(details, episode, release.at, release.approximate, app, region, availability, categories)
            }
    }

    private fun toEntry(
        details: TvDetailsDto,
        episode: EpisodeDto,
        at: ZonedDateTime,
        approximate: Boolean,
        app: StreamingApp?,
        region: Region?,
        availability: List<ProviderAvailability>,
        categories: Set<ContentFilter>,
    ) = EpisodeEntry(
        showId = details.id,
        showName = details.name,
        posterUrl = TmdbImages.poster(details.posterPath),
        stillUrl = TmdbImages.still(episode.stillPath),
        seasonNumber = episode.seasonNumber,
        episodeNumber = episode.episodeNumber,
        episodeTitle = episode.name,
        overview = episode.overview,
        releaseAt = at,
        timeIsApproximate = approximate,
        watchOn = app,
        watchRegion = region,
        availability = availability,
        tmdbRating = details.voteAverage,
        tmdbVotes = details.voteCount,
        imdbId = details.externalIds?.imdbId,
        categories = categories,
        isSeasonPremiere = episode.episodeNumber == 1,
        isNewShow = episode.seasonNumber == 1,
    )

    companion object {
        /**
         * First installed app (in the user's order) that carries the show in the
         * region the user set for that app. Falls back to the show's original
         * network when TMDB has no provider data yet (common for brand-new shows).
         */
        fun matchInstalledApp(details: TvDetailsDto, settings: AppSettings): Pair<StreamingApp?, Region?> {
            val providers = details.watchProviders?.results.orEmpty()
            val installed = settings.installedApps()
            installed.firstOrNull { (app, region) ->
                providers[region.code]?.streaming.orEmpty().any { it.providerId in app.tmdbProviderIds }
            }?.let { return it.first to it.second }

            val networkIds = details.networks.map { it.id }.toSet()
            installed.firstOrNull { (app, _) -> app.tmdbNetworkIds.any { it in networkIds } }
                ?.let { return it.first to it.second }
            return null to null
        }

        fun availability(details: TvDetailsDto): List<ProviderAvailability> {
            val providers = details.watchProviders?.results.orEmpty()
            return Region.entries.flatMap { region ->
                providers[region.code]?.streaming.orEmpty()
                    .distinctBy { it.providerId }
                    .sortedBy { it.displayPriority }
                    .map { ProviderAvailability(it.providerId, it.providerName, region, TmdbImages.logo(it.logoPath)) }
            }
        }

        private fun EpisodeDto.parsedAirDate(): LocalDate? = airDate?.takeIf { it.isNotBlank() }?.let {
            try {
                LocalDate.parse(it)
            } catch (e: DateTimeParseException) {
                null
            }
        }
    }
}
