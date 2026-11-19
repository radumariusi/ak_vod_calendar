package com.ak.vodcalendar.core.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PagedResponse<T>(
    val page: Int = 1,
    val results: List<T> = emptyList(),
    @SerialName("total_pages") val totalPages: Int = 1,
    @SerialName("total_results") val totalResults: Int = 0,
)

@Serializable
data class TvSummaryDto(
    val id: Int,
    val name: String = "",
    @SerialName("original_name") val originalName: String = "",
    @SerialName("original_language") val originalLanguage: String = "",
    @SerialName("origin_country") val originCountry: List<String> = emptyList(),
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList(),
    val popularity: Double = 0.0,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("vote_count") val voteCount: Int = 0,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    val overview: String? = null,
)

@Serializable
data class GenreDto(val id: Int, val name: String = "")

@Serializable
data class NetworkDto(
    val id: Int,
    val name: String = "",
    @SerialName("logo_path") val logoPath: String? = null,
    @SerialName("origin_country") val originCountry: String? = null,
)

@Serializable
data class EpisodeDto(
    val id: Int = 0,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("episode_number") val episodeNumber: Int = 0,
    @SerialName("season_number") val seasonNumber: Int = 0,
    @SerialName("still_path") val stillPath: String? = null,
    val runtime: Int? = null,
    @SerialName("episode_type") val episodeType: String? = null,
)

@Serializable
data class ExternalIdsDto(
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("tvdb_id") val tvdbId: Int? = null,
)

@Serializable
data class ProviderDto(
    @SerialName("provider_id") val providerId: Int,
    @SerialName("provider_name") val providerName: String = "",
    @SerialName("logo_path") val logoPath: String? = null,
    @SerialName("display_priority") val displayPriority: Int = 0,
)

@Serializable
data class RegionProvidersDto(
    val link: String? = null,
    val flatrate: List<ProviderDto> = emptyList(),
    val free: List<ProviderDto> = emptyList(),
    val ads: List<ProviderDto> = emptyList(),
    val rent: List<ProviderDto> = emptyList(),
    val buy: List<ProviderDto> = emptyList(),
) {
    /** Providers where the title is included in a subscription or free. */
    val streaming: List<ProviderDto> get() = flatrate + free + ads
}

@Serializable
data class WatchProvidersDto(
    val id: Int? = null,
    val results: Map<String, RegionProvidersDto> = emptyMap(),
)

@Serializable
data class TvDetailsDto(
    val id: Int,
    val name: String = "",
    @SerialName("original_language") val originalLanguage: String = "",
    @SerialName("origin_country") val originCountry: List<String> = emptyList(),
    val genres: List<GenreDto> = emptyList(),
    val networks: List<NetworkDto> = emptyList(),
    val type: String? = null,
    val status: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("vote_count") val voteCount: Int = 0,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val overview: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("number_of_seasons") val numberOfSeasons: Int = 0,
    @SerialName("last_episode_to_air") val lastEpisodeToAir: EpisodeDto? = null,
    @SerialName("next_episode_to_air") val nextEpisodeToAir: EpisodeDto? = null,
    @SerialName("episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    @SerialName("watch/providers") val watchProviders: WatchProvidersDto? = null,
    @SerialName("external_ids") val externalIds: ExternalIdsDto? = null,
)

@Serializable
data class SeasonDto(
    val id: Int = 0,
    @SerialName("season_number") val seasonNumber: Int = 0,
    val episodes: List<EpisodeDto> = emptyList(),
)

@Serializable
data class ProviderListDto(val results: List<ProviderDto> = emptyList())
