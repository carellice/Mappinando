package it.mappinando.app.map

import java.text.Normalizer
import java.util.Locale
import kotlin.math.*

/** Rank actual provider results; never synthesize a business from the user's query. */
internal object SearchRelevance {
    private val genericWords = setOf("a", "in", "di", "da", "del", "della", "dei", "delle", "il", "la", "le", "lo", "un", "una",
        "ristorante", "ristoranti", "pizzeria", "pizzerie", "bar", "caffe", "cafe", "negozio", "negozi")

    fun normalized(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private fun tokens(value: String) = normalized(value).split(' ').filter(String::isNotBlank).toSet()
    private fun queryTokens(query: String) = tokens(query).filterNot { it in genericWords }.toSet()

    fun isGoodMatch(query: String, result: SearchResult): Boolean {
        val wanted = queryTokens(query)
        if (wanted.isEmpty()) return true
        val available = tokens("${result.name} ${result.address} ${result.city}")
        return wanted.count { it in available }.toDouble() / wanted.size >= .8
    }

    private fun score(query: String, result: SearchResult): Double {
        val wanted = queryTokens(query)
        if (wanted.isEmpty()) return 0.0
        val name = tokens(result.name)
        val all = tokens("${result.name} ${result.address} ${result.city}")
        return wanted.count { it in all } * 120.0 / wanted.size +
            wanted.count { it in name } * 40.0 / wanted.size +
            (if (normalized(result.name) == normalized(query)) 30.0 else 0.0)
    }

    fun distanceMeters(first: SearchResult, second: SearchResult): Double = distanceMeters(first.latitude, first.longitude, second.latitude, second.longitude)

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val latitude = Math.toRadians(lat2 - lat1)
        val longitude = Math.toRadians(lon2 - lon1)
        val a = sin(latitude / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(longitude / 2).pow(2)
        return 6_371_000.0 * 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    fun mergeAndRank(query: String, center: SearchCenter, results: List<SearchResult>): List<SearchResult> {
        val ranked = results.filter { it.name.isNotBlank() && it.latitude.isFinite() && it.latitude in -90.0..90.0 && it.longitude.isFinite() && it.longitude in -180.0..180.0 }
            .sortedWith(compareByDescending<SearchResult> { score(query, it) }
                .thenBy { distanceMeters(center.latitude, center.longitude, it.latitude, it.longitude) })
        val unique = mutableListOf<SearchResult>()
        ranked.forEach { result ->
            val index = unique.indexOfFirst { normalized(it.name) == normalized(result.name) && distanceMeters(it, result) <= 40.0 }
            if (index == -1) unique += result else {
                val first = unique[index]
                unique[index] = first.copy(
                    address = first.address.ifBlank { result.address }, city = first.city.ifBlank { result.city },
                    category = first.category ?: result.category,
                    typeLabel = if (first.category == null && result.category != null) result.typeLabel else first.typeLabel.ifBlank { result.typeLabel },
                )
            }
        }
        return unique.take(40)
    }
}
