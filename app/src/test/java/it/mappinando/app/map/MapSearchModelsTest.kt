package it.mappinando.app.map

import it.mappinando.app.data.Place
import it.mappinando.app.data.PlaceCategory
import org.junit.Assert.*
import org.junit.Test

class MapSearchModelsTest {
    @Test fun restaurantPrefixFiltersBusinessesWithoutRequiringTheWordInTheirName() {
        assertEquals(SearchTerms("Nerino Dieci Milano", SearchKind.RESTAURANTS), SearchTerms.parse("  Ristorante   Nerino Dieci Milano ", SearchKind.ALL))
        assertEquals(SearchTerms("Milano", SearchKind.RESTAURANTS), SearchTerms.parse("ristoranti Milano", SearchKind.ALL))
        assertEquals(SearchTerms("", SearchKind.RESTAURANTS), SearchTerms.parse("ristoranti", SearchKind.ALL))
    }

    @Test fun nameSearchAndExplicitFilterArePreserved() {
        assertEquals(SearchTerms("Nerino Dieci Milano", SearchKind.ALL), SearchTerms.parse("Nerino Dieci Milano", SearchKind.ALL))
        assertEquals(SearchTerms("Bari", SearchKind.ALL), SearchTerms.parse("Bari", SearchKind.ALL))
        assertEquals(SearchTerms("Basso Milano", SearchKind.CAFES), SearchTerms.parse("Bar Basso Milano", SearchKind.ALL))
        assertEquals(SearchKind.SHOPS, SearchTerms.parse("Nerino", SearchKind.SHOPS).kind)
    }

    @Test fun urlEncodesNamesAndUsesMapCenterWithoutRestrictingSearchToItsBounds() {
        val url = PhotonSearch.buildUrl(SearchTerms("L'oro & il pane, Roma", SearchKind.RESTAURANTS), SearchCenter(45.464234, 9.190024))
        assertTrue(url.startsWith("https://photon.komoot.io/api/?"))
        assertTrue(url.contains("q=L%27oro+%26+il+pane%2C+Roma"))
        assertTrue(url.contains("lat=45.4642&lon=9.1900"))
        assertTrue(url.contains("osm_tag=amenity%3Arestaurant"))
        assertTrue(url.contains("limit=30"))
        assertFalse(url.contains("bbox="))
    }

    @Test fun genericActivityQuerySearchesNearTheVisibleMap() {
        val url = PhotonSearch.buildUrl(SearchTerms.parse("ristoranti", SearchKind.ALL), SearchCenter(-33.8688, 151.2093))
        assertTrue(url.startsWith("https://photon.komoot.io/reverse?"))
        assertTrue(url.contains("radius=15"))
        assertFalse(url.contains("q="))
    }

    @Test fun previewRecognizesSavedPlacesWithoutConfusingDifferentBusinessesAtTheSameAddress() {
        val place = Place(name = "Nerino Dieci", category = PlaceCategory.RESTAURANTS, latitude = 45.46, longitude = 9.18)
        assertEquals(place, SearchResult.fromPlace(place).savedPlace(listOf(place)))
        assertEquals(place, SearchResult("NERINO DIECI", "", 45.460001, 9.180001).savedPlace(listOf(place)))
        assertNull(SearchResult("Un altro locale", "", 45.46, 9.18).savedPlace(listOf(place)))
    }
}
