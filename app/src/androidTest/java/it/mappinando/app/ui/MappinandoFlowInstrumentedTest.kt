package it.mappinando.app.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import it.mappinando.app.data.PlaceRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID

/** Exercises real app state and storage without touching the user's preference files. */
@RunWith(AndroidJUnit4::class)
class MappinandoFlowInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var storageContext: IsolatedPreferencesContext

    @After fun cleanUp() {
        if (::storageContext.isInitialized) storageContext.deleteTestPreferences()
    }

    @Test fun coordinatePlaceCanBeSavedCompletedFilteredAndDeleted() {
        val uniqueSuffix = UUID.randomUUID().toString().take(8)
        val placeName = "Scoperta test $uniqueSuffix"
        val taskTitle = "Fotografare il tramonto $uniqueSuffix"
        var createdId = ""
        var taskId = ""
        compose.setContent {
            val baseContext = LocalContext.current
            val isolated = remember(baseContext) { IsolatedPreferencesContext(baseContext) }
            SideEffect { storageContext = isolated }
            CompositionLocalProvider(LocalContext provides isolated) {
                MappinandoTheme { MappinandoApp() }
            }
        }

        // Coordinates work offline; the test never waits for map tiles or geocoding.
        compose.onNodeWithTag("tab-MAP").assertIsSelected()
        compose.onNodeWithTag("search-entry").performClick()
        compose.onNodeWithTag("search-coordinates-button").performScrollTo().performClick()
        compose.onNodeWithTag("search-latitude").performScrollTo().performTextInput("45.4701")
        compose.onNodeWithTag("search-longitude").performScrollTo().performTextInput("-9.1901")
        compose.onNodeWithTag("search-coordinate-add").performScrollTo().performClick()
        compose.onNodeWithTag("map-preview-save").performClick()

        compose.onNodeWithTag("editor-name").performTextInput(placeName)
        compose.onNodeWithTag("editor-city").performScrollTo().performTextInput("Roma")
        compose.onNodeWithTag("editor-task-input").performScrollTo().performTextInput(taskTitle)
        compose.onNodeWithTag("editor-save").performScrollTo().performClick()
        compose.runOnIdle {
            val saved = PlaceRepository(storageContext).places.value.single { it.name == placeName }
            createdId = saved.id
            taskId = saved.tasks.single().id
            assertEquals(-9.1901, saved.longitude, .000001)
            assertEquals("Roma", saved.city)
            assertEquals(taskTitle, saved.tasks.single().title)
            assertFalse(saved.tasks.single().done)
        }

        compose.onNodeWithContentDescription("Riduci mappa").performClick()
        compose.onNodeWithTag("tab-PLACES").performClick()
        compose.onNodeWithTag("tab-PLACES").assertIsSelected()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(placeName))
        compose.onNodeWithText(placeName).performClick()
        compose.onNodeWithTag("place-detail-screen").assertIsDisplayed()
        inDetail(hasContentDescription("Aggiungi ai preferiti")).performClick()
        inDetail(hasText("Visitato")).performScrollTo().performClick()
        compose.onNodeWithTag("place-task-$taskId").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Chiudi dettaglio").performClick()

        compose.onNodeWithContentDescription("Filtra i luoghi").performClick()
        inDialog(hasText("Preferiti")).performClick()
        compose.onNodeWithText("Mostra risultati").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(placeName))
        compose.onNodeWithText(placeName).assertExists()
        compose.runOnIdle {
            val saved = PlaceRepository(storageContext).places.value.single { it.id == createdId }
            assertTrue(saved.favorite)
            assertTrue(saved.visited)
            assertTrue(saved.tasks.single().done)
        }

        compose.onNodeWithTag("tab-TASKS").performClick()
        compose.onNodeWithTag("tab-TASKS").assertIsSelected()
        compose.onNodeWithText(taskTitle).assertDoesNotExist()
        compose.onNodeWithContentDescription("Mostra attività completate").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(placeName))
        compose.onNodeWithText(taskTitle).assertExists()
        compose.onNodeWithText(placeName).performClick()
        inDetail(hasText("Elimina")).performScrollTo().performClick()
        compose.onNodeWithText("Elimina luogo").performClick()
        compose.runOnIdle {
            assertTrue(PlaceRepository(storageContext).places.value.none { it.id == createdId })
        }
    }

    @Test fun mapExpandsToFullscreenAndReturnsToPreview() {
        compose.setContent {
            val baseContext = LocalContext.current
            val isolated = remember(baseContext) { IsolatedPreferencesContext(baseContext) }
            SideEffect { storageContext = isolated }
            CompositionLocalProvider(LocalContext provides isolated) { MappinandoTheme { MappinandoApp() } }
        }
        val previewHeight = compose.onNodeWithTag("map-preview").fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithTag("map-fullscreen-toggle").performClick()
        compose.onNodeWithTag("fullscreen-map").assertIsDisplayed()
        val fullHeight = compose.onNodeWithTag("fullscreen-map").fetchSemanticsNode().boundsInRoot.height
        assertTrue(fullHeight > previewHeight * 1.3f)
        compose.onNodeWithTag("tab-MAP").assertDoesNotExist()
        compose.onAllNodesWithTag("native-map-view").assertCountEquals(1)
        compose.onNodeWithContentDescription("Riduci mappa").performClick()
        compose.onNodeWithTag("map-preview").assertIsDisplayed()
        compose.onNodeWithTag("tab-MAP").assertIsSelected()
    }

    private fun inDetail(matcher: SemanticsMatcher) =
        compose.onNode(matcher and hasAnyAncestor(hasTestTag("place-detail-screen")))

    private fun inDialog(matcher: SemanticsMatcher) =
        compose.onNode(matcher and hasAnyAncestor(isDialog()))

    private class IsolatedPreferencesContext(base: Context) : ContextWrapper(base) {
        private val prefix = "mappinando_flow_test_${UUID.randomUUID()}_"
        private val preferenceFiles = mutableSetOf<String>()
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val isolatedName = prefix + name
            preferenceFiles += isolatedName
            return baseContext.getSharedPreferences(isolatedName, mode)
        }
        fun deleteTestPreferences() {
            preferenceFiles.forEach { name ->
                baseContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
                baseContext.deleteSharedPreferences(name)
            }
        }
    }
}
