package it.mappinando.app.map

import androidx.test.ext.junit.runners.AndroidJUnit4
import it.mappinando.app.data.PlaceCategory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotonSearchInstrumentedTest {
    @Test fun businessNameAddressCategoryAndCoordinateOrderArePreserved() {
        val result = PhotonSearch.parseResults("""{"features":[{"geometry":{"type":"Point","coordinates":[9.1833265,45.4611083]},"properties":{"name":"Nerino Dieci Trattoria","street":"Via Nerino","housenumber":"10","city":"Milano","country":"Italia","osm_key":"amenity","osm_value":"restaurant"}}]}""").single()
        assertEquals("Nerino Dieci Trattoria", result.name)
        assertEquals("Via Nerino 10, Milano, Italia", result.address)
        assertEquals(45.4611083, result.latitude, .0000001)
        assertEquals(9.1833265, result.longitude, .0000001)
        assertEquals("Milano", result.city)
        assertEquals("Ristorante", result.typeLabel)
        assertEquals(PlaceCategory.RESTAURANTS, result.category)
        assertNull(result.savedPlaceId)
    }

    @Test fun malformedFeaturesAreIgnoredAndDuplicateBusinessesAreRemoved() {
        val good = """{"geometry":{"type":"Point","coordinates":[-9.18,45.46]},"properties":{"name":"Caffè & idee","city":"Milano","osm_key":"amenity","osm_value":"cafe"}}"""
        val invalid = """{"geometry":{"type":"Point","coordinates":[9,999]},"properties":{"name":"Invalid"}}"""
        val result = PhotonSearch.parseResults("""{"features":[$good,{},$invalid,$good]}""").single()
        assertEquals("Caffè & idee", result.name)
        assertEquals(PlaceCategory.BARS, result.category)
        assertEquals(-9.18, result.longitude, .00001)
    }

    @Test fun unnamedAddressesAndEmptyResponsesAreHandled() {
        assertTrue(PhotonSearch.parseResults("""{"features":[]}""").isEmpty())
        val address = PhotonSearch.parseResults("""{"features":[{"geometry":{"type":"Point","coordinates":[9,45]},"properties":{"street":"Via Roma","housenumber":"8","city":"Torino"}}]}""").single()
        assertEquals("Via Roma 8", address.name)
        assertNull(address.category)
    }
}
