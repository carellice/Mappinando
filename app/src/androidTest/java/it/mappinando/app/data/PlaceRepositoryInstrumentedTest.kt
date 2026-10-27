package it.mappinando.app.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Every test uses a UUID-prefixed preference file; real app data is never opened. */
@RunWith(AndroidJUnit4::class)
class PlaceRepositoryInstrumentedTest {
    private lateinit var context: IsolatedPreferencesContext
    private lateinit var repository: PlaceRepository

    @Before fun setUp() {
        context = IsolatedPreferencesContext(InstrumentationRegistry.getInstrumentation().targetContext)
        repository = PlaceRepository(context)
    }

    @After fun tearDown() {
        context.deleteTestPreferences()
    }

    @Test fun startsEmptyAndKeepsAnIntentionallyEmptyCollection() {
        assertTrue(repository.places.value.isEmpty())
        repository.clearAll()
        assertTrue(repository.places.value.isEmpty())

        val reopened = PlaceRepository(context)
        assertTrue("Deleted demo places must not reappear", reopened.places.value.isEmpty())

        val created = reopened.add(newPlace())
        assertEquals(listOf(created), PlaceRepository(context).places.value)
    }

    @Test fun removesOnlyUnmodifiedLegacyExamplesAndRunsMigrationOnce() {
        // Build a pre-migration payload, including a personalised example and a genuine place.
        val edited = DemoPlaces.create().first().copy(note = "La mia nota personale", favorite = false)
        val own = newPlace()
        DemoPlaces.create().drop(1).forEach(repository::add)
        repository.add(edited); repository.add(own)
        val migrated = PlaceRepository(context)
        assertEquals(listOf(edited, own), migrated.places.value)
        assertEquals(listOf(edited, own), PlaceRepository(context).places.value)
        // Matching a legacy entry later must never trigger a second deletion.
        migrated.add(DemoPlaces.create()[1])
        assertEquals(3, PlaceRepository(context).places.value.size)
    }

    @Test fun createsUpdatesAndDeletesPlacesAcrossRepositoryInstances() {
        repository.clearAll()
        val created = repository.add(newPlace().copy(name = "  Il mio posto  "))
        assertEquals("Il mio posto", created.name)
        assertEquals(created, PlaceRepository(context).places.value.single())

        val updated = created.copy(name = "Il mio posto preferito", note = "Tornarci domenica", category = PlaceCategory.VIEWS)
        assertTrue(repository.update(updated))
        assertEquals(updated, PlaceRepository(context).places.value.single())

        assertTrue(repository.delete(created.id))
        assertTrue(PlaceRepository(context).places.value.isEmpty())
        assertFalse(repository.delete(created.id))
    }

    @Test fun persistsFavoriteVisitedAndAllTaskChanges() {
        repository.clearAll()
        val created = repository.add(newPlace())
        assertTrue(repository.toggleFavorite(created.id))
        assertTrue(repository.toggleVisited(created.id))
        assertTrue(repository.addTask(created.id, "  Fotografare il panorama  "))
        val task = repository.places.value.single().tasks.single()
        assertEquals("Fotografare il panorama", task.title)
        assertTrue(repository.toggleTask(created.id, task.id))

        val reopened = PlaceRepository(context).places.value.single()
        assertTrue(reopened.favorite)
        assertTrue(reopened.visited)
        assertTrue(reopened.tasks.single().done)
        assertEquals(task.id, reopened.tasks.single().id)

        assertTrue(repository.deleteTask(created.id, task.id))
        assertTrue(PlaceRepository(context).places.value.single().tasks.isEmpty())
    }

    @Test fun persistsNormalizedCityAndLaterCityEdits() {
        repository.clearAll()
        val created = repository.add(newPlace().copy(city = " Reggio\n Emilia "))
        assertEquals("Reggio Emilia", created.city)
        assertEquals(created, PlaceRepository(context).places.value.single())

        val updated = created.copy(city = "Torino")
        assertTrue(repository.update(updated))
        assertEquals(updated, PlaceRepository(context).places.value.single())
    }

    @Test fun legacyPayloadRestoresCityWithoutLosingAnySavedPlaceDetails() {
        repository.clearAll()
        val original = repository.add(newPlace().copy(
            address = "Via Felice Casati, 27 · Milano",
            note = "La mia nota precedente",
            favorite = true,
            visited = true,
            tasks = listOf(PlaceTask("legacy-task", "Una cosa già fatta", true)),
        ))
        val preferences = context.getSharedPreferences("mappinando_places", Context.MODE_PRIVATE)
        val legacy = JSONArray(preferences.getString("places.v1", "[]"))
        legacy.getJSONObject(0).remove("city")
        assertTrue(preferences.edit().putString("places.v1", legacy.toString()).commit())

        val reopened = PlaceRepository(context)
        assertEquals(original.copy(city = "Milano"), reopened.places.value.single())
        reopened.toggleFavorite(original.id)
        val savedCity = JSONArray(preferences.getString("places.v1", "[]")).getJSONObject(0).getString("city")
        assertEquals("Milano", savedCity)
        assertEquals(original.copy(city = "Milano", favorite = false), PlaceRepository(context).places.value.single())
    }

