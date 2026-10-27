package it.mappinando.app.map

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Two free sources are independent: partial results from one must not prevent the other. */
internal class FreePlaceSearch(
    private val primary: PlaceSearcher,
    private val addresses: PlaceSearcher,
    private val alternate: PlaceSearcher = primary,
) : PlaceSearcher {
    private data class Outcome(val results: List<SearchResult>, val succeeded: Boolean)
    private data class Cached(val created: Long, val results: List<SearchResult>)
    private val mutex = Mutex()
    private val cache = object : LinkedHashMap<String, Cached>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>) = size > 32
    }

    override suspend fun search(query: String, center: SearchCenter, kind: SearchKind): List<SearchResult> = mutex.withLock {
        val clean = query.trim().replace(Regex("\\s+"), " ")
        require(clean.length in 3..200) { "Scrivi almeno 3 caratteri per cercare un posto." }
        val key = "${clean.lowercase(java.util.Locale.ROOT)}|${center.latitude}|${center.longitude}|$kind"
        cache[key]?.takeIf { System.currentTimeMillis() - it.created < if (it.results.isEmpty()) 60_000 else 600_000 }?.let { return@withLock it.results }
        val terms = SearchTerms.parse(clean, kind)
        val outcomes = supervisorScope {
            val first = async { attempt { primary.search(clean, center, kind) } }
            // A category-only query is not an address lookup (e.g. "ristoranti").
            val second = if (terms.text.isNotBlank()) async { attempt { addresses.search(clean, center, SearchKind.ALL) } } else null
            listOfNotNull(first.await(), second?.await())
        }
        val candidates = (outcomes.first().results + outcomes.drop(1).flatMap { it.results }.map { result ->
            if (result.typeLabel == "Indirizzo" && result.address.isNotBlank() && !SearchRelevance.isGoodMatch(clean, result)) {
                result.copy(typeLabel = "Indirizzo da verificare", suggestedName = clean,
                    category = result.category ?: terms.kind.placeCategory)
            } else result
        }).filter { result ->
            terms.kind == SearchKind.ALL || result.category == terms.kind.placeCategory ||
                result.requiresAddressCheck || (result.category == null && result.typeLabel != "Città" && SearchRelevance.isGoodMatch(clean, result))
        }.toMutableList()
        // Retry the intact business name when a generic-looking prefix could be part of its brand.
        val alternateOutcome = if (terms.text.isNotBlank() && terms.text != clean && candidates.none { SearchRelevance.isGoodMatch(clean, it) }) {
            attempt { alternate.search(clean, center, kind) }.also { candidates += it.results.filter { result ->
                terms.kind == SearchKind.ALL || result.category == terms.kind.placeCategory ||
                    (result.category == null && SearchRelevance.isGoodMatch(clean, result))
            } }
        } else null
        if (outcomes.none { it.succeeded } && alternateOutcome?.succeeded != true) {
            throw IOException("La ricerca non è disponibile adesso. Controlla la connessione e riprova.")
        }
        val results = SearchRelevance.mergeAndRank(clean, center, candidates)
        cache[key] = Cached(System.currentTimeMillis(), results)
        results
    }

    private suspend fun attempt(action: suspend () -> List<SearchResult>): Outcome = try {
        Outcome(action(), true)
    } catch (_: TimeoutCancellationException) {
        Outcome(emptyList(), false)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        Outcome(emptyList(), false)
    }
}
