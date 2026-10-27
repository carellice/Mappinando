package it.mappinando.app.map

import it.mappinando.app.data.PlaceCategory
import org.junit.Assert.*
import org.junit.Test

class SearchRelevanceTest {
    private val rome = SearchCenter(41.9028, 12.4964)

    @Test fun requestedCityOutranksSameBrandInOtherCities() {
        val aquila = SearchResult("Arrosticini Divini", "Via Castello, L'Aquila", 42.35, 13.4, "L'Aquila")
        val roma = SearchResult("Arrosticini Divini", "Via della Bufalotta 871, Roma", 41.9694, 12.5499, "Roma")
        val result = SearchRelevance.mergeAndRank("Arrosticini Divini Roma", rome, listOf(aquila, roma))
        assertEquals(roma, result.first())
        assertTrue(SearchRelevance.isGoodMatch("Arrosticini Divini Roma", roma))
        assertFalse(SearchRelevance.isGoodMatch("Arrosticini Divini Roma", aquila))
    }

    @Test fun nearbyDuplicatesAreMergedWithUsefulCategoryAndAddress() {
        val osm = SearchResult("Caffè dell'Arte", "", 41.9, 12.5, category = PlaceCategory.BARS, typeLabel = "Caffè")
        val device = SearchResult("CAFFE DELL’ARTE", "Via Roma 8", 41.90001, 12.50001, city = "Roma")
        val result = SearchRelevance.mergeAndRank("Caffè dell'Arte", rome, listOf(osm, device)).single()
        assertEquals(PlaceCategory.BARS, result.category)
        assertEquals("Via Roma 8", result.address)
        assertEquals("Roma", result.city)
    }

    @Test fun differentBranchesAndBusinessesAtSameAddressAreNotCollapsed() {
        val first = SearchResult("Bar Centrale", "", 41.9, 12.5)
        val otherBranch = first.copy(latitude = 41.91)
        val otherName = first.copy(name = "Bar al Centro")
        assertEquals(3, SearchRelevance.mergeAndRank("bar", rome, listOf(first, otherBranch, otherName)).size)
    }

    @Test fun distanceOnlyBreaksEqualRelevanceAndInvalidCoordinatesAreRejected() {
        val nearby = SearchResult("Panificio Sole", "Roma", 41.903, 12.496)
        val far = nearby.copy(latitude = 45.0)
        val invalid = nearby.copy(latitude = Double.NaN)
        assertEquals(listOf(nearby, far), SearchRelevance.mergeAndRank("Panificio Sole", rome, listOf(far, invalid, nearby)))
    }

    @Test fun resultCollectionKeepsMoreThanTenButHasABoundedSize() {
        val results = (1..60).map { SearchResult("Locale $it", "Roma", 41.9 + it * .001, 12.5) }
        assertEquals(40, SearchRelevance.mergeAndRank("Roma", rome, results).size)
    }
}
