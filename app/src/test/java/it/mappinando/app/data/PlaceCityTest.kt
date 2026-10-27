package it.mappinando.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceCityTest {
    private fun place(name: String = "Un luogo", city: String = "", address: String = "") = Place(
        name = name,
        category = PlaceCategory.NATURE,
        latitude = 45.4642,
        longitude = 9.19,
        address = address,
        city = city,
    )

    @Test fun normalizesSpacesWithoutChangingCitySpelling() {
        assertEquals("Reggio Emilia", PlaceCity.normalize("  Reggio\n \tEmilia  "))
        assertEquals("São Paulo", PlaceCity.normalize("São\u00a0Paulo"))
        assertEquals("L’Aquila", PlaceCity.normalize(" L’Aquila "))
    }

    @Test fun migratesOnlyTheExplicitLegacyCitySuffix() {
        assertEquals("Milano", PlaceCity.legacyCity("Piazza del Duomo · Milano"))
        assertEquals("Reggio Emilia", PlaceCity.legacyCity("Via Roma, 2 · Reggio Emilia"))
        assertEquals("", PlaceCity.legacyCity("Via Milano, 12"))
        assertEquals("", PlaceCity.legacyCity("Via Roma, 12, 20121 Milano, Italia"))
        assertEquals("", PlaceCity.legacyCity("Via Roma · 20121 Milano"))
        assertEquals("", PlaceCity.legacyCity("Via Roma · "))
    }

    @Test fun explicitCityWinsOverOldAddressAndUnknownCoordinatesStayUnknown() {
        assertEquals("Torino", PlaceCity.resolvedCity(place(city = " Torino ", address = "Piazza del Duomo · Milano")))
        assertEquals("Milano", PlaceCity.resolvedCity(place(address = "Piazza del Duomo · Milano")))
        assertEquals("", PlaceCity.resolvedCity(place()))
    }

    @Test fun groupsCitiesIgnoringCaseAndKeepsUnknownLast() {
        val firstMilano = place(name = "Primo", city = "milano")
        val secondMilano = place(name = "Secondo", city = " MILANO ")
        val groups = PlaceCity.group(listOf(
            place(city = "Torino"), firstMilano, place(), secondMilano,
            place(address = "Via del Corso · Roma"),
        ))
        assertEquals(listOf("milano", "Roma", "Torino", ""), groups.map { it.city })
        assertEquals(listOf(firstMilano, secondMilano), groups.first().places)
        assertEquals(1, groups.last().places.size)
    }

    @Test fun emptyCollectionProducesNoCitySections() {
        assertTrue(PlaceCity.group(emptyList()).isEmpty())
    }
}