    @Test fun legacyUnstructuredAddressDoesNotInventACityAndExplicitCityWins() {
        repository.clearAll()
        val original = repository.add(newPlace().copy(address = "Via Roma, 12, 20121 Milano, Italia"))
        val preferences = context.getSharedPreferences("mappinando_places", Context.MODE_PRIVATE)
        val legacy = JSONArray(preferences.getString("places.v1", "[]"))
        legacy.getJSONObject(0).remove("city")
        assertTrue(preferences.edit().putString("places.v1", legacy.toString()).commit())
        assertEquals(original, PlaceRepository(context).places.value.single())

        val explicit = original.copy(city = "Torino", address = "Un vecchio indirizzo · Milano")
        assertTrue(repository.update(explicit))
        assertEquals(explicit, PlaceRepository(context).places.value.single())
    }

    @Test fun invalidInputAndDuplicateIdsDoNotChangeSavedPlaces() {
        repository.clearAll()
        val created = repository.add(newPlace())
        val original = repository.places.value

        expectInvalid { repository.add(created) }
        expectInvalid { repository.update(created.copy(latitude = Double.NaN)) }
        expectInvalid { repository.update(created.copy(longitude = 200.0)) }
        expectInvalid { repository.add(newPlace().copy(name = "  ")) }
        expectInvalid { repository.addTask(created.id, "  ") }

        assertEquals(original, repository.places.value)
        assertEquals(original, PlaceRepository(context).places.value)
    }

    @Test fun missingPlaceMutationsAreHarmless() {
        val original = repository.places.value
        assertFalse(repository.update(newPlace()))
        assertFalse(repository.delete("missing"))
        assertFalse(repository.toggleFavorite("missing"))
        assertFalse(repository.toggleVisited("missing"))
        assertFalse(repository.addTask("missing", "Un'attività"))
        assertFalse(repository.toggleTask("missing", "task"))
        assertFalse(repository.deleteTask("missing", "task"))
        assertEquals(original, repository.places.value)
    }

    @Test fun restoresGoodRowsWhenAnotherRecordIsMalformed() {
        repository.clearAll()
        val created = repository.add(newPlace())
        val preferences = context.getSharedPreferences("mappinando_places", Context.MODE_PRIVATE)
        val serialized = JSONArray(preferences.getString("places.v1", "[]"))
        serialized.put(JSONObject().put("name", "Malformed record without coordinates"))
        // Duplicate IDs must not produce two markers or duplicate LazyColumn keys.
        serialized.put(serialized.getJSONObject(0))
        assertTrue(preferences.edit().putString("places.v1", serialized.toString()).commit())

        assertEquals(listOf(created), PlaceRepository(context).places.value)
    }

    @Test fun keepsAnUnreadablePayloadForRecoveryWithoutReseedingDemo() {
        val preferences = context.getSharedPreferences("mappinando_places", Context.MODE_PRIVATE)
        val unreadable = "{ interrupted-json"
        assertTrue(preferences.edit().putString("places.v1", unreadable).commit())

        val reopened = PlaceRepository(context)
        assertTrue(reopened.places.value.isEmpty())
        assertEquals(unreadable, preferences.getString("places.v1.recovery", null))
        reopened.add(newPlace())
        assertNotNull(preferences.getString("places.v1.recovery", null))
        assertEquals(1, PlaceRepository(context).places.value.size)
    }

    private fun newPlace() = Place(
        name = "Il mio posto",
        category = PlaceCategory.NATURE,
        latitude = 45.4642,
        longitude = 9.1900,
        address = "Milano",
        note = "Da scoprire"
    )

    private fun expectInvalid(action: () -> Unit) {
        var rejected = false
        try {
            action()
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue("Invalid input must be rejected", rejected)
    }

    private class IsolatedPreferencesContext(base: Context) : ContextWrapper(base) {
        private val prefix = "mappinando_test_${UUID.randomUUID()}_"
        private val preferenceFiles = mutableSetOf<String>()

        // PlaceRepository deliberately uses applicationContext; keep that isolated as well.
        override fun getApplicationContext(): Context = this

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val isolatedName = prefix + name
            preferenceFiles += isolatedName
            return baseContext.getSharedPreferences(isolatedName, mode)
        }

        fun deleteTestPreferences() {
            preferenceFiles.forEach { name ->
                // commit waits for pending apply writes before deleting this test-only file.
                baseContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
                baseContext.deleteSharedPreferences(name)
            }
        }
    }
}
