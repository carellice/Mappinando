package it.mappinando.app.map

import it.mappinando.app.data.Place
import it.mappinando.app.data.PlaceCategory

data class SearchCenter(val latitude: Double, val longitude: Double) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
    }
}

enum class SearchKind(val label: String, val tags: List<String>) {
    ALL("Tutto", emptyList()),
    RESTAURANTS("Ristoranti", listOf("amenity:restaurant", "amenity:fast_food", "amenity:food_court")),
    CAFES("Bar e caffè", listOf("amenity:cafe", "amenity:bar", "amenity:pub", "amenity:biergarten")),
    SHOPS("Negozi", listOf("shop")),
    CULTURE("Da visitare", listOf("tourism:museum", "tourism:attraction", "tourism:gallery", "historic"));

    val placeCategory: PlaceCategory? get() = when (this) {
        ALL -> null
        RESTAURANTS -> PlaceCategory.RESTAURANTS
        CAFES -> PlaceCategory.BARS
        SHOPS -> PlaceCategory.SHOPPING
        CULTURE -> PlaceCategory.CULTURE
    }
}

fun interface PlaceSearcher {
    suspend fun search(query: String, center: SearchCenter, kind: SearchKind): List<SearchResult>
}

data class SearchResult(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val city: String = "",
    val category: PlaceCategory? = null,
    val typeLabel: String = "",
    val savedPlaceId: String? = null,
    val suggestedName: String = "",
) {
    val requiresAddressCheck: Boolean get() = suggestedName.isNotBlank()
    fun savedPlace(places: List<Place>): Place? = places.firstOrNull {
        it.id == savedPlaceId || ((it.name.equals(name, ignoreCase = true) || (suggestedName.isNotBlank() && it.name.equals(suggestedName, ignoreCase = true))) &&
            kotlin.math.abs(it.latitude - latitude) < .00003 && kotlin.math.abs(it.longitude - longitude) < .00003)
    }

    companion object {
        fun fromPlace(place: Place) = SearchResult(
            place.name, place.address, place.latitude, place.longitude, place.city,
            place.category, place.category.label, place.id,
        )
    }
}

/** Generic category words are filters, rather than a requirement in the business's name. */
internal data class SearchTerms(val text: String, val kind: SearchKind) {
    companion object {
        fun parse(query: String, selectedKind: SearchKind): SearchTerms {
            val clean = query.trim().replace(Regex("\\s+"), " ")
            val prefix = Regex("^(ristorant[ei]|pizzeri[ae]|bar|caff[eè]|caf[eé]|negozi[oa]?)(?:\\s+|$)", RegexOption.IGNORE_CASE)
                .find(clean)
            val inferred = when (prefix?.groupValues?.get(1)?.lowercase(java.util.Locale.ITALY)) {
                "ristorante", "ristoranti", "pizzeria", "pizzerie" -> SearchKind.RESTAURANTS
                "bar", "caffè", "caffe", "café", "cafe" -> SearchKind.CAFES
                "negozio", "negozi" -> SearchKind.SHOPS
                else -> SearchKind.ALL
            }
            return SearchTerms(
                if (prefix != null) clean.substring(prefix.range.last + 1).trim() else clean,
                if (selectedKind == SearchKind.ALL) inferred else selectedKind,
            )
        }
    }
}
