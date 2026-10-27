package it.mappinando.app.map

import it.mappinando.app.data.PlaceCategory
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class FreePlaceSearchTest {
    private val center = SearchCenter(41.9028, 12.4964)
    private val distant = SearchResult("Arrosticini Divini", "Via Castello, L'Aquila", 42.3527, 13.4017, "L'Aquila", PlaceCategory.RESTAURANTS, "Ristorante")
    private val address = SearchResult("Via della Bufalotta 871", "Via della Bufalotta, 871, La Cinquina - Bufalotta RM, Italia", 41.9694171, 12.5499763, "La Cinquina - Bufalotta", typeLabel = "Indirizzo")

    @Test fun partialNameResultsDoNotSuppressDeviceLookupAndAddressIsClearlyUnverified() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val service = FreePlaceSearch(
            PlaceSearcher { _, _, _ -> started.await(); listOf(distant) },
            PlaceSearcher { _, _, _ -> started.complete(Unit); listOf(address) },
        )
        val results = withTimeout(2_000) { service.search("Arrosticini Divini Roma", center, SearchKind.RESTAURANTS) }
        val candidate = results.single { it.requiresAddressCheck }
        assertEquals(address.name, candidate.name)
        assertEquals(address.latitude, candidate.latitude, .0000001)
        assertEquals("Arrosticini Divini Roma", candidate.suggestedName)
        assertEquals("Indirizzo da verificare", candidate.typeLabel)
        assertEquals(PlaceCategory.RESTAURANTS, candidate.category)
        assertEquals(2, results.size)
    }

    @Test fun failureOfOneSourceDoesNotDiscardTheOther() = runBlocking {
        val service = FreePlaceSearch(PlaceSearcher { _, _, _ -> throw IOException() }, PlaceSearcher { _, _, _ -> listOf(address) })
        assertEquals(address.latitude, service.search("Arrosticini Divini Roma", center, SearchKind.ALL).single().latitude, .0000001)
    }

    @Test fun failureOfBothSourcesRemainsAnErrorAndDoesNotBecomeEmptySuccess() = runBlocking {
        val failing = PlaceSearcher { _, _, _ -> throw IOException() }
        try { FreePlaceSearch(failing, failing).search("Un locale", center, SearchKind.ALL); fail("Expected IOException") }
        catch (_: IOException) { }
    }

    @Test fun categoryOnlyQueryDoesNotSendAnAddressLookup() = runBlocking {
        var calls = 0
        val service = FreePlaceSearch(PlaceSearcher { _, _, _ -> listOf(distant) }, PlaceSearcher { _, _, _ -> calls++; emptyList() })
        assertEquals(1, service.search("ristoranti", center, SearchKind.ALL).size)
        assertEquals(0, calls)
    }

    @Test fun intactBusinessPrefixCanBeRetriedAndRepeatedQueryUsesCache() = runBlocking {
        var alternateCalls = 0
        val empty = PlaceSearcher { _, _, _ -> emptyList() }
        val real = SearchResult("Bar Basso", "Milano", 45.47, 9.2, "Milano", PlaceCategory.BARS)
        val service = FreePlaceSearch(empty, empty, PlaceSearcher { query, _, _ -> assertEquals("Bar Basso Milano", query); alternateCalls++; listOf(real) })
        assertEquals(real, service.search("Bar Basso Milano", center, SearchKind.ALL).single())
        service.search("  Bar   Basso Milano ", center, SearchKind.ALL)
        assertEquals(1, alternateCalls)
    }

    @Test fun cancellationIsPropagatedInsteadOfBecomingAnEmptyResult() = runBlocking {
        val cancelled = PlaceSearcher { _, _, _ -> throw CancellationException("Dismissed") }
        try { FreePlaceSearch(cancelled, cancelled).search("Un locale", center, SearchKind.ALL); fail("Expected cancellation") }
        catch (_: CancellationException) { }
    }

    @Test fun cityOnlyDeviceResultIsNotPresentedAsARestaurant() = runBlocking {
        val service = FreePlaceSearch(PlaceSearcher { _, _, _ -> emptyList() }, PlaceSearcher { _, _, _ -> listOf(SearchResult("Roma", "Roma", 41.9, 12.5, "Roma", typeLabel = "Città")) })
        assertTrue(service.search("Ristorante sconosciuto Roma", center, SearchKind.RESTAURANTS).isEmpty())
    }
}
