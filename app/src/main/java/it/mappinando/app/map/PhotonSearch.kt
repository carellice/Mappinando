package it.mappinando.app.map

import it.mappinando.app.data.PlaceCategory
import it.mappinando.app.data.PlaceCity
import it.mappinando.app.data.PlaceValidation
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Explicit submissions only; one request at a time and a small, expiring in-memory cache. */
internal class PhotonSearch : PlaceSearcher {
    private val mutex = Mutex()
    private data class Cached(val time: Long, val results: List<SearchResult>)
    private val cache = object : LinkedHashMap<String, Cached>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>) = size > 32
    }

    override suspend fun search(query: String, center: SearchCenter, kind: SearchKind): List<SearchResult> = searchTerms(SearchTerms.parse(query, kind), center)

    internal suspend fun searchTerms(terms: SearchTerms, center: SearchCenter): List<SearchResult> = mutex.withLock {
        val endpoint = buildUrl(terms, center)
        cache[endpoint]?.takeIf { System.currentTimeMillis() - it.time < if (it.results.isEmpty()) 60_000 else 600_000 }?.let { return@withLock it.results }
        val results = withContext(Dispatchers.IO) {
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 6_000
                connection.readTimeout = 8_000
                connection.setRequestProperty("User-Agent", "Mappinando/1.0 (Android; personal places map)")
                connection.setRequestProperty("Accept", "application/json")
                val status = connection.responseCode
                if (status == 429) throw IOException("Troppe ricerche ravvicinate. Attendi un momento e riprova.")
                if (status != 200) throw IOException("La ricerca di attività non risponde. Riprova tra poco.")
                val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                parseResults(response)
            } finally {
                connection.disconnect()
            }
        }
        cache[endpoint] = Cached(System.currentTimeMillis(), results)
        results
    }

    companion object {
        internal fun buildUrl(terms: SearchTerms, center: SearchCenter): String {
            val nearby = terms.text.isBlank() && terms.kind != SearchKind.ALL
            val parameters = mutableListOf(
                "lat" to String.format(Locale.ROOT, "%.4f", center.latitude),
                "lon" to String.format(Locale.ROOT, "%.4f", center.longitude),
                "limit" to "30",
            )
            if (nearby) parameters += "radius" to "15"
            else parameters += listOf("q" to terms.text, "zoom" to "12")
            terms.kind.tags.forEach { parameters += "osm_tag" to it }
            return "https://photon.komoot.io/${if (nearby) "reverse" else "api/"}?" + parameters.joinToString("&") {
                "${it.first}=${URLEncoder.encode(it.second, "UTF-8")}" }
        }

        internal fun parseResults(json: String): List<SearchResult> {
            val features = JSONObject(json).getJSONArray("features")
            return (0 until features.length()).mapNotNull { index ->
                val feature = features.optJSONObject(index) ?: return@mapNotNull null
                val geometry = feature.optJSONObject("geometry") ?: return@mapNotNull null
                if (geometry.optString("type") != "Point") return@mapNotNull null
                val coordinates = geometry.optJSONArray("coordinates") ?: return@mapNotNull null
                val longitude = coordinates.optDouble(0, Double.NaN)
                val latitude = coordinates.optDouble(1, Double.NaN)
                if (!latitude.isFinite() || latitude !in -90.0..90.0 || !longitude.isFinite() || longitude !in -180.0..180.0) return@mapNotNull null
                val properties = feature.optJSONObject("properties") ?: return@mapNotNull null
                fun field(key: String) = properties.optString(key).takeUnless { it == "null" }.orEmpty().trim()
                val street = listOf(field("street"), field("housenumber")).filter(String::isNotBlank).joinToString(" ")
                val city = PlaceCity.normalize(field("city").ifBlank { field("town").ifBlank { field("village").ifBlank { field("county") } } })
                    .take(PlaceValidation.MAX_CITY_LENGTH)
                val type = field("osm_value")
                val category = when {
                    type in listOf("restaurant", "fast_food", "food_court") -> PlaceCategory.RESTAURANTS
                    type in listOf("cafe", "bar", "pub", "biergarten") -> PlaceCategory.BARS
                    field("osm_key") == "shop" -> PlaceCategory.SHOPPING
                    field("osm_key") == "historic" || type in listOf("museum", "attraction", "gallery", "artwork") -> PlaceCategory.CULTURE
                    field("osm_key") == "natural" || type in listOf("park", "garden", "nature_reserve") -> PlaceCategory.NATURE
                    type == "viewpoint" -> PlaceCategory.VIEWS
                    else -> null
                }
                val name = field("name").ifBlank { street.ifBlank { city } }.take(PlaceValidation.MAX_NAME_LENGTH)
                if (name.isBlank()) return@mapNotNull null
                val label = when (type) {
                    "restaurant" -> "Ristorante"
                    "fast_food" -> "Ristorazione veloce"
                    "cafe" -> "Caffè"
                    "bar", "pub" -> "Bar"
                    "museum" -> "Museo"
                    "city", "town", "village" -> "Città"
                    "street" -> "Indirizzo"
                    else -> category?.label.orEmpty()
                }
                SearchResult(name, listOf(street, city, field("country")).filter(String::isNotBlank).distinct().joinToString(", ").take(PlaceValidation.MAX_ADDRESS_LENGTH),
                    latitude, longitude, city, category, label)
            }.distinctBy { Triple(it.name.lowercase(Locale.ITALY), it.latitude, it.longitude) }
        }
    }
}
