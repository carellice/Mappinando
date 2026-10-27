package it.mappinando.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.mappinando.app.data.Place
import it.mappinando.app.data.PlaceCategory
import it.mappinando.app.data.PlaceTask
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CollectionScreensInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val places = (0 until 36).map { index ->
        Place(
            id = "city-place-$index", name = "Luogo $index", category = PlaceCategory.NATURE,
            latitude = 45.0, longitude = 9.0,
            city = if (index < 24) "Milano" else "Roma",
            tasks = listOf(PlaceTask(id = "idea-$index", title = "Un’idea per il luogo $index")),
        )
    }

    @Test fun placesAreGroupedByCityAndHeaderCollapsesDuringScroll() {
        compose.setContent {
            MappinandoTheme {
                PlacesScreen(places, places, null, "Tutti", {}, {}, {}, {}, {}, 110.dp, {})
            }
        }
        compose.onNodeWithTag("city-heading:milano").assertIsDisplayed()
        val expanded = compose.onNodeWithTag("places-header").fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithTag("places-list").performTouchInput { swipeUp() }
        val collapsed = compose.onNodeWithTag("places-header").fetchSemanticsNode().boundsInRoot.height
        assertTrue("L’intestazione deve liberare spazio scorrendo", collapsed < expanded * .8f)
        compose.onNodeWithContentDescription("Filtra i luoghi").assertIsDisplayed()
        compose.onNodeWithTag("places-list").performScrollToNode(hasTestTag("city-heading:roma"))
        compose.onNodeWithTag("city-heading:roma").assertIsDisplayed()
    }

    @Test fun tasksHeaderCollapsesAndCompletionControlStaysAccessible() {
        compose.setContent {
            MappinandoTheme { TasksScreen(places, {}, { _, _ -> }, {}, 110.dp, {}) }
        }
        val expanded = compose.onNodeWithTag("tasks-header").fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithTag("tasks-list").performTouchInput { swipeUp() }
        val collapsed = compose.onNodeWithTag("tasks-header").fetchSemanticsNode().boundsInRoot.height
        assertTrue("L’intestazione delle attività deve comprimersi", collapsed < expanded * .8f)
        compose.onNodeWithContentDescription("Mostra attività completate").assertIsDisplayed()
    }
}
