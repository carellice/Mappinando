package it.mappinando.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceValidationTest {
    private fun place() = Place(
        name = "Un bel posto",
        category = PlaceCategory.NATURE,
        latitude = 45.4642,
        longitude = 9.1900
    )

    @Test fun normalizesWhitespaceWithoutLosingTaskState() {
        val normalized = PlaceValidation.normalized(place().copy(
            name = "  Un bel posto  ",
            address = "  Milano  ",
            city = "  Reggio \n Emilia  ",
            note = "  Da ricordare  ",
            tasks = listOf(PlaceTask("task", "  Fare una foto  ", true))
        ))
        assertEquals("Un bel posto", normalized.name)
        assertEquals("Milano", normalized.address)
        assertEquals("Reggio Emilia", normalized.city)
        assertEquals("Da ricordare", normalized.note)
        assertEquals("Fare una foto", normalized.tasks.single().title)
        assertTrue(normalized.tasks.single().done)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankName() {
        PlaceValidation.normalized(place().copy(name = " \n "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsLatitudeOutsideMap() {
        PlaceValidation.normalized(place().copy(latitude = 90.001))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonFiniteCoordinates() {
        PlaceValidation.normalized(place().copy(longitude = Double.NaN))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateTaskIds() {
        PlaceValidation.normalized(place().copy(tasks = listOf(
            PlaceTask("same", "Prima attività"),
            PlaceTask("same", "Seconda attività")
        )))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyTask() {
        PlaceValidation.normalized(place().copy(tasks = listOf(PlaceTask(title = "  "))))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCityLongerThanSupportedLimit() {
        PlaceValidation.normalized(place().copy(city = "a".repeat(PlaceValidation.MAX_CITY_LENGTH + 1)))
    }

    @Test fun acceptsBoundaryCoordinatesAndEmptyTaskList() {
        val normalized = PlaceValidation.normalized(place().copy(latitude = -90.0, longitude = 180.0))
        assertEquals(-90.0, normalized.latitude, 0.0)
        assertEquals(180.0, normalized.longitude, 0.0)
        assertTrue(normalized.tasks.isEmpty())
    }

    @Test fun demoPlacesHaveUniqueIdsAndValidData() {
        val examples = DemoPlaces.create()
        assertEquals(examples.size, examples.map { it.id }.distinct().size)
        assertEquals(PlaceCategory.entries.toSet(), examples.map { it.category }.toSet())
        assertTrue(examples.all { it.city == "Milano" })
        examples.forEach { PlaceValidation.normalized(it) }
    }
}
