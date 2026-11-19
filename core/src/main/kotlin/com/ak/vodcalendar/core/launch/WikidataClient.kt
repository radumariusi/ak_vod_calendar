package com.ak.vodcalendar.core.launch

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/** The ids a title has on each streaming service. */
interface StreamingIdsSource {
    /** Keyed by StreamingApp id; empty when nothing is known or the lookup failed. */
    suspend fun ids(wikidataId: String, type: ContentType): Map<String, ServiceTitleId>
}

/**
 * Reads streaming-service ids from Wikidata (TMDB gives the Wikidata item in `external_ids`).
 * Wikidata is free, needs no key and is edited by the same people who keep the Netflix /
 * Disney+ templates on Wikipedia up to date. Coverage is good for popular titles, patchy
 * for the rest. Answers (misses included) are cached for [ttlMillis].
 */
class WikidataClient(
    private val http: OkHttpClient,
    baseUrl: String = DEFAULT_BASE_URL,
    private val ttlMillis: Long = 7 * 24 * 60 * 60 * 1000L,
    private val now: () -> Long = System::currentTimeMillis,
) : StreamingIdsSource {

    private val base: HttpUrl = baseUrl.toHttpUrl()
    private val cache = ConcurrentHashMap<String, Pair<Long, Map<String, ServiceTitleId>>>()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun ids(wikidataId: String, type: ContentType): Map<String, ServiceTitleId> {
        val q = wikidataId.trim().uppercase()
        if (!Regex("Q\\d+").matches(q)) return emptyMap()
        val cacheKey = "$q-$type"
        cache[cacheKey]?.let { (at, value) -> if (now() - at < ttlMillis) return value }

        val url = base.newBuilder().addPathSegments("wiki/Special:EntityData/$q.json").build()
        val body = try {
            withContext(Dispatchers.IO) {
                http.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build()).execute().use { r ->
                    if (r.isSuccessful) r.body?.string() else null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return emptyMap() // not cached: try again next time

        val result = runCatching { parse(body, q, type) }.getOrDefault(emptyMap())
        cache[cacheKey] = now() to result
        return result
    }

    internal fun parse(body: String, q: String, type: ContentType): Map<String, ServiceTitleId> {
        val entities = json.parseToJsonElement(body).jsonObject["entities"]?.jsonObject ?: return emptyMap()
        // Wikidata may answer with the item a merged id redirects to.
        val entity = (entities[q] ?: entities.values.firstOrNull())?.jsonObject ?: return emptyMap()
        val claims = entity["claims"]?.jsonObject ?: return emptyMap()
        val out = linkedMapOf<String, ServiceTitleId>()
        for (p in PROPERTIES) {
            if (p.type != null && p.type != type) continue
            if (p.appId in out) continue
            val value = firstValue(claims, p.property) ?: continue
            out[p.appId] = ServiceTitleId(value, p.type ?: type)
        }
        return out
    }

    private fun firstValue(claims: JsonObject, property: String): String? {
        val statements = claims[property]?.jsonArray ?: return null
        // Prefer a "preferred" statement, skip deprecated ones.
        val ordered = statements.map { it.jsonObject }
            .filter { it["rank"]?.jsonPrimitive?.contentOrNull != "deprecated" }
            .sortedByDescending { it["rank"]?.jsonPrimitive?.contentOrNull == "preferred" }
        return ordered.firstNotNullOfOrNull { st ->
            st["mainsnak"]?.jsonObject?.get("datavalue")?.jsonObject?.get("value")
                ?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
                ?.trim()?.takeIf { it.isNotBlank() }
        }
    }

    fun clear() = cache.clear()

    /** A Wikidata property holding a service id; [type] null means it's used for movies and series. */
    internal data class Property(val property: String, val appId: String, val type: ContentType?)

    companion object {
        const val DEFAULT_BASE_URL = "https://www.wikidata.org/"
        private const val USER_AGENT = "AkVodCalendar/0.4 (Android TV calendar app)"

        internal val PROPERTIES = listOf(
            Property("P1874", "netflix", null), // Netflix ID
            Property("P7595", "disney", ContentType.MOVIE), // Disney+ movie ID
            Property("P7596", "disney", ContentType.SERIES), // Disney+ series ID
            Property("P8055", "prime", null), // Amazon Prime Video ID
            Property("P9586", "appletv", ContentType.MOVIE), // Apple TV movie ID
            Property("P9751", "appletv", ContentType.SERIES), // Apple TV show ID
            Property("P6466", "hulu", ContentType.MOVIE), // Hulu movie ID
            Property("P6465", "hulu", ContentType.SERIES), // Hulu series ID
        )
    }
}
