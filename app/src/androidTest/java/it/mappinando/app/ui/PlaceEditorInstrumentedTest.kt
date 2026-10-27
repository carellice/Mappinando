package it.mappinando.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.mappinando.app.data.Place
import it.mappinando.app.data.PlaceCategory
import it.mappinando.app.data.PlaceTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaceEditorInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun emptyNameCannotBeSaved() {
        var saved: Place? = null
        compose.setContent {
            MaterialTheme {
                PlaceEditorSheet(
                    initial = null,
                    latitude = 45.4642,
                    longitude = 9.19,
                    onDismiss = {},
                    onSave = { saved = it },
                )
            }
        }

        compose.onNodeWithTag("editor-save").performScrollTo().performClick()

        compose.onNodeWithText("Dai un nome al luogo per salvarlo.").assertExists()
        compose.runOnIdle { assertNull(saved) }
    }

    @Test
    fun createPlaceIncludesCategoryAndUnsubmittedTask() {
        var saved: Place? = null
        compose.setContent {
            MaterialTheme {
                PlaceEditorSheet(
                    initial = null,
                    latitude = 45.4642,
                    longitude = 9.19,
                    suggestedAddress = "Via Brera, Milano",
                    onDismiss = {},
                    onSave = { saved = it },
                )
            }
        }

        compose.onNodeWithTag("editor-name").performTextInput("  Caffè delle idee  ")
        compose.onNodeWithTag("editor-city").performScrollTo().performTextInput("  Milano  ")
        compose.onNodeWithText("${PlaceCategory.BARS.emoji}  ${PlaceCategory.BARS.label}")
            .performScrollTo().performClick()
        compose.onNodeWithTag("editor-task-input").performScrollTo()
            .performTextInput("  Provare il cappuccino  ")
        // The Save action also retains a task that has not been submitted with +.
        compose.onNodeWithTag("editor-save").performScrollTo().performClick()

        compose.runOnIdle {
            val place = requireNotNull(saved)
            assertEquals("Caffè delle idee", place.name)
            assertEquals(PlaceCategory.BARS, place.category)
            assertEquals("Via Brera, Milano", place.address)
            assertEquals("Milano", place.city)
            assertEquals(45.4642, place.latitude, .000001)
            assertEquals(9.19, place.longitude, .000001)
            assertEquals(listOf("Provare il cappuccino"), place.tasks.map { it.title })
            assertEquals(false, place.visited)
        }
    }

    @Test
    fun detailControlsInvokeFavoriteVisitedAndTaskCallbacks() {
        var favoriteChanges = 0
        var visitedChanges = 0
        var toggledTask: String? = null
        val place = samplePlace()
        compose.setContent {
            MaterialTheme {
                PlaceDetailScreen(
                    place = place,
                    onDismiss = {},
                    onEdit = {},
                    onDelete = {},
                    onToggleFavorite = { favoriteChanges++ },
                    onToggleVisited = { visitedChanges++ },
                    onToggleTask = { toggledTask = it },
                    onAddTask = {},
                    onDeleteTask = {},
                    onNavigate = {},
                )
            }
        }

        compose.onNodeWithTag("place-detail-screen").assertExists()
        compose.onNodeWithContentDescription("Aggiungi ai preferiti").performClick()
        compose.onNodeWithText("Visitato").performScrollTo().performClick()
        compose.onNodeWithTag("place-task-task-photo").performScrollTo().performClick()

        compose.runOnIdle {
            assertEquals(1, favoriteChanges)
            assertEquals(1, visitedChanges)
            assertEquals("task-photo", toggledTask)
        }
    }

    @Test
    fun deletingPlaceRequiresConfirmation() {
        var deleted: Place? = null
        val place = samplePlace()
        compose.setContent {
            MaterialTheme {
                PlaceDetailScreen(
                    place = place,
                    onDismiss = {},
                    onEdit = {},
                    onDelete = { deleted = it },
                    onToggleFavorite = {},
                    onToggleVisited = {},
                    onToggleTask = {},
                    onAddTask = {},
                    onDeleteTask = {},
                    onNavigate = {},
                )
            }
        }

        compose.onNodeWithText("Elimina").performScrollTo().performClick()
        compose.onNodeWithText("Eliminare questo luogo?").assertExists()
        compose.runOnIdle { assertNull(deleted) }
        compose.onNodeWithText("Elimina luogo").performClick()

        compose.runOnIdle { assertEquals(place.id, deleted?.id) }
    }

    @Test
    fun lateCitySuggestionDoesNotOverwriteUserCity() {
        val suggestedCity = mutableStateOf("")
        compose.setContent {
            MaterialTheme {
                PlaceEditorSheet(
                    initial = null,
                    latitude = 45.4642,
                    longitude = 9.19,
                    suggestedCity = suggestedCity.value,
                    onDismiss = {},
                    onSave = {},
                )
            }
        }
        compose.runOnIdle { suggestedCity.value = "Milano" }
        compose.onNodeWithTag("editor-city").performScrollTo().assertTextContains("Milano")
        compose.onNodeWithTag("editor-city").performTextClearance()
        compose.onNodeWithTag("editor-city").performTextInput("Roma")
        compose.runOnIdle { suggestedCity.value = "Torino" }
        compose.onNodeWithTag("editor-city").assertTextContains("Roma")
    }

    private fun samplePlace() = Place(
        id = "place-garden",
        name = "Orto botanico di Brera",
        category = PlaceCategory.NATURE,
        latitude = 45.4713,
        longitude = 9.1882,
        address = "Via Brera 28, Milano",
        city = "Milano",
        tasks = listOf(PlaceTask(id = "task-photo", title = "Fotografare il giardino")),
    )
}
