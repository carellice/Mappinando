package it.mappinando.app.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.mappinando.app.data.*
import it.mappinando.app.map.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import java.io.IOException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MapSearchInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var storage: IsolatedContext
    private lateinit var root: View
    private val restaurant = SearchResult("Nerino Dieci Trattoria", "Via Nerino 10, Milano", 45.4611083, 9.1833265, "Milano", PlaceCategory.RESTAURANTS, "Ristorante")

    @After fun cleanUp() { if (::storage.isInitialized) storage.clear() }

    @Test fun fullscreenSearchMovesCameraBeforeExplicitSaveAndPreservesBusinessCategory() {
        var submitted: Triple<String, SearchCenter, SearchKind>? = null
        mount(PlaceSearcher { query, center, kind -> submitted = Triple(query, center, kind); listOf(restaurant) })
        val originalCount = compose.runOnIdle { PlaceRepository(storage).places.value.size }
        compose.onNodeWithTag("map-fullscreen-toggle").performClick()
        compose.onNodeWithTag("map-search").assertIsDisplayed().performClick()
        compose.onNodeWithTag("search-query").performTextInput("Nerino Dieci Milano")
        compose.onNodeWithTag("search-kind-RESTAURANTS").performClick()
        compose.onNodeWithTag("search-submit").performScrollTo().performClick()
        compose.waitUntil { compose.onAllNodesWithTag("search-result").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("search-result").performScrollTo().performClick()
        compose.onNodeWithTag("fullscreen-map").assertIsDisplayed()
        compose.onNodeWithTag("map-search-preview").assertIsDisplayed()
        compose.onNodeWithTag("editor-name").assertDoesNotExist()
        compose.onNodeWithText("Aggiungi alle tappe").assertIsDisplayed()
        compose.onNodeWithTag("map-preview-google-maps").assertIsDisplayed().performClick()
        compose.onNodeWithTag("map-search-preview").assertIsDisplayed()
        compose.runOnIdle {
            val intent = storage.openedIntents.single()
            assertEquals("com.google.android.apps.maps", intent.`package`)
            assertEquals(restaurant.address, intent.data?.getQueryParameter("query"))
            assertEquals("Nerino Dieci Milano", submitted?.first)
            assertEquals(SearchKind.RESTAURANTS, submitted?.third)
            assertEquals(originalCount, PlaceRepository(storage).places.value.size)
        }
        assertCameraAt(restaurant.latitude, restaurant.longitude)
        compose.onAllNodesWithTag("native-map-view").assertCountEquals(1)
        compose.onNodeWithTag("map-preview-save").performClick()
        compose.onNodeWithTag("editor-name").assertTextContains(restaurant.name)
        compose.onNodeWithTag("editor-save").performScrollTo().performClick()
        compose.onNodeWithTag("map-search-preview").assertDoesNotExist()
        compose.runOnIdle {
            val saved = PlaceRepository(storage).places.value.single { it.name == restaurant.name }
            assertEquals(PlaceCategory.RESTAURANTS, saved.category)
            assertEquals("Milano", saved.city)
            assertEquals(restaurant.latitude, saved.latitude, .0000001)
            assertEquals(originalCount + 1, PlaceRepository(storage).places.value.size)
        }
    }

    @Test fun savedResultOnlyPreviewsAndOpensExistingPlaceWithoutDuplicatingIt() {
        mount(PlaceSearcher { _, _, _ -> throw IOException("Offline") }, seedSavedPlace = true)
        val saved = compose.runOnIdle { PlaceRepository(storage).places.value.first() }
        val originalCount = compose.runOnIdle { PlaceRepository(storage).places.value.size }
        compose.onNodeWithTag("search-entry").performClick()
        compose.onNodeWithTag("search-query").performTextInput(saved.name)
        compose.onNodeWithTag("search-saved-${saved.id}").performScrollTo().performClick()
        compose.onNodeWithText("GIÀ TRA LE TUE TAPPE").assertIsDisplayed()
        compose.onNodeWithTag("editor-name").assertDoesNotExist()
        compose.onNodeWithTag("map-preview-save").performClick()
        compose.onNodeWithTag("place-detail-screen").assertIsDisplayed()
        compose.runOnIdle { assertEquals(originalCount, PlaceRepository(storage).places.value.size) }
    }

    @Test fun closingPreviewDoesNotSaveAndSearchRemainsAvailable() {
        mount(PlaceSearcher { _, _, _ -> listOf(restaurant) })
        val originalCount = compose.runOnIdle { PlaceRepository(storage).places.value.size }
        compose.onNodeWithTag("search-entry").performClick()
        compose.onNodeWithTag("search-query").performTextInput("Nerino Dieci")
        compose.onNodeWithTag("search-submit").performScrollTo().performClick()
        compose.waitUntil { compose.onAllNodesWithTag("search-result").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("search-result").performScrollTo().performClick()
        compose.onNodeWithTag("map-preview-close").performClick()
        compose.onNodeWithTag("map-search-preview").assertDoesNotExist()
        compose.onNodeWithTag("map-search").assertIsDisplayed().performClick()
        compose.onNodeWithTag("search-query").assertIsDisplayed()
        compose.runOnIdle { assertEquals(originalCount, PlaceRepository(storage).places.value.size) }
    }

    @Test fun networkFailureCanBeRetriedAndNewQueryClearsPreviousResults() {
        var calls = 0
        mount(PlaceSearcher { _, _, _ -> if (++calls == 1) throw IOException("Offline") else listOf(restaurant) })
        compose.onNodeWithTag("search-entry").performClick()
        compose.onNodeWithTag("search-query").performTextInput("Nerino Dieci")
        compose.onNodeWithTag("search-submit").performScrollTo().performClick()
        compose.waitUntil { compose.onAllNodesWithTag("search-error").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("search-error").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("search-submit").performScrollTo().performClick()
        compose.waitUntil { compose.onAllNodesWithTag("search-result").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("search-error").assertDoesNotExist()
        compose.onNodeWithTag("search-query").performScrollTo().performTextReplacement("Al")
        compose.onNodeWithTag("search-result").assertDoesNotExist()
        compose.onNodeWithTag("search-submit").assertIsNotEnabled()
    }

    @Test fun uncertainAddressIsExplainedAndOriginalNameIsOnlyProposedInTheEditor() {
        val wanted = "Arrosticini Divini Roma"
        val address = SearchResult("Via della Bufalotta 871", "Via della Bufalotta 871, Roma", 41.9694171, 12.5499763,
            "Roma", typeLabel = "Indirizzo da verificare", suggestedName = wanted)
        mount(PlaceSearcher { _, _, _ -> listOf(restaurant, address) })
        val originalCount = compose.runOnIdle { PlaceRepository(storage).places.value.size }
        compose.onNodeWithTag("search-entry").performClick()
        compose.onNodeWithTag("search-query").performTextInput(wanted)
        compose.onNodeWithTag("search-submit").performScrollTo().performClick()
        compose.waitUntil { compose.onAllNodesWithTag("search-address-explanation").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("search-address-explanation").performScrollTo().assertIsDisplayed()
        compose.onNode(hasTestTag("search-result") and hasText(address.name)).performScrollTo().performClick()
        compose.onNodeWithTag("map-preview-address-check").assertIsDisplayed()
        compose.runOnIdle { assertEquals(originalCount, PlaceRepository(storage).places.value.size) }
        compose.onNodeWithTag("map-preview-save").performClick()
        compose.onNodeWithTag("editor-name").assertTextContains(wanted)
        compose.runOnIdle { assertEquals(originalCount, PlaceRepository(storage).places.value.size) }
    }

    @Test fun missingBusinessCanBeAddedByAddressWithoutChangingItsChosenName() {
        val wanted = "Locale ancora da scoprire"
        val address = SearchResult("Via della Bufalotta 871", "Via della Bufalotta 871, Roma", 41.9694171, 12.5499763, "Roma", typeLabel = "Indirizzo")
        var submitted: Pair<String, SearchKind>? = null
        mount(PlaceSearcher { query, _, kind ->
            submitted = query to kind
            if (query.startsWith("Via")) listOf(address) else emptyList()
        })
        compose.onNodeWithTag("search-entry").performClick()
        compose.onNodeWithTag("search-query").performTextInput(wanted)
        compose.onNodeWithTag("search-kind-RESTAURANTS").performClick()
        compose.onNodeWithTag("search-missing-place").performScrollTo().performClick()
        compose.onNodeWithTag("search-manual-name").performScrollTo().assertTextContains(wanted)
        compose.onNodeWithTag("search-query").performScrollTo().performTextInput("Via della Bufalotta 871 Roma")
        compose.onNodeWithTag("search-submit").performScrollTo().performClick()
        compose.waitUntil { compose.onAllNodesWithTag("search-result").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("search-result").performScrollTo().performClick()
        compose.onNodeWithTag("map-preview-address-check").assertIsDisplayed()
        compose.onNodeWithTag("map-preview-save").performClick()
        compose.onNodeWithTag("editor-name").assertTextContains(wanted)
        compose.onNodeWithTag("editor-save").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("Via della Bufalotta 871 Roma" to SearchKind.ALL, submitted)
            val saved = PlaceRepository(storage).places.value.single { it.name == wanted }
            assertEquals(address.latitude, saved.latitude, .0000001)
            assertEquals(PlaceCategory.RESTAURANTS, saved.category)
        }
    }

    private fun mount(searcher: PlaceSearcher, seedSavedPlace: Boolean = false) {
        compose.setContent {
            val context = LocalContext.current
            val isolated = remember(context) { IsolatedContext(context).also { isolated ->
                if (seedSavedPlace) PlaceRepository(isolated).add(it.mappinando.app.data.Place(
                    name = "Il mio ristorante salvato", category = PlaceCategory.RESTAURANTS,
                    latitude = restaurant.latitude, longitude = restaurant.longitude, city = "Milano",
                ))
            } }
            val view = LocalView.current
            SideEffect { storage = isolated; root = view.rootView }
            CompositionLocalProvider(LocalContext provides isolated) { MappinandoTheme { MappinandoApp(searcher) } }
        }
    }

    private fun assertCameraAt(latitude: Double, longitude: Double) {
        var map: MapLibreMap? = null
        compose.runOnUiThread {
            fun find(view: View): MapView? = if (view is MapView) view else
                (view as? ViewGroup)?.let { group -> (0 until group.childCount).firstNotNullOfOrNull { find(group.getChildAt(it)) } }
            checkNotNull(find(root)).getMapAsync { map = it }
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            var matches = false
            compose.runOnUiThread { map?.cameraPosition?.target?.let {
                matches = kotlin.math.abs(it.latitude - latitude) < .00001 && kotlin.math.abs(it.longitude - longitude) < .00001
            } }
            matches
        }
    }

    private class IsolatedContext(base: Context) : ContextWrapper(base) {
        private val prefix = "map_search_test_${UUID.randomUUID()}_"
        private val files = mutableSetOf<String>()
        val openedIntents = mutableListOf<Intent>()
        override fun startActivity(intent: Intent) { openedIntents += Intent(intent) }
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val isolated = prefix + name; files += isolated
            return baseContext.getSharedPreferences(isolated, mode)
        }
        fun clear() { files.forEach { baseContext.deleteSharedPreferences(it) } }
    }
}
