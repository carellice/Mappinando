package it.mappinando.app.map

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import it.mappinando.app.data.PlaceCity
import it.mappinando.app.data.PlaceValidation
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** POI/name search with a device-geocoder fallback for addresses. */
class PlaceSearch(context: Context) : PlaceSearcher {
    private val businesses = PhotonSearch()
    private val combined = FreePlaceSearch(
        primary = businesses,
        addresses = PlaceSearcher { query, _, _ -> searchAddresses(query) },
        alternate = PlaceSearcher { query, center, kind -> businesses.searchTerms(SearchTerms(query, kind), center) },
    )
    private val geocoder = Geocoder(context.applicationContext, Locale.ITALY)
    private val mutex = Mutex()
    private data class CachedAddresses(val created: Long, val results: List<SearchResult>)
    private val cache = object : LinkedHashMap<String, CachedAddresses>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedAddresses>): Boolean = size > 32
    }
    private val reverseCache = object : LinkedHashMap<Pair<Double, Double>, SearchResult?>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Double, Double>, SearchResult?>): Boolean = size > 32
    }

    override suspend fun search(query: String, center: SearchCenter, kind: SearchKind): List<SearchResult> {
        return combined.search(query, center, kind)
    }

    private suspend fun searchAddresses(query: String): List<SearchResult> = mutex.withLock {
        val cleanQuery = query.trim().replace(Regex("\\s+"), " ")
        require(cleanQuery.length >= 3) { "Scrivi almeno 3 caratteri per cercare un posto." }
        val key = cleanQuery.lowercase(Locale.ITALY)
        cache[key]?.takeIf { System.currentTimeMillis() - it.created < if (it.results.isEmpty()) 60_000 else 600_000 }?.let { return@withLock it.results }
        if (!Geocoder.isPresent()) {
            throw IOException("La ricerca indirizzi non è disponibile su questo dispositivo. Puoi aggiungere un posto tenendo premuta la mappa.")
        }
        val addresses = withTimeout(8_000) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine<List<Address>> { continuation ->
                    geocoder.getFromLocationName(cleanQuery, 8, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (continuation.isActive) continuation.resume(addresses.toList())
                        }
                        override fun onError(errorMessage: String?) {
                            if (continuation.isActive) continuation.resumeWithException(
                                IOException("Non riesco a cercare adesso. Controlla la connessione e riprova."),
                            )
                        }
                    })
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(cleanQuery, 8).orEmpty()
                }
            }
        }
        val results = addresses.filter { it.hasLatitude() && it.hasLongitude() }
            .map { address -> address.toSearchResult() }
            .distinctBy { Pair(it.latitude, it.longitude) }
        cache[key] = CachedAddresses(System.currentTimeMillis(), results)
        results
    }

    /** One optional lookup for a point explicitly chosen by the user, without location permission. */
    suspend fun reverse(latitude: Double, longitude: Double): SearchResult? = mutex.withLock {
        require(latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0) {
            "Le coordinate non sono valide."
        }
        val key = latitude to longitude
        if (reverseCache.containsKey(key)) return@withLock reverseCache[key]
        if (!Geocoder.isPresent()) return@withLock null
        val addresses = withTimeout(15_000) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine<List<Address>> { continuation ->
                    geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (continuation.isActive) continuation.resume(addresses.toList())
                        }

                        override fun onError(errorMessage: String?) {
                            if (continuation.isActive) continuation.resumeWithException(
                                IOException("La città non è disponibile adesso. Puoi scriverla nel campo Città."),
                            )
                        }
                    })
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(latitude, longitude, 1).orEmpty()
                }
            }
        }
        val result = addresses.firstOrNull { it.hasLatitude() && it.hasLongitude() }?.toSearchResult()
        reverseCache[key] = result
        result
    }

    private fun Address.toSearchResult(): SearchResult {
        val street = listOfNotNull(thoroughfare, subThoroughfare).joinToString(" ")
        val feature = featureName?.takeUnless { it.isBlank() || it.all(Char::isDigit) }
        val city = PlaceCity.normalize(locality?.takeIf { it.isNotBlank() } ?: subAdminArea.orEmpty())
            .take(PlaceValidation.MAX_CITY_LENGTH)
        return SearchResult(
            name = feature ?: street.ifBlank { locality.orEmpty() },
            address = (0..maxAddressLineIndex).mapNotNull(this::getAddressLine)
                .joinToString(", ").ifBlank { listOfNotNull(locality, countryName).joinToString(", ") },
            latitude = latitude,
            longitude = longitude,
            city = city,
            typeLabel = if (feature == locality || (feature == null && street.isBlank())) "Città" else "Indirizzo",
        )
    }
}
