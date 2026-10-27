package it.mappinando.app.data

import java.text.Collator
import java.util.Locale

/** A blank [city] represents places that still need a city, rather than a guessed location. */
data class CityGroup(val city: String, val places: List<Place>)

object PlaceCity {
    private val whitespace = Regex("[\\s\\p{Z}]+")
    private val legacyCityName = Regex("^[\\p{L}\\p{M}][\\p{L}\\p{M} .’'\\p{Pd}]+$")

    fun normalize(value: String): String = value.trim().replace(whitespace, " ")

    /** Only our legacy explicit `address · city` format supplies a fallback city. */
    fun legacyCity(address: String): String {
        if (!address.contains(" · ")) return ""
        val candidate = normalize(address.substringAfterLast(" · "))
        return candidate.takeIf {
            it.length in 2..PlaceValidation.MAX_CITY_LENGTH && legacyCityName.matches(it)
        }.orEmpty()
    }

    fun resolvedCity(place: Place): String = normalize(place.city).ifBlank { legacyCity(place.address) }

    fun groupingKey(place: Place): String = resolvedCity(place).lowercase(Locale.ITALIAN)

    /** City names ignore case when grouped; the original place order is preserved within each city. */
    fun group(places: List<Place>): List<CityGroup> {
        val collator = Collator.getInstance(Locale.ITALIAN).apply { strength = Collator.PRIMARY }
        return places.groupBy(::groupingKey).values.map { group ->
            CityGroup(resolvedCity(group.first()), group)
        }.sortedWith { first, second ->
            when {
                first.city.isBlank() && second.city.isNotBlank() -> 1
                first.city.isNotBlank() && second.city.isBlank() -> -1
                else -> collator.compare(first.city, second.city)
            }
        }
    }
}
