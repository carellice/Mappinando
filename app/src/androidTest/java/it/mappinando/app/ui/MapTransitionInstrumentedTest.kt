package it.mappinando.app.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import it.mappinando.app.data.Place
import it.mappinando.app.data.PlaceCategory
import it.mappinando.app.data.PlaceTask
import it.mappinando.app.data.PlaceRepository
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.maps.MapView
import java.util.UUID

/** Regression tests sample layout during the animation, rather than only its endpoints. */
@RunWith(AndroidJUnit4::class)
class MapTransitionInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var storageContext: IsolatedPreferencesContext
    private lateinit var dispatcher: OnBackPressedDispatcher
    private lateinit var androidRoot: View
    private var nativeWidth = 0
    private var nativeHeight = 0

    @After fun cleanUp() {
        compose.mainClock.autoAdvance = true
        if (::storageContext.isInitialized) storageContext.deleteTestPreferences()
    }

    @Test fun expansionAndCollapseHaveContinuousBoundsAndKeepTheCompactAnchorFixed() {
        mountApp()
        val compact = mapBounds()
        val anchor = anchorBounds()
        val originalMapView = onlyMapView()
        compose.mainClock.autoAdvance = false

        compose.onNodeWithTag("map-fullscreen-toggle").performClick()
        val growing = sampleFrames(anchor)
        growing.zipWithNext().forEach { (before, after) ->
            assertTrue("La mappa non deve risalire a scatti", after.top <= before.top + 2f)
            assertTrue("La mappa non deve spostarsi a destra durante l’apertura", after.left <= before.left + 2f)
            assertTrue("La larghezza deve crescere senza inversioni", after.width >= before.width - 2f)
            assertTrue("L’altezza deve crescere senza inversioni", after.height >= before.height - 2f)
        }
        advance(500)
        val fullscreen = mapBounds()
        assertTrue(fullscreen.height > compact.height * 1.3f)
        assertTrue("Il primo fotogramma deve essere intermedio", growing.first().height > compact.height + 2f)
        assertTrue("L’apertura non deve saltare subito al rettangolo finale", growing.first().height < fullscreen.height - 2f)
        assertRectClose(anchor, anchorBounds())
        assertSame("La MapView deve restare la stessa durante l’apertura", originalMapView, onlyMapView())

        compose.onNodeWithContentDescription("Riduci mappa").performClick()
        val shrinking = sampleFrames(anchor)
        shrinking.zipWithNext().forEach { (before, after) ->
            assertTrue("La mappa non deve saltare verso l’alto in chiusura", after.top >= before.top - 2f)
            assertTrue("La larghezza deve ridursi senza inversioni", after.width <= before.width + 2f)
            assertTrue("L’altezza deve ridursi senza inversioni", after.height <= before.height + 2f)
        }
        advance(500)
        assertRectClose(compact, mapBounds())
        assertRectClose(anchor, anchorBounds())
        assertSame("Il ritorno non deve ricreare la MapView", originalMapView, onlyMapView())
        compose.onNodeWithTag("tab-MAP").assertIsSelected()
    }

    @Test fun backDuringExpansionReversesFromTheCurrentRectangleWithoutSnapping() {
        mountApp()
        val compact = mapBounds()
        val anchor = anchorBounds()
        val originalMapView = onlyMapView()
        compose.mainClock.autoAdvance = false

        compose.onNodeWithTag("map-fullscreen-toggle").performClick()
        advance(160)
        val interrupted = mapBounds()
        assertTrue("Il test deve interrompere una mappa già in espansione", interrupted.height > compact.height + 8f)

        compose.runOnUiThread { dispatcher.onBackPressed() }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val reversed = mapBounds()
        val travelled = interrupted.height - compact.height
        assertTrue("Indietro non deve saltare direttamente alla miniatura", reversed.height > compact.height + travelled * .55f)
        assertTrue("Indietro non deve completare prima l’espansione", reversed.height <= interrupted.height + 3f)
        assertRectClose(anchor, anchorBounds())

        advance(80)
        assertTrue("La transizione deve proseguire verso la forma compatta", mapBounds().height <= reversed.height + 2f)
        advance(500)
        assertRectClose(compact, mapBounds())
        assertRectClose(anchor, anchorBounds())
        assertSame("Indietro non deve ricreare la mappa", originalMapView, onlyMapView())
        compose.onNodeWithTag("map-preview").assertIsDisplayed()
        compose.onNodeWithTag("tab-MAP").assertIsSelected()
    }

    @Test fun scrollingNextPlacesDoesNotChangeMapHeightOrRecreateItsView() {
        mountApp(seedCards = true)
        val anchor = anchorBounds()
        val map = onlyMapView()
        repeat(4) {
            compose.onNodeWithTag("next-places").performTouchInput { swipeLeft() }
            compose.waitForIdle()
            assertRectClose(anchor, anchorBounds())
            assertSame(map, onlyMapView())
        }
    }

    private fun mountApp(seedCards: Boolean = false) {
        compose.setContent {
            val baseContext = LocalContext.current
            val isolated = remember(baseContext) { IsolatedPreferencesContext(baseContext).also { isolated ->
                if (seedCards) {
                    val repository = PlaceRepository(isolated)
                    repeat(8) { index -> repository.add(Place(
                        name = if (index % 2 == 0) "Bar" else "Un ristorante con un nome molto lungo da provare",
                        category = PlaceCategory.entries[index % PlaceCategory.entries.size],
                        latitude = 42.0, longitude = 12.0,
                        tasks = if (index % 2 == 0) emptyList() else List(12) { PlaceTask(title = "Idea $it") },
                    )) }
                }
            } }
            val owner = LocalOnBackPressedDispatcherOwner.current
            val view = LocalView.current
            SideEffect {
                storageContext = isolated
                dispatcher = checkNotNull(owner).onBackPressedDispatcher
                androidRoot = view.rootView
            }
            CompositionLocalProvider(LocalContext provides isolated) {
                MappinandoTheme { MappinandoApp() }
            }
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("map-preview").fetchSemanticsNodes().size == 1
        }
        compose.waitForIdle()
        compose.onNodeWithTag("map-preview").assertIsDisplayed()
        val mountedMap = onlyMapView()
        compose.runOnUiThread {
            val view = mountedMap
            nativeWidth = view.width
            nativeHeight = view.height
        }
    }

    private fun sampleFrames(anchor: Rect): List<Rect> = (1..4).map {
        // Relative samples are 80, 160, 240 and 320 ms after the requested transition.
        advance(80)
        assertRectClose(anchor, anchorBounds())
        mapBounds()
    }

    private fun advance(milliseconds: Long) {
        compose.mainClock.advanceTimeBy(milliseconds)
        compose.waitForIdle()
        val currentMap = onlyMapView()
        compose.runOnUiThread {
            val view = currentMap
            assertEquals("La superficie GL non deve ridimensionarsi durante la transizione", nativeWidth, view.width)
            assertEquals("Il buffer non deve essere stirato tra fotogrammi", nativeHeight, view.height)
        }
    }

    private fun mapBounds(): Rect = compose.onNode(
        hasTestTag("map-preview") or hasTestTag("fullscreen-map"),
        useUnmergedTree = true,
    ).fetchSemanticsNode().boundsInRoot

    private fun anchorBounds(): Rect = compose.onNodeWithTag(
        "map-compact-anchor", useUnmergedTree = true,
    ).fetchSemanticsNode().boundsInRoot

    private fun onlyMapView(): MapView {
        var result: MapView? = null
        compose.runOnUiThread {
            val maps = mutableListOf<MapView>()
            fun visit(view: View) {
                if (view is MapView) maps += view
                if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
            }
            visit(androidRoot)
            assertEquals("Deve esistere una sola MapView nativa", 1, maps.size)
            result = maps.single()
        }
        return checkNotNull(result)
    }

    private fun assertRectClose(expected: Rect, actual: Rect) {
        assertEquals("Bordo sinistro", expected.left, actual.left, 2f)
        assertEquals("Bordo superiore", expected.top, actual.top, 2f)
        assertEquals("Bordo destro", expected.right, actual.right, 2f)
        assertEquals("Bordo inferiore", expected.bottom, actual.bottom, 2f)
    }

    private class IsolatedPreferencesContext(base: Context) : ContextWrapper(base) {
        private val prefix = "mappinando_map_transition_test_${UUID.randomUUID()}_"
        private val files = mutableSetOf<String>()
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val isolatedName = prefix + name
            files += isolatedName
            return baseContext.getSharedPreferences(isolatedName, mode)
        }
        fun deleteTestPreferences() {
            files.forEach { name ->
                baseContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
                baseContext.deleteSharedPreferences(name)
            }
        }
    }
}
